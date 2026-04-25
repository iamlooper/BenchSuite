package io.github.iamlooper.benchsuite.engine

import java.nio.ByteBuffer

/**
 * JNI interface to the Rust native engine.
 *
 * Call order per lifecycle:
 *   1. [nativeInit] - returns opaque handle (Arc<Mutex<BridgeContext>> pointer cast to jlong).
 *   2. [nativeInitBuffer] - delivers the DirectByteBuffer to Rust so it can call
 *      GetDirectBufferAddress() for zero-copy ring buffer access.
 *   3. [nativeSend] - repeated, for each Envelope command.
 *   4. [nativeDestroy] - drops the Arc clone held by the handle.
 */
object NativeBridge {
    init {
        System.loadLibrary("benchsuite")
    }

    /** Initialises the native BridgeContext and returns an opaque handle. */
    external fun nativeInit(callback: BridgeCallback): Long

    /**
     * Delivers [ringBuffer] (a direct ByteBuffer) to Rust via GetDirectBufferAddress().
     * Must be called immediately after [nativeInit] and before any [nativeSend] calls.
     */
    external fun nativeInitBuffer(handle: Long, ringBuffer: ByteBuffer)

    /**
     * Serialises [envelopeBytes] (a Protobuf-encoded Envelope) and dispatches it
     * asynchronously on the Rust Tokio runtime. Returns immediately.
     */
    external fun nativeSend(handle: Long, envelopeBytes: ByteArray)

    /**
     * Drops the Arc clone held by [handle]. In-flight Tokio tasks retain their own
     * Arc clones and run to completion before the BridgeContext is deallocated.
     */
    external fun nativeDestroy(handle: Long)
}
