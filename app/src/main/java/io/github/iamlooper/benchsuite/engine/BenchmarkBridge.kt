package io.github.iamlooper.benchsuite.engine

import io.github.iamlooper.benchsuite.proto.AvailableResponse
import io.github.iamlooper.benchsuite.proto.Envelope
import io.github.iamlooper.benchsuite.proto.InitRequest
import io.github.iamlooper.benchsuite.proto.ResultsResponse
import io.github.iamlooper.benchsuite.proto.SingleStartRequest
import io.github.iamlooper.benchsuite.proto.Status
import io.github.iamlooper.benchsuite.proto.SuiteConfig
import io.github.iamlooper.benchsuite.proto.SuiteStartRequest
import com.google.protobuf.ByteString
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Async Protobuf Envelope bridge between Kotlin and the Rust native engine.
 *
 * Each outbound command is wrapped in an [Envelope] with a UUID correlation_id.
 * That id maps to a [kotlinx.coroutines.CompletableDeferred] that suspends the caller
 * until Rust delivers the matching response via [onEnvelope]. A 30-second timeout
 * guards every call against lost responses.
 *
 * Lifecycle:
 *   1. [init] - calls nativeInit (gets handle), nativeInitBuffer (passes DirectByteBuffer),
 *               sends "bench.init" Envelope to validate ring buffer capacity.
 *   2. [startSuite] / [startSingle] - fire-and-forget from Rust side; Choreographer polls
 *      the ring buffer for progress. Call [getResults] once the Complete record appears.
 *   3. [destroy] - drops the native handle; any in-flight Tokio tasks complete on their own.
 */
class BenchmarkBridge : BridgeCallback {

    internal val pending = ConcurrentHashMap<String, CompletableDeferred<Envelope>>()

    private var handle: Long = 0L

    // Volatile so the JVM-attached Rust thread calling onEnvelope() always sees the latest scope
    // reference after a destroy() + init() cycle.
    @Volatile private var scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * 4 MB DirectByteBuffer. Allocated here, native pointer delivered to Rust
     * via NativeBridge.nativeInitBuffer() using the stable JNI GetDirectBufferAddress API.
     */
    val ringBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(RING_BUFFER_SIZE).order(ByteOrder.nativeOrder())

    // Lifecycle

    /**
     * Initialises the native bridge and ring buffer. Must be called once before any other
     * command. Safe to call from a coroutine.
     *
     * @throws IllegalStateException if the bridge init response carries a non-OK status.
     */
    suspend fun init() {
        // If a previous destroy() cancelled the scope, create a fresh one before calling nativeInit.
        // destroy() cancels the bridge-private scope only, so the engine's scope is unaffected.
        if (!scope.isActive) {
            scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        }
        handle = NativeBridge.nativeInit(this)
        NativeBridge.nativeInitBuffer(handle, ringBuffer)
        val req = InitRequest.newBuilder()
            .setRingBufferCapacity(RING_BUFFER_SIZE)
            .build().toByteArray()
        val response = send(envelope("bench.init", req))
        check(response.status == Status.OK) {
            "bench.init failed: ${response.error}"
        }
    }

    /**
     * Drops the native handle. Safe to call multiple times.
     * In-flight Rust tasks complete independently via their Arc clones.
     */
    fun destroy() {
        cleanupHandle()
        scope.cancel()
    }

    /**
     * Sends a cancel envelope without suspending and without waiting for acknowledgement.
     * Safe to call even after destroy() because it bypasses the scope and calls JNI directly.
     * Used by forceCancel() which must not block the main thread or require a live coroutine scope.
     */
    fun cancelImmediate() {
        if (handle == 0L) return
        val env = envelope("bench.cancel", byteArrayOf())
        NativeBridge.nativeSend(handle, env.toByteArray())
    }

    /**
     * Clears any partially initialised native handle after a failed init attempt without
     * cancelling the bridge scope, so a later retry remains possible in the same process.
     */
    fun cleanupAfterFailedInit() {
        cleanupHandle()
    }

    private fun cleanupHandle() {
        if (handle == 0L) return
        NativeBridge.nativeDestroy(handle)
        handle = 0L
        pending.values.forEach { it.cancel() }
        pending.clear()
    }

    // Commands

    /**
     * Starts a benchmark suite run.
     * Returns immediately after Rust acknowledges; progress streams via the ring buffer.
     *
     * @param config [SuiteConfig] with storage_path populated.
     * @return The "bench.suite.started" ack Envelope.
     */
    suspend fun startSuite(config: SuiteConfig): Envelope =
        send(envelope(
            "bench.suite.start",
            SuiteStartRequest.newBuilder().setConfig(config).build().toByteArray()
        ))

    /**
     * Starts a single benchmark run.
     *
     * @param benchId Numeric id of the benchmark (matches bench_id in the Rust registry).
     * @param config  [SuiteConfig] with storage_path populated.
     * @return The "bench.single.started" ack Envelope.
     */
    suspend fun startSingle(benchId: Int, config: SuiteConfig): Envelope =
        send(envelope(
            "bench.single.start",
            SingleStartRequest.newBuilder()
                .setBenchId(benchId)
                .setConfig(config)
                .build().toByteArray()
        ))

    /**
     * Requests cancellation of the running benchmark. Rust writes a cancel flag into the
     * ring buffer state; running benchmarks check it periodically.
     */
    suspend fun cancel(): Envelope = send(envelope("bench.cancel", byteArrayOf()))

    /**
     * Fetches the final [ResultsResponse] after the ring buffer reports a Complete record.
     * Must only be called after the Complete record has been observed.
     */
    suspend fun getResults(): ResultsResponse {
        val resp = send(envelope("bench.results.get", byteArrayOf()))
        return ResultsResponse.parseFrom(resp.payload)
    }

    /**
     * Returns the list of all available benchmarks as reported by the Rust registry.
     * Useful at startup to validate the registry is populated.
     */
    suspend fun getAvailable(): AvailableResponse {
        val resp = send(envelope("bench.available.get", byteArrayOf()))
        return AvailableResponse.parseFrom(resp.payload)
    }

    // BridgeCallback

    override fun onEnvelope(bytes: ByteArray) {
        // Called from a JVM-attached Rust Tokio thread, dispatch to our coroutine scope.
        // to avoid blocking the Rust thread while completing the deferred.
        scope.launch {
            val env = Envelope.parseFrom(bytes)
            pending[env.correlationId]?.complete(env)
        }
    }

    // Internal

    private fun requireAlive() = check(handle != 0L) { "BenchmarkBridge has been destroyed" }

    private suspend fun send(outbound: Envelope): Envelope {
        requireAlive()
        val id = outbound.correlationId.ifEmpty { UUID.randomUUID().toString() }
        val stamped = outbound.toBuilder().setCorrelationId(id).build()
        val deferred = CompletableDeferred<Envelope>()
        pending[id] = deferred
        NativeBridge.nativeSend(handle, stamped.toByteArray())
        return try {
            withTimeout(30_000L) { deferred.await() }
        } finally {
            pending.remove(id)
        }
    }

    private fun envelope(typeUrl: String, payload: ByteArray): Envelope =
        Envelope.newBuilder()
            .setTypeUrl(typeUrl)
            .setPayload(ByteString.copyFrom(payload))
            .build()

    companion object {
        const val RING_BUFFER_SIZE = 4 * 1024 * 1024  // 4 MB
    }
}
