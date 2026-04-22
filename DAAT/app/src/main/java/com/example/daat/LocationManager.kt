package com.example.daat

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.firestore.FirebaseFirestore

class LocationManager(private val context: Context) : SensorEventListener {

    private val db = FirebaseFirestore.getInstance()
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private var currentHeading = 0.0

    /** One-shot fetch + save — used on login and snipe events. */
    @SuppressLint("MissingPermission")
    fun getAndSaveLocation(
        onSuccess: (lat: Double, lng: Double, heading: Double) -> Unit,
        onFailure: (String) -> Unit
    ) {
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    saveLocationToFirebase(location.latitude, location.longitude, currentHeading, onSuccess, onFailure)
                } else {
                    onFailure("Failed to get location: location is null")
                }
            }
            .addOnFailureListener { e ->
                onFailure("Failed to get location: ${e.message}")
            }
    }

    private fun saveLocationToFirebase(
        lat: Double,
        lng: Double,
        heading: Double,
        onSuccess: (Double, Double, Double) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val locationData = hashMapOf(
            "latitude" to lat,
            "longitude" to lng,
            "heading" to heading,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("locations")
            .add(locationData)
            .addOnSuccessListener {
                onSuccess(lat, lng, heading)
            }
            .addOnFailureListener { e ->
                onFailure("Firebase save failed: ${e.message}")
            }
    }

    fun startOrientationUpdates() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopOrientationUpdates() {
        sensorManager.unregisterListener(this)
    }

    // Passive updates can be implemented if needed for background tracking
    fun startPassiveLocationUpdates() {
        // Implementation for background updates if required
    }

    fun stopPassiveLocationUpdates() {
        // Implementation for background updates if required
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            val azimuthDegrees = Math.toDegrees(orientation[0].toDouble())
            currentHeading = (azimuthDegrees + 360) % 360
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
