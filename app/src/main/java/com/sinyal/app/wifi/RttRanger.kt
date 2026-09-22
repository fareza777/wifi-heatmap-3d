package com.sinyal.app.wifi

import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.rtt.RangingRequest
import android.net.wifi.rtt.RangingResult
import android.net.wifi.rtt.RangingResultCallback
import android.net.wifi.rtt.WifiRttManager
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** One ranging burst against an access point. */
data class RttSample(
    val ok: Boolean,
    val distanceM: Double?,
    val stddevM: Double?,
    val rssiDbm: Int?,
    /** Platform status code on failure (RangingResult.STATUS_*). */
    val failCode: Int?,
)

/** Aggregated distance to one access point, medians across the bursts. */
data class RttMeasurement(
    val distanceM: Double?,
    val stddevM: Double?,
    val rssiDbm: Int?,
    val samples: Int,
    val failures: Int,
) {
    val succeeded: Boolean get() = distanceM != null
}

/**
 * IEEE 802.11mc Fine Time Measurement, wrapped for the app's needs.
 *
 * RTT measures time-of-flight rather than inferring distance from RSSI, so it
 * lands within a metre or two where signal-strength guesses are off by rooms.
 * The platform runs a burst of frame exchanges per request and returns the
 * negotiated distance; a handful of bursts are then medianed, because single
 * readings jump when a frame collides or the AP is busy.
 *
 * Needs the hardware feature (FEATURE_WIFI_RTT), Wi-Fi on, location granted,
 * and an AP that advertises itself as an RTT responder — many home routers do
 * not, which is why this stays opt-in per AP rather than a promise.
 * The ranging API arrived in Android 9; callers must check [hardwareSupported]
 * anyway, so the whole class is gated to keep pre-P devices from even linking it.
 */
@RequiresApi(Build.VERSION_CODES.P)
class RttRanger(context: Context) {

    private val appContext = context.applicationContext
    private val rttManager =
        appContext.getSystemService(Context.WIFI_RTT_RANGING_SERVICE) as? WifiRttManager

    val hardwareSupported: Boolean
        get() = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_RTT)

    /** Whether ranging can run right now — Wi-Fi toggles change this live. */
    val available: Boolean
        get() = runCatching { rttManager?.isAvailable == true }.getOrDefault(false)

    /**
     * Runs [bursts] ranging rounds against [peer], reporting each through
     * [onSample]. Medians across the successful rounds, so one collided burst
     * does not move the displayed distance.
     */
    suspend fun measure(
        peer: ScanResult,
        bursts: Int = DEFAULT_BURSTS,
        onSample: (RttSample) -> Unit = {},
    ): RttMeasurement = withContext(Dispatchers.IO) {
        val distances = mutableListOf<Double>()
        val stddevs = mutableListOf<Double>()
        val rssis = mutableListOf<Int>()
        var failures = 0

        repeat(bursts) { round ->
            if (round > 0) delay(ROUND_GAP_MS)
            val sample = runCatching { singleBurst(peer) }.getOrElse {
                RttSample(ok = false, distanceM = null, stddevM = null, rssiDbm = null, failCode = null)
            }
            onSample(sample)

            if (sample.ok && sample.distanceM != null) {
                distances += sample.distanceM
                sample.stddevM?.let(stddevs::add)
                sample.rssiDbm?.let(rssis::add)
            } else {
                failures++
            }
        }

        RttMeasurement(
            distanceM = distances.medianOrNull(),
            stddevM = stddevs.medianOrNull(),
            rssiDbm = rssis.sorted().getOrNull(rssis.size / 2),
            samples = distances.size,
            failures = failures,
        )
    }

    /** One platform ranging request, suspended until the callback lands. */
    private suspend fun singleBurst(peer: ScanResult): RttSample =
        suspendCancellableCoroutine { continuation ->
            val manager = rttManager ?: run {
                continuation.resume(
                    RttSample(false, null, null, null, RangingResult.STATUS_FAIL),
                )
                return@suspendCancellableCoroutine
            }

            val callback = object : RangingResultCallback() {
                override fun onRangingResults(results: List<RangingResult>) {
                    val result = results.firstOrNull()
                    val resumed = if (result != null && result.status == RangingResult.STATUS_SUCCESS) {
                        RttSample(
                            ok = true,
                            distanceM = result.distanceMm / 1000.0,
                            stddevM = runCatching { result.distanceStdDevMm / 1000.0 }
                                .getOrNull(),
                            rssiDbm = runCatching { result.rssi }.getOrNull(),
                            failCode = null,
                        )
                    } else {
                        RttSample(
                            ok = false,
                            distanceM = null,
                            stddevM = null,
                            rssiDbm = null,
                            failCode = result?.status,
                        )
                    }
                    if (continuation.isActive) continuation.resume(resumed)
                }

                @Deprecated("Required by the callback contract before API 30.")
                override fun onRangingFailure(code: Int) {
                    if (continuation.isActive) {
                        continuation.resume(
                            RttSample(false, null, null, null, code),
                        )
                    }
                }
            }

            val request = RangingRequest.Builder()
                .addAccessPoint(peer)
                .build()

            try {
                manager.startRanging(
                    request,
                    androidx.core.content.ContextCompat.getMainExecutor(appContext),
                    callback,
                )
            } catch (e: SecurityException) {
                continuation.resume(
                    RttSample(false, null, null, null, RangingResult.STATUS_FAIL),
                )
            } catch (e: IllegalArgumentException) {
                // Stale ScanResult — the AP vanished since the scan was cached.
                continuation.resume(
                    RttSample(false, null, null, null, RangingResult.STATUS_FAIL),
                )
            }
        }

    private fun List<Double>.medianOrNull(): Double? {
        if (isEmpty()) return null
        val sorted = sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        } else {
            sorted[mid]
        }
    }

    private companion object {
        /** Enough bursts to let the median shrug off a collision. */
        const val DEFAULT_BURSTS = 5

        /** Back-to-back requests hammer the AP; a gap keeps it responsive. */
        const val ROUND_GAP_MS = 450L
    }
}
