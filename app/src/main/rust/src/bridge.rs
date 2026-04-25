use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};

use jni::objects::{Global, JObject, JValue};
use jni::signature::{MethodSignature, RuntimeMethodSignature};
use jni::{jni_str, Env};
use jni::errors::Result as JniResult;
use jni::JavaVM;
use parking_lot::Mutex;
use prost::Message;
use tokio::task;

use crate::benchmarks::registry;
use crate::bridge_proto::{Envelope, Status};
use crate::engine;
use crate::ring_buffer::RingBufferWriter;

/// Opaque context retained per-handle on the Kotlin side.
pub struct BridgeContext {
    /// JVM reference for attaching Tokio threads.
    jvm: JavaVM,
    /// Global ref to the Kotlin `BridgeCallback` object.
    callback: Global<JObject<'static>>,
    /// Ring buffer writer - initialized after `nativeInitBuffer`.
    ring_writer: Option<Arc<RingBufferWriter>>,
    /// Cancel flag shared into the in-flight benchmark run task.
    cancel_flag: Arc<AtomicBool>,
}

impl BridgeContext {
    pub fn new(env: &mut Env<'_>, callback: JObject<'_>) -> Self {
        let jvm          = env.get_java_vm().expect("could not get JavaVM");
        let callback_ref: Global<JObject<'static>> = env.new_global_ref(callback).expect("could not create global ref");
        Self {
            jvm,
            callback: callback_ref,
            ring_writer: None,
            cancel_flag: Arc::new(AtomicBool::new(false)),
        }
    }

    pub fn init_ring_buffer(&mut self, addr: *mut u8, capacity: usize) {
        let writer = RingBufferWriter::new(addr, capacity);
        writer.write_header();
        self.ring_writer = Some(Arc::new(writer));
    }

    pub fn ring_writer(&self) -> Option<Arc<RingBufferWriter>> {
        self.ring_writer.clone()
    }

    pub fn cancel_flag(&self) -> Arc<AtomicBool> {
        self.cancel_flag.clone()
    }

    /// Creates a fresh AtomicBool for the next run and returns an Arc clone for the Tokio task.
    /// Any in-flight task that holds its own Arc clone of the previous flag continues to see
    /// its own state independently; the old task observes its Arc as true (cancelled) and exits
    /// without interfering with the new task's false flag.
    pub fn new_cancel_flag(&mut self) -> Arc<AtomicBool> {
        self.cancel_flag = Arc::new(AtomicBool::new(false));
        self.cancel_flag.clone()
    }

    /// Sends a serialized Envelope back to Kotlin via `BridgeCallback.onEnvelope(byte[])`.
    /// Attaches the current Tokio thread to the JVM for the duration of the call.
    pub fn send_to_kotlin(&self, envelope: &Envelope) {
        let bytes = envelope.encode_to_vec();
        self.jvm
            .attach_current_thread(|env| -> JniResult<()> {
                let byte_arr = env.byte_array_from_slice(&bytes)?;
                let rts = RuntimeMethodSignature::from_str("([B)V")?;
                let sig = MethodSignature::from(&rts);
                env.call_method(
                    &self.callback,
                    jni_str!("onEnvelope"),
                    sig,
                    &[JValue::Object(byte_arr.as_ref())],
                )
                .map(|_| ())
            })
            .expect("onEnvelope() call failed");
    }
}

// Safety: BridgeContext pointers are only accessed through Arc<Mutex<BridgeContext>>.
unsafe impl Send for BridgeContext {}
unsafe impl Sync for BridgeContext {}

// --- Envelope dispatch ---

/// Decodes a serialized Envelope from Kotlin and forwards to the appropriate handler.
/// Responses are sent back via `BridgeContext::send_to_kotlin`.
pub async fn dispatch(ctx: Arc<Mutex<BridgeContext>>, bytes: Vec<u8>) {
    let envelope = match Envelope::decode(bytes.as_slice()) {
        Ok(e)  => e,
        Err(e) => {
            let resp = make_error_envelope("", "bench.error", &format!("malformed envelope: {e}"));
            ctx.lock().send_to_kotlin(&resp);
            return;
        }
    };

    let type_url       = envelope.type_url.as_str();
    let correlation_id = envelope.correlation_id.as_str();

    match type_url {
        "bench.init" => {
            let resp = make_typed_ok_envelope(correlation_id, "bench.init.ok");
            ctx.lock().send_to_kotlin(&resp);
        }

        "bench.suite.start" => {
            use crate::bridge_proto::SuiteStartRequest;
            let req = SuiteStartRequest::decode(envelope.payload.as_ref()).unwrap_or_default();
            let storage_path = req.config
                .map(|c| c.storage_path)
                .unwrap_or_default();

            // new_cancel_flag() allocates a fresh AtomicBool so any still-running task from a
            // previous cancelled run keeps its own Arc with true and exits without corrupting
            // the new run's flag.
            let cancel = {
                let mut guard = ctx.lock();
                guard.new_cancel_flag()
            };
            let writer = ctx.lock().ring_writer();
            let ack    = make_typed_ok_envelope(correlation_id, "bench.suite.started");
            ctx.lock().send_to_kotlin(&ack);

            task::spawn_blocking(move || {
                engine::run_suite(writer.as_deref(), &cancel, &storage_path);
            }).await.ok();
        }

        "bench.single.start" => {
            use crate::bridge_proto::SingleStartRequest;
            let req = SingleStartRequest::decode(envelope.payload.as_ref()).unwrap_or_default();
            let bench_id     = req.bench_id as usize;
            let storage_path = req.config.map(|c| c.storage_path).unwrap_or_default();

            let cancel = {
                let mut guard = ctx.lock();
                guard.new_cancel_flag()
            };
            let writer = ctx.lock().ring_writer();
            let ack    = make_typed_ok_envelope(correlation_id, "bench.single.started");
            ctx.lock().send_to_kotlin(&ack);

            task::spawn_blocking(move || {
                engine::run_single_benchmark(bench_id, writer.as_deref(), &cancel, &storage_path);
            }).await.ok();
        }

        "bench.cancel" => {
            ctx.lock().cancel_flag().store(true, Ordering::Release);
            let resp = make_typed_ok_envelope(correlation_id, "bench.cancelled");
            ctx.lock().send_to_kotlin(&resp);
        }

        "bench.results.get" => {
            let writer  = ctx.lock().ring_writer();
            let results = engine::collect_results(writer.as_deref());
            let payload = {
                use crate::bridge_proto::ResultsResponse;
                ResultsResponse { results: Some(results) }.encode_to_vec()
            };
            let resp = make_typed_payload_envelope(correlation_id, "bench.results", &payload);
            ctx.lock().send_to_kotlin(&resp);
        }

        "bench.available.get" => {
            use crate::bridge_proto::{AvailableResponse, BenchmarkDescriptor};
            let benchmarks: Vec<BenchmarkDescriptor> = registry()
                .iter()
                .enumerate()
                .map(|(i, b)| BenchmarkDescriptor {
                    id:       i as u32,
                    name:     b.name().to_owned(),
                    category: b.category().to_string(),
                    unit:     b.config().unit.to_string(),
                })
                .collect();
            let resp_payload = AvailableResponse { benchmarks }.encode_to_vec();
            let resp = make_typed_payload_envelope(correlation_id, "bench.available", &resp_payload);
            ctx.lock().send_to_kotlin(&resp);
        }

        unknown => {
            let resp = make_error_envelope(
                correlation_id,
                "bench.error",
                &format!("unknown type_url: {unknown}"),
            );
            ctx.lock().send_to_kotlin(&resp);
        }
    }
}

// --- Envelope constructors ---

pub fn make_typed_ok_envelope(correlation_id: &str, type_url: &str) -> Envelope {
    Envelope {
        correlation_id: correlation_id.to_owned(),
        type_url:       type_url.to_owned(),
        status:         Status::Ok as i32,
        ..Default::default()
    }
}

pub fn make_typed_payload_envelope(correlation_id: &str, type_url: &str, payload: &[u8]) -> Envelope {
    Envelope {
        correlation_id: correlation_id.to_owned(),
        type_url:       type_url.to_owned(),
        status:         Status::Ok as i32,
        payload:        payload.to_vec(),
        ..Default::default()
    }
}

pub fn make_error_envelope(correlation_id: &str, type_url: &str, error: &str) -> Envelope {
    Envelope {
        correlation_id: correlation_id.to_owned(),
        type_url:       type_url.to_owned(),
        status:         Status::Error as i32,
        error:          error.to_owned(),
        ..Default::default()
    }
}
