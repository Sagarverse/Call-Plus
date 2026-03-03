package com.example.call.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class ShakeDetector(
    context: Context,
    private val onShakeDetected: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    
    private var lastShakeTime = 0L
    
    companion object {
        private const val SHAKE_THRESHOLD = 5.0f // Requires a very firm shake
        private const val SHAKE_COOLDOWN = 1500L // 1.5 seconds between triggers
    }

    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH

        // Calculate G-force across all axes (approaches 1.0 when stationary)
        val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)
        
        if (gForce > SHAKE_THRESHOLD) {
            val now = System.currentTimeMillis()
            if (now - lastShakeTime > SHAKE_COOLDOWN) {
                lastShakeTime = now
                onShakeDetected()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
