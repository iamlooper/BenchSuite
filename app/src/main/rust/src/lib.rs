// Generated protobuf types (prost-build output).
// bridge.proto + bench_commands.proto (package bridge)  → bridge_proto module
// config.proto + results.proto (package benchsuite)     → benchsuite module
// Module must be named `benchsuite` so prost cross-package refs (super::benchsuite::T) resolve.
pub mod bridge_proto {
    include!(concat!(env!("OUT_DIR"), "/bridge.rs"));
}
pub mod benchsuite {
    include!(concat!(env!("OUT_DIR"), "/benchsuite.rs"));
}

pub mod bridge;
pub mod ring_buffer;
pub mod engine;
pub mod scoring;
pub mod benchmarks;

use std::sync::Arc;
use jni::{EnvUnowned, errors::{Result as JniResult, ThrowRuntimeExAndDefault}};
use jni::objects::{JByteArray, JByteBuffer, JClass, JObject};
use jni::sys::jlong;
use once_cell::sync::Lazy;
use parking_lot::Mutex;
use tokio::runtime::Runtime;

use bridge::BridgeContext;

// Global Tokio runtime - one per process, Never dropped.
static TOKIO: Lazy<Runtime> = Lazy::new(|| {
    Runtime::new().expect("failed to create Tokio runtime")
});

// --- JNI entry points ---

/// `BenchmarkBridge.nativeInit` - creates a new BridgeContext and returns an opaque handle
/// (raw pointer to `Arc<Mutex<BridgeContext>>`).
#[unsafe(no_mangle)]
pub extern "system" fn Java_io_github_iamlooper_benchsuite_engine_NativeBridge_nativeInit<'local>(
    mut env: EnvUnowned<'local>,
    _class: JClass<'local>,
    callback: JObject<'local>,
) -> jlong {
    env.with_env(|e| -> JniResult<jlong> {
        let ctx = BridgeContext::new(e, callback);
        let arc = Arc::new(Mutex::new(ctx));
        Ok(Arc::into_raw(arc) as jlong)
    }).resolve::<ThrowRuntimeExAndDefault>()
}

/// `BenchmarkBridge.nativeInitBuffer` - delivers the `DirectByteBuffer` virtual address to Rust.
/// Writes the ring buffer header and makes the buffer ready for writing.
#[unsafe(no_mangle)]
pub extern "system" fn Java_io_github_iamlooper_benchsuite_engine_NativeBridge_nativeInitBuffer<'local>(
    mut env: EnvUnowned<'local>,
    _class: JClass<'local>,
    handle: jlong,
    ring_buffer: JByteBuffer<'local>,
) {
    env.with_env(|e| -> JniResult<()> {
        let ptr = handle as *const Mutex<BridgeContext>;
        // Safety: ptr came from Arc::into_raw in nativeInit, and Java lifetime guarantees
        // nativeDestroy has not been called.
        let ctx_guard = unsafe { Arc::increment_strong_count(ptr); Arc::from_raw(ptr) };
        let addr     = e.get_direct_buffer_address(&ring_buffer)?;
        let capacity = e.get_direct_buffer_capacity(&ring_buffer)?;
        ctx_guard.lock().init_ring_buffer(addr, capacity);
        Ok(())
    }).resolve::<ThrowRuntimeExAndDefault>()
}

/// `BenchmarkBridge.nativeSend` - dispatches an Envelope (proto bytes) on the Tokio runtime.
/// Returns immediately; the response is sent to Kotlin via `BridgeCallback.onEnvelope`.
#[unsafe(no_mangle)]
pub extern "system" fn Java_io_github_iamlooper_benchsuite_engine_NativeBridge_nativeSend<'local>(
    mut env: EnvUnowned<'local>,
    _class: JClass<'local>,
    handle: jlong,
    envelope_bytes: JByteArray<'local>,
) {
    env.with_env(|e| -> JniResult<()> {
        let bytes: Vec<u8> = e.convert_byte_array(&envelope_bytes)?;
        let ptr = handle as *const Mutex<BridgeContext>;
        // Safety: same as nativeInitBuffer.
        let ctx_arc = unsafe { Arc::increment_strong_count(ptr); Arc::from_raw(ptr) };
        TOKIO.spawn(async move {
            bridge::dispatch(ctx_arc, bytes).await;
        });
        Ok(())
    }).resolve::<ThrowRuntimeExAndDefault>()
}

/// `BenchmarkBridge.nativeDestroy` - drops the Arc clone held by the JNI handle.
/// In-flight Tokio tasks retain their own Arc clones and run to completion.
#[unsafe(no_mangle)]
pub extern "system" fn Java_io_github_iamlooper_benchsuite_engine_NativeBridge_nativeDestroy<'local>(
    mut env: EnvUnowned<'local>,
    _class: JClass<'local>,
    handle: jlong,
) {
    env.with_env(|_e| -> JniResult<()> {
        let ptr = handle as *const Mutex<BridgeContext>;
        // Safety: ptr came from Arc::into_raw; this call balances the original into_raw.
        drop(unsafe { Arc::from_raw(ptr) });
        Ok(())
    }).resolve::<ThrowRuntimeExAndDefault>()
}
