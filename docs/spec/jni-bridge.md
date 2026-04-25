# Section 3: JNI Bridge - Async Envelope + Ring Buffer

## 3.1 Design Goals & Pattern

The JNI layer uses the **Async Envelope Bridge** pattern: all structured control traffic crosses the JNI boundary as a serialized **Protobuf `Envelope`** in a `jbyteArray`, correlated by UUID, and dispatched asynchronously on a Tokio runtime. High-frequency benchmark sample data streams separately via an in-memory lock-free SPSC ring buffer for zero-allocation, zero-JNI-call throughput.

| Requirement | Solution |
|---|---|
| **Generalized control** | Protobuf `Envelope` in `jbyteArray` - binary, schema-enforced, self-describing |
| **State** | `jlong` opaque handle → `Arc<Mutex<BridgeContext>>` |
| **Bi-directional** | Kotlin→Rust: `nativeSend(handle, bytes)`; Rust→Kotlin: stored `GlobalRef` callback |
| **Async** | Tokio on Rust releases JNI thread immediately; `CompletableDeferred` suspends Kotlin coroutine |
| **Streaming** | Lock-free SPSC ring buffer in `DirectByteBuffer` - Rust writes, Kotlin polls at 60 Hz |
| **Safe** | `Arc`, `Mutex`, `GlobalRef`, no raw pointer leaks - Rust ownership enforces at compile time |
| **Extensible** | New command = one new `match` arm in `dispatch()` |

## 3.2 Envelope Dispatch Table

| `type_url` | Direction | Payload | Async? |
|---|---|---|---|
| `bench.init` | K→R | `InitRequest` (capacity only) | Yes |
| `bench.suite.start` | K→R | `SuiteStartRequest` | Yes - fire-and-forget |
| `bench.single.start` | K→R | `SingleStartRequest` | Yes - fire-and-forget |
| `bench.cancel` | K→R | none | Yes |
| `bench.results.get` | K→R | none | Yes |
| `bench.available.get` | K→R | none | Yes |
| `bench.init.ok` | R→K | none | - |
| `bench.suite.started` | R→K | none (ack; run streams via ring buffer) | - |
| `bench.single.started` | R→K | none | - |
| `bench.cancelled` | R→K | none | - |
| `bench.results` | R→K | `ResultsResponse` | - |
| `bench.available` | R→K | `AvailableResponse` | - |

## 3.3 Ring Buffer Layout (4 MB DirectByteBuffer)

```
Offset    Size     Field                  Description
──────────────────────────────────────────────────────────────
0x0000    8B       magic                  0x42454E4348535549 ("BENCHSUI")
0x0008    4B       version                Protocol version (1)
0x000C    4B       capacity               Total ring data capacity in bytes
0x0010    4B       record_size            Fixed record size (64 bytes)
0x0014    4B       _pad0                  Alignment padding
0x0018    8B       write_index            Atomic, release semantics (Rust writes)
0x0020    8B       read_index             Atomic, acquire semantics (Kotlin writes)
0x0028    4B       state                  Engine state enum (0=idle,1=running,2=done,3=error)
0x002C    4B       dropped_count          Records dropped due to full buffer
0x0030    16B      _pad1                  Pad to 64-byte cache line
0x0040    ...      [Ring Data Region]     Record slots from here to end
```

## 3.4 Record Format (64 bytes each)

```
Offset    Size     Field              Description
──────────────────────────────────────────────────────────────
0x00      1B       record_type        0x01=Progress, 0x02=Sample, 0x03=Log, 0xFF=Complete
0x01      1B       category_id        Benchmark category enum
0x02      2B       bench_id           Benchmark identifier (u16)
0x04      4B       flags              Bit flags (warmup, final, error, etc.)
0x08      4B       sequence           Monotonic sequence number
0x0C      4B       phase              Current phase (warmup/measure/cooldown)
0x10      4B       current_iter       Current iteration within phase
0x14      4B       total_iter         Total iterations in phase
0x18      8B       timestamp_ns       CLOCK_MONOTONIC timestamp
0x20      8B       value_primary      Primary metric value (f64)
0x28      8B       value_secondary    Secondary metric value (f64)
0x30      4B       unit               Unit enum
0x34      4B       metric_id          Sub-metric identifier
0x38      8B       _reserved          Future use
```

## 3.5 Safety Guarantees

- **Memory safety** - `Arc` prevents use-after-free across the JNI boundary. `Mutex` prevents data races on `BridgeContext`.
- **Deadlock prevention** - The `BridgeContext` mutex is **never held across any JNI call**.
- **Lifetime** - `Arc<GlobalRef>` keeps the Kotlin `BridgeCallback` object alive in the JVM GC for as long as any `Arc` clone of `BridgeContext` exists.
- **Cloneable writer** - `RingBufferWriter` wraps a raw pointer into the `DirectByteBuffer`'s memory and must implement `Clone` so `dispatch()` can give a clone to each spawned blocking task.

## 3.6 Proto Schema

```protobuf
// proto/bridge.proto
message Envelope {
  string              correlation_id = 1;
  string              type_url       = 2;
  bytes               payload        = 3;
  map<string, string> meta           = 4;
  Status              status         = 5;
  string              error          = 6;
}
```

See `proto/bench_commands.proto`, `proto/config.proto`, and `proto/results.proto` for the full message definitions.
