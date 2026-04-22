package com.example.daat

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore

class LocationManager(private val context: Context) {

    private val db = FirebaseFirestore.getInstance()

    fun getAndSaveLocation(
        onSuccess: (lat: Double, lng: Double) -> Unit,
        onFailure: (String) -> Unit
    ) {
        // Dummy location (New York City) for testing
        val dummyLat = 40.7128
        val dummyLng = -74.0060

        saveLocationToFirebase(dummyLat, dummyLng, onSuccess, onFailure)
    }

    private fun saveLocationToFirebase(
        lat: Double,
        lng: Double,
        onSuccess: (Double, Double) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val locationData = hashMapOf(
            "latitude" to lat,
            "longitude" to lng,
            "timestamp" to System.currentTimeMillis(),
            "note" to "dummy test location"
        )

        db.collection("locations")
            .add(locationData)
            .addOnSuccessListener {
                println("Firebase save SUCCESS: $lat, $lng")
                onSuccess(lat, lng)
            }
            .addOnFailureListener { e ->
                println("Firebase save FAILED: ${e.message}")
                onFailure("Firebase save failed: ${e.message}")
            }
    }
}