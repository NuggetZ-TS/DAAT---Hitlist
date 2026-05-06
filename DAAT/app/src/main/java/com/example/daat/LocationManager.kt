package com.example.daat

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for the hunter's location and compass heading.
 *
 * Location strategy
 * -----------------
 * • [startPassiveLocationUpdates] calls [FusedLocationProviderClient.requestLocationUpdates]
 *   with a 1 s target interval (0.5 s fastest).  GPS hardware typically delivers
 *   fixes every 1–2 s, so [locationFlow] updates at roughly that rate.
 * • Firestore writes are **throttled** to at most once every 15 s so we don't
 *   blow the free-tier write quota (~100k writes/day).
 * • [getAndSaveLocation] (one-shot, high-accuracy) is still used on login /
 *   after a successful snipe to guarantee a fresh fix is persisted immediately.
 *
 * Heading strategy
 * ----------------
 * • The rotation-vector sensor runs at SENSOR_DELAY_UI (~60 ms).
 * • [currentHeading] is a plain property read directly by HomeScreen at
 *   100 ms poll rate — no duplicate sensor listener needed.
 */
class LocationManager(private val context: Context) : SensorEventListener {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ── Public state ──────────────────────────────────────────────────────────

    /**
     * Emits every GPS fix as it arrives from [requestLocationUpdates] (~1 s).
     * Collect this in HomeScreen instead of polling [getCurrentLocation].
     */
    private val _locationFlow = MutableStateFlow<Location?>(null)
    val locationFlow: StateFlow<Location?> = _locationFlow.asStateFlow()

    /**
     * Camera azimuth (0 = North, clockwise) computed from the rotation-vector
     * sensor with portrait-mode axis remapping.  Read directly by HomeScreen.
     */
    var currentHeading = 0.0
        private set

    // ── Firestore write throttle ──────────────────────────────────────────────

    /** Epoch-ms of the most recent Firestore write; 0 = never written. */
    @Volatile private var lastFirestoreWrite = 0L

    /** Minimum gap between passive Firestore writes (15 s). */
    private val FIRESTORE_THROTTLE_MS = 15_000L

    // ── Continuous location updates ───────────────────────────────────────────

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        1_000L          // target interval: 1 s
    ).apply {
        setMinUpdateIntervalMillis(500L)   // fastest interval: 0.5 s
        setWaitForAccurateLocation(false)  // don't stall waiting for high-accuracy fix
    }.build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            _locationFlow.value = location

            // Throttled Firestore write — keep target's view of hunter fresh
            val now = System.currentTimeMillis()
            if (now - lastFirestoreWrite >= FIRESTORE_THROTTLE_MS) {
                lastFirestoreWrite = now
                saveToUserDoc(
                    lat = location.latitude,
                    lng = location.longitude,
                    heading = currentHeading,
                    onSuccess = { _, _, _ -> },
                    onFailure = {}
                )
            }
        }
    }

    /**
     * Starts continuous GPS updates via [requestLocationUpdates].
     * Safe to call from onResume — no-ops if already active.
     * Must be paired with [stopPassiveLocationUpdates] in onPause.
     */
    @SuppressLint("MissingPermission")
    fun startPassiveLocationUpdates() {
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (_: SecurityException) {
            // Permission not yet granted — MainActivity will request it
        }
    }

    fun stopPassiveLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    // ── One-shot high-accuracy fetch (login / snipe events) ───────────────────

    /**
     * Fetches a single high-accuracy GPS fix and immediately writes it to
     * Firestore (bypassing the throttle).  Call on login and after each snipe.
     */
    @SuppressLint("MissingPermission")
    fun getAndSaveLocation(
        onSuccess: (lat: Double, lng: Double, heading: Double) -> Unit,
        onFailure: (String) -> Unit
    ) {
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    _locationFlow.value = location          // update the flow too
                    lastFirestoreWrite = System.currentTimeMillis()
                    saveToUserDoc(location.latitude, location.longitude, currentHeading, onSuccess, onFailure)
                } else {
                    onFailure("Location is null — GPS may not have a fix yet")
                }
            }
            .addOnFailureListener { e ->
                onFailure("Location fetch failed: ${e.message}")
            }
    }

    // ── Orientation sensor ────────────────────────────────────────────────────

    fun startOrientationUpdates() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopOrientationUpdates() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ROTATION_VECTOR) return

        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        // Remap for portrait-upright phone: camera lens (device Z) → world North.
        val remapped = FloatArray(9)
        SensorManager.remapCoordinateSystem(
            rotationMatrix,
            SensorManager.AXIS_X,
            SensorManager.AXIS_Z,
            remapped
        )

        val orientation = FloatArray(3)
        SensorManager.getOrientation(remapped, orientation)
        val azimuthDeg = Math.toDegrees(orientation[0].toDouble())
        currentHeading = (azimuthDeg + 360) % 360
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ── Firestore write ───────────────────────────────────────────────────────

    private fun saveToUserDoc(
        lat: Double,
        lng: Double,
        heading: Double,
        onSuccess: (Double, Double, Double) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            onFailure("No authenticated user")
            return
        }

        db.collection("users").document(userId)
            .update(
                mapOf(
                    "latitude" to lat,
                    "longitude" to lng,
                    "heading" to heading,
                    "lastLocationUpdate" to System.currentTimeMillis()
                )
            )
            .addOnSuccessListener { onSuccess(lat, lng, heading) }
            .addOnFailureListener { e -> onFailure("Firestore write failed: ${e.message}") }
    }
}