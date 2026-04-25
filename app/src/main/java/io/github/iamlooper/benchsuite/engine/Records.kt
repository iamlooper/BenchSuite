package io.github.iamlooper.benchsuite.engine

/** Marker constants for the record_type byte in a 64-byte ring buffer record. */
object RecordType {
    const val PROGRESS: Byte = 0x01
    const val SAMPLE: Byte   = 0x02
    const val LOG: Byte      = 0x03
    const val COMPLETE: Byte = 0xFF.toByte()
}

/** Marker constants for the unit field in a ring buffer record. */
object UnitId {
    const val NS_PER_OP   = 0
    const val MB_PER_SEC  = 1
    const val OPS_PER_SEC = 2
    const val US_PER_OP   = 3
    const val GB_PER_SEC  = 4
}

/** Marker constants for the phase field in a ring buffer record. */
object Phase {
    const val WARMUP  = 0
    const val MEASURE = 1
    const val COOLDOWN = 2
}

/**
 * Decoded view of a single 64-byte ring buffer record.
 *
 * Layout:
 *   0x00  1B  record_type
 *   0x01  1B  category_id
 *   0x02  2B  bench_id       (u16, little-endian)
 *   0x04  4B  flags
 *   0x08  4B  sequence
 *   0x0C  4B  phase
 *   0x10  4B  current_iter
 *   0x14  4B  total_iter
 *   0x18  8B  timestamp_ns
 *   0x20  8B  value_primary  (f64)
 *   0x28  8B  value_secondary (f64)
 *   0x30  4B  unit
 *   0x34  4B  metric_id
 *   0x38  8B  _reserved
 */
data class RingRecord(
    val recordType: Byte,
    val categoryId: Byte,
    val benchId: Int,
    val flags: Int,
    val sequence: Int,
    val phase: Int,
    val currentIter: Int,
    val totalIter: Int,
    val timestampNs: Long,
    val valuePrimary: Double,
    val valueSecondary: Double,
    val unit: Int,
    val metricId: Int,
)
