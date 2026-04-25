package io.github.iamlooper.benchsuite.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes records from the SPSC ring buffer shared with the Rust engine.
 *
 * The buffer layout:
 *   0x0000  8B  magic          0x42454E4348535549 ("BENCHSUI")
 *   0x0008  4B  version
 *   0x000C  4B  capacity       (ring data capacity in bytes)
 *   0x0010  4B  record_size    (64 bytes, fixed)
 *   0x0014  4B  _pad0
 *   0x0018  8B  write_index    (atomic, Rust writes with Release semantics)
 *   0x0020  8B  read_index     (atomic, Kotlin writes with Acquire semantics)
 *   0x0028  4B  state          (0=idle, 1=running, 2=done, 3=error)
 *   0x002C  4B  dropped_count
 *   0x0030  16B _pad1
 *   0x0040  … ring data region
 *
 * Because the JVM does not expose memory-order fences for ByteBuffer reads, we
 * use a volatile-read idiom: we re-read write_index on every poll() call and
 * treat it as the latest committed count. The ring buffer is populated on Rust's
 * Tokio blocking thread pool, and the Choreographer callback runs on the main
 * thread, there is a natural happens-before through the display frame callback,
 * which is sufficient in practice given the 64-byte record granularity and the
 * Rust Release store on write_index.
 */
class RingBufferReader(private val buffer: ByteBuffer) {

    companion object {
        private const val MAGIC_EXPECTED = 0x42454E4348535549L  // "BENCHSUI"
        private const val HEADER_SIZE    = 0x0040
        private const val RECORD_SIZE    = 64

        // Header field offsets
        private const val OFF_MAGIC        = 0x0000
        private const val OFF_CAPACITY     = 0x000C
        private const val OFF_WRITE_INDEX  = 0x0018
        private const val OFF_READ_INDEX   = 0x0020
        private const val OFF_STATE        = 0x0028
        private const val OFF_DROPPED      = 0x002C
    }

    // Native-order little-endian view of the DirectByteBuffer
    private val view: ByteBuffer = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)

    // Tracks how many records we have already consumed; mirrors the ring's read_index semantics.
    private var localReadIndex: Long = 0L

    // Whether we have seen a validated header (Rust writes it on bench.init).
    private var headerValidated = false

    /** Ring data capacity in bytes, derived from the header. Initialised on first validation. */
    private var ringCapacity: Int = 0

    /**
     * Returns all new records written by Rust since the last call.
     * Returns empty list if the header is not yet valid or no new records exist.
     * Advances [localReadIndex] for consumed records.
     * Also writes read_index back to the buffer so Rust can track consumer progress.
     */
    fun poll(): List<RingRecord> {
        if (!headerValidated) {
            if (!validateHeader()) return emptyList()
        }

        val writeIndex = view.getLong(OFF_WRITE_INDEX)
        val available  = (writeIndex - localReadIndex).coerceAtLeast(0L)
        if (available == 0L) return emptyList()

        val records = mutableListOf<RingRecord>()
        repeat(available.coerceAtMost(1024L).toInt()) {
            val slot = (localReadIndex % (ringCapacity / RECORD_SIZE)).toInt()
            val base = HEADER_SIZE + slot * RECORD_SIZE
            records.add(decodeRecord(base))
            localReadIndex++
        }

        // Write back so Rust knows this far has been consumed
        view.putLong(OFF_READ_INDEX, localReadIndex)
        return records
    }

    /**
     * Resets the local read cursor to the ring buffer's current write position, effectively
     * discarding all records buffered by a previous run. Must be called (on the main thread,
     * with Choreographer stopped) before starting a new run so stale records from a
     * force-cancelled run are never delivered to the new run's progress handlers.
     */
    fun resetToCurrentWriteIndex() {
        if (!headerValidated && !validateHeader()) return
        localReadIndex = view.getLong(OFF_WRITE_INDEX).coerceAtLeast(0L)
        view.putLong(OFF_READ_INDEX, localReadIndex)
    }

    /** Returns the engine state byte (0=idle, 1=running, 2=done, 3=error). */
    fun engineState(): Int {
        if (!headerValidated && !validateHeader()) return 0
        return view.getInt(OFF_STATE)
    }

    /** Number of records dropped by Rust due to a full ring buffer. */
    fun droppedCount(): Int {
        if (!headerValidated && !validateHeader()) return 0
        return view.getInt(OFF_DROPPED)
    }

    // Private helpers

    private fun validateHeader(): Boolean {
        // The header is populated by Rust on the bench.init response path.
        // We read magic as two ints due to ByteBuffer alignment constraints.
        val magic = view.getLong(OFF_MAGIC)
        if (magic != MAGIC_EXPECTED) return false
        ringCapacity = view.getInt(OFF_CAPACITY)
        if (ringCapacity <= 0 || ringCapacity % RECORD_SIZE != 0) return false
        headerValidated = true
        return true
    }

    private fun decodeRecord(base: Int): RingRecord {
        // Record layout (all little-endian)
        val recordType    = view.get(base + 0x00)
        val categoryId    = view.get(base + 0x01)
        val benchId       = view.getShort(base + 0x02).toInt() and 0xFFFF
        val flags         = view.getInt(base + 0x04)
        val sequence      = view.getInt(base + 0x08)
        val phase         = view.getInt(base + 0x0C)
        val currentIter   = view.getInt(base + 0x10)
        val totalIter     = view.getInt(base + 0x14)
        val timestampNs   = view.getLong(base + 0x18)
        val valuePrimary  = view.getDouble(base + 0x20)
        val valueSecondary = view.getDouble(base + 0x28)
        val unit          = view.getInt(base + 0x30)
        val metricId      = view.getInt(base + 0x34)
        return RingRecord(
            recordType, categoryId, benchId, flags, sequence, phase,
            currentIter, totalIter, timestampNs, valuePrimary, valueSecondary,
            unit, metricId
        )
    }
}
