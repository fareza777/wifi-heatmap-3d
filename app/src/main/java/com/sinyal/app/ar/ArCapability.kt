package com.sinyal.app.ar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.sinyal.app.R
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session

enum class ArSupport(@StringRes val label: Int) {
    SUPPORTED(R.string.ar_supported),
    NEEDS_INSTALL(R.string.ar_needs_install),
    NEEDS_UPDATE(R.string.ar_needs_update),
    UNSUPPORTED(R.string.ar_unsupported),
    CHECKING(R.string.ar_checking),
    UNKNOWN(R.string.ar_unknown),
}

/**
 * Result of probing ARCore on this device.
 *
 * [depthSupported] is null until a real [Session] has been opened — the answer
 * cannot be derived from the device model, and the published support list is
 * ambiguous for some variants, so we ask the runtime rather than guess.
 */
data class ArCapabilityReport(
    val support: ArSupport,
    val arcoreVersionName: String?,
    val depthSupported: Boolean?,
    val instantPlacementSupported: Boolean?,
    val probeError: String?,
)

class ArCapability(context: Context) {

    private val appContext = context.applicationContext

    private val hasCameraPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Cheap availability check. Safe to call from the UI; [ArCoreApk] may answer
     * [ArSupport.CHECKING] on the first call and settle on a later one.
     */
    fun checkSupport(): ArSupport =
        when (ArCoreApk.getInstance().checkAvailability(appContext)) {
            ArCoreApk.Availability.SUPPORTED_INSTALLED -> ArSupport.SUPPORTED
            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD -> ArSupport.NEEDS_UPDATE
            ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> ArSupport.NEEDS_INSTALL
            ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> ArSupport.UNSUPPORTED
            ArCoreApk.Availability.UNKNOWN_CHECKING -> ArSupport.CHECKING
            else -> ArSupport.UNKNOWN
        }

    fun arcoreVersionName(): String? = runCatching {
        appContext.packageManager.getPackageInfo(ARCORE_PACKAGE, 0).versionName
    }.getOrNull()

    /**
     * Opens a throwaway session to read the capabilities that only the runtime
     * knows. Blocking and camera-permission gated, so call it off the main thread.
     */
    fun probe(): ArCapabilityReport {
        val support = checkSupport()
        val version = arcoreVersionName()

        if (support != ArSupport.SUPPORTED) {
            return ArCapabilityReport(support, version, null, null, null)
        }
        if (!hasCameraPermission) {
            return ArCapabilityReport(
                support, version, null, null,
                probeError = appContext.getString(R.string.ar_camera_permission_needed),
            )
        }

        var session: Session? = null
        return try {
            session = Session(appContext)
            ArCapabilityReport(
                support = support,
                arcoreVersionName = version,
                depthSupported = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC),
                instantPlacementSupported = true,
                probeError = null,
            )
        } catch (t: Throwable) {
            ArCapabilityReport(
                support = support,
                arcoreVersionName = version,
                depthSupported = null,
                instantPlacementSupported = null,
                probeError = t.javaClass.simpleName + (t.message?.let { ": $it" } ?: ""),
            )
        } finally {
            runCatching { session?.close() }
        }
    }

    private companion object {
        const val ARCORE_PACKAGE = "com.google.ar.core"
    }
}
