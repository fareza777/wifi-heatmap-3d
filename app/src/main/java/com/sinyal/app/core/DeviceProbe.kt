package com.sinyal.app.core

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.wifi.WifiManager
import android.os.Build

/** Static hardware facts that decide whether a scan can work on this device. */
data class DeviceReport(
    val model: String,
    val androidRelease: String,
    val apiLevel: Int,
    val soc: String,
    val totalRamMb: Long,
    val hasGyroscope: Boolean,
    val gyroMaxRateHz: Int,
    val cameraHardwareLevel: String,
    val supports5GHz: Boolean,
    val supports6GHz: Boolean,
    val supportsWifiRtt: Boolean,
)

class DeviceProbe(context: Context) {

    private val appContext = context.applicationContext

    fun read(): DeviceReport {
        val sensors = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val gyro: Sensor? = sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val pm = appContext.packageManager

        return DeviceReport(
            model = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidRelease = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            soc = socName(),
            totalRamMb = totalRamMb(),
            hasGyroscope = gyro != null,
            gyroMaxRateHz = gyro?.let { maxRateHz(it) } ?: 0,
            cameraHardwareLevel = cameraHardwareLevel(),
            supports5GHz = runCatching { wifi.is5GHzBandSupported }.getOrDefault(false),
            supports6GHz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { wifi.is6GHzBandSupported }.getOrDefault(false)
            } else false,
            supportsWifiRtt = pm.hasSystemFeature(PackageManager.FEATURE_WIFI_RTT),
        )
    }

    private fun socName(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL)
                .filter { it.isNotBlank() && it != Build.UNKNOWN }
                .joinToString(" ")
                .ifBlank { Build.HARDWARE }
        } else {
            Build.HARDWARE
        }

    private fun totalRamMb(): Long {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem / (1024 * 1024)
    }

    /** minDelay is microseconds between samples; invert it for a rate. */
    private fun maxRateHz(sensor: Sensor): Int =
        if (sensor.minDelay > 0) 1_000_000 / sensor.minDelay else 0

    private fun cameraHardwareLevel(): String = runCatching {
        val manager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val backId = manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: return@runCatching "—"

        val level = manager.getCameraCharacteristics(backId)
            .get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)

        when (level) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> "—"
        }
    }.getOrDefault("—")
}
