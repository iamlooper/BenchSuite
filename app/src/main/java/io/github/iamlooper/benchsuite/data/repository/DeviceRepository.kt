package io.github.iamlooper.benchsuite.data.repository

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.iamlooper.benchsuite.data.model.DeviceInfo
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Collects device metadata for leaderboard uploads from Android system APIs.
 *
 * Build.SOC_MODEL is available on API 31+. On older APIs we fall back to
 * Build.HARDWARE as a best-effort SoC name.
 */
@Singleton
class DeviceRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /** Returns a [DeviceInfo] snapshot for leaderboard upload metadata. */
    fun getDeviceInfo(): DeviceInfo {
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.takeIf { it.isNotBlank() }
                ?: Build.HARDWARE.ifBlank { "unknown" }
        } else {
            Build.HARDWARE.ifBlank { "unknown" }
        }

        val fingerprint = Build.FINGERPRINT
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(fingerprint.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE)
            as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        // Battery level and charging state via the sticky ACTION_BATTERY_CHANGED broadcast.
        // No permission is required; registerReceiver with null receiver returns the last sticky value.
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        val rawLevel = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val rawScale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryLevel = if (rawLevel >= 0 && rawScale > 0) {
            (rawLevel / rawScale.toFloat() * 100).toInt()
        } else {
            -1
        }
        val chargingStatus = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = chargingStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
                         chargingStatus == BatteryManager.BATTERY_STATUS_FULL

        return DeviceInfo(
            brand            = Build.MANUFACTURER.ifBlank { "unknown" },
            model            = Build.MODEL.ifBlank { "unknown" },
            deviceName       = Build.DEVICE.ifBlank { "unknown" },
            soc              = soc,
            abi              = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            cpuCores         = Runtime.getRuntime().availableProcessors(),
            ramBytes         = memInfo.totalMem,
            androidApi       = Build.VERSION.SDK_INT,
            fingerprintHash  = hash,
            batteryLevel     = batteryLevel,
            isCharging       = isCharging,
        )
    }
}
