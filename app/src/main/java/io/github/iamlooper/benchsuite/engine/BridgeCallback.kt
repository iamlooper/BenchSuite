package io.github.iamlooper.benchsuite.engine

/** Interface implemented by BenchmarkBridge to receive Envelope responses from Rust. */
interface BridgeCallback {
    /** Called from any thread when Rust delivers a response Envelope. */
    fun onEnvelope(bytes: ByteArray)
}
