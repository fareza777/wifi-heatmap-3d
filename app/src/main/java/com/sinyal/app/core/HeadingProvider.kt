package com.sinyal.app.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Compass heading, used to tie ARCore's arbitrary yaw to the real world.
 *
 * ARCore builds a gravity-aligned frame whose north is wherever the phone
 * happened to point when the session began. Recording the azimuth at that
 * moment is what lets every later position be expressed as a real bearing
 * instead of "somewhere off to the left of where I started".
 */
class HeadingProvider(context: Context) : SensorEventListener {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVector: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    /** Degrees clockwise from magnetic north, or null before the first reading. */
    @Volatile
    var azimuthDegrees: Float? = null
        private set

    val isAvailable: Boolean get() = rotationVector != null

    fun start() {
        rotationVector?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientation)
        val degrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
        azimuthDegrees = (degrees + 360f) % 360f
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
