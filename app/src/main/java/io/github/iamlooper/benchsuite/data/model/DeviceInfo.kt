package io.github.iamlooper.benchsuite.data.model

/** Device hardware snapshot collected at benchmark startup. */
data class DeviceInfo(
    val brand: String,
    val model: String,
    val deviceName: String,
    val soc: String,
    val abi: String,
    val cpuCores: Int,
    val ramBytes: Long,
    val androidApi: Int,
    val fingerprintHash: String,      // SHA-256(Build.FINGERPRINT) hex, used for Supabase dedup
    val batteryLevel: Int,            // 0–100 at run start; -1 if unavailable
    val isCharging: Boolean,
)
