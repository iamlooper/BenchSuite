# Section 2: Architecture

## 2.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Android Application                       │
│                                                                   │
│  ┌───────────────────────────────────────────────────────────┐   │
│  │                    Kotlin / Compose UI                     │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌─────────────┐  │   │
│  │  │Dashboard │ │Benchmark │ │ Results  │ │ Leaderboard │  │   │
│  │  │  Screen  │ │  Runner  │ │  Detail  │ │   Screen    │  │   │
│  │  └────┬─────┘ └────┬─────┘ └────┬─────┘ └──────┬──────┘  │   │
│  │       │             │            │               │          │   │
│  │  ┌────┴─────────────┴────────────┴───────────────┴──────┐  │   │
│  │  │              ViewModel Layer (StateFlow)              │  │   │
│  │  └──────────────────────┬───────────────────────────────┘  │   │
│  │                         │                                   │   │
│  │  ┌──────────────────────┴───────────────────────────────┐  │   │
│  │  │           BenchmarkEngine (Kotlin Wrapper)            │  │   │
│  │  │    • Holds jlong handle → Arc<Mutex<BridgeContext>>   │  │   │
│  │  │    • Sends Envelope commands via Bridge (Coroutines)  │  │   │
│  │  │    • Allocates DirectByteBuffer (ring buffer)         │  │   │
│  │  │    • Polls ring buffer at ~60Hz via Choreographer     │  │   │
│  │  │    • Decodes progress/sample records                  │  │   │
│  │  │    • Manages benchmark lifecycle                      │  │   │
│  │  └──────────────────────┬───────────────────────────────┘  │   │
│  └─────────────────────────│─────────────────────────────────┘   │
│                            │ JNI: Envelope jbyteArray (control)   │
│                            │    + DirectByteBuffer (streaming)    │
│  ┌─────────────────────────│─────────────────────────────────┐   │
│  │                    Rust Native Engine                       │   │
│  │  ┌──────────────────────┴───────────────────────────────┐  │   │
│  │  │              JNI Entry Points (jni crate)             │  │   │
│  │  └──────────────────────┬───────────────────────────────┘  │   │
│  │                         │                                   │   │
│  │  ┌──────────────────────┴───────────────────────────────┐  │   │
│  │  │               Ring Buffer Writer (SPSC)               │  │   │
│  │  └──────────────────────┬───────────────────────────────┘  │   │
│  │                         │                                   │   │
│  │  ┌──────────────────────┴───────────────────────────────┐  │   │
│  │  │   CPU │ Memory │ Scheduler │ IPC │ I/O │ Network │ Timer  │  │
│  │  └────────────────────────────────────────────────────────┘  │   │
│  │                    Linux Kernel APIs                          │   │
│  └─────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

## 2.2 Data Flow

The bridge operates on two parallel channels:

**Control channel (Async Protobuf Envelope):**

1. On startup, Kotlin calls `NativeBridge.nativeInit(callback)` with a `BridgeCallback` reference, receiving a `jlong` handle pointing to a heap-allocated `Arc<Mutex<BridgeContext>>` on the Rust side.
2. Kotlin sends commands as serialized `Envelope` protobufs via `NativeBridge.nativeSend(handle, bytes)`. Each envelope carries a `correlation_id` and a `type_url`.
3. Rust deserializes the envelope, dispatches to the matching handler on a Tokio async thread (releasing the JNI thread immediately), executes the command, and delivers the response envelope back to Kotlin by calling `BridgeCallback.onEnvelope(bytes)`.
4. Kotlin resumes the suspended coroutine via `CompletableDeferred`, matching by `correlation_id`. A 30-second timeout guards against lost responses.
5. On teardown, Kotlin calls `NativeBridge.nativeDestroy(handle)`.

**Streaming channel (Lock-free Ring Buffer):**

6. Kotlin allocates a `DirectByteBuffer` (4 MB), calls `NativeBridge.nativeInitBuffer(handle, ringBuffer)` to let Rust record the native pointer via `GetDirectBufferAddress`, then sends a `bench.init` Envelope.
7. As each benchmark iteration completes, Rust writes fixed-size **progress records** and **sample records** into the ring buffer using atomic release stores.
8. Kotlin polls the ring buffer at display refresh rate (~60 Hz) via `Choreographer`, reads new records using atomic acquire loads, decodes them, and pushes updates to Compose UI `StateFlow`.
9. When all benchmarks complete, Rust writes a `Complete` record. Kotlin detects this and requests final results via the control channel.

## 2.3 Module Breakdown

| Layer | Location | Responsibility |
|---|---|---|
| `app` (Kotlin) | `app/src/main/java/io/github/iamlooper/benchsuite/` | Application entry, DI, navigation, screens, engine wrapper, data layer, UI components |
| `app` (Rust) | `app/src/main/rust/` | All benchmark implementations, async Envelope bridge, ring buffer writer, JNI entry points, scoring |
