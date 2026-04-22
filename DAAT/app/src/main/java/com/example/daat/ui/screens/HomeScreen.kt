package com.example.daat.ui.screens

import android.Manifest
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.util.Log
import android.view.Surface
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.daat.logic.VerificationUtils
import com.example.daat.ui.viewmodel.GameViewModel
import com.example.daat.ui.viewmodel.VerificationStatus
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import kotlin.math.abs

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(viewModel: GameViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Multiple Permissions for Camera and Location
    val permissionsState = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // Compass/Heading tracking
    var currentHeading by remember { mutableStateOf(0.0) }
    
    // Live location tracking
    var currentLat by remember { mutableStateOf(uiState.currentUser?.latitude ?: 0.0) }
    var currentLon by remember { mutableStateOf(uiState.currentUser?.longitude ?: 0.0) }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    DisposableEffect(permissionsState.allPermissionsGranted) {
        if (!permissionsState.allPermissionsGranted) {
            return@DisposableEffect onDispose {}
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    currentLat = location.latitude
                    currentLon = location.longitude
                    viewModel.onUpdateLocation(location.latitude, location.longitude)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                context.mainLooper
            )
        } catch (e: SecurityException) {
            Log.e("HomeScreen", "Location permission missing", e)
        }

        onDispose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    DisposableEffect(permissionsState.allPermissionsGranted) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        
        val listener = object : SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private val orientation = FloatArray(3)

            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    
                    // Standard remap for portrait orientation where phone is held upright (camera-style)
                    val adjustedRotationMatrix = FloatArray(9)
                    SensorManager.remapCoordinateSystem(
                        rotationMatrix,
                        SensorManager.AXIS_X,
                        SensorManager.AXIS_Z,
                        adjustedRotationMatrix
                    )
                    
                    SensorManager.getOrientation(adjustedRotationMatrix, orientation)
                    val azimuthDegrees = Math.toDegrees(orientation[0].toDouble())
                    currentHeading = (azimuthDegrees + 360) % 360
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        
        if (permissionsState.allPermissionsGranted) {
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
        }
        
        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    val imageCapture = remember { ImageCapture.Builder().build() }

    LaunchedEffect(uiState.verificationStatus) {
        when (uiState.verificationStatus) {
            VerificationStatus.SUCCESS -> snackbarHostState.showSnackbar("Target Eliminated! +${uiState.lastPointsAwarded} pts")
            VerificationStatus.FAILED_LOCATION -> snackbarHostState.showSnackbar("Too far away from target!")
            VerificationStatus.FAILED_TIME -> snackbarHostState.showSnackbar("Photo is too old!")
            VerificationStatus.FAILED_ORIENTATION -> snackbarHostState.showSnackbar("You're not facing the target!")
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (permissionsState.allPermissionsGranted) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                CameraPreview(modifier = Modifier.fillMaxSize(), imageCapture = imageCapture)

                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Target Name
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("DAILY TARGET", style = MaterialTheme.typography.labelLarge, color = Color.White)
                            Text(
                                text = uiState.currentTarget?.name ?: "No Target Assigned",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Tactical Compass with Real Data
                    val targetBearing = remember(uiState.currentTarget, currentLat, currentLon) {
                        val t = uiState.currentTarget
                        if (t != null && t.latitude != null && t.longitude != null) {
                            VerificationUtils.calculateBearing(currentLat, currentLon, t.latitude, t.longitude)
                        } else 0.0
                    }
                    
                    TacticalCompass(
                        currentHeading = currentHeading,
                        targetBearing = targetBearing,
                        modifier = Modifier.size(200.dp)
                    )

                    // Debug coordinates (remove for production)
                    if (uiState.currentTarget != null) {
                        val dist = VerificationUtils.calculateDistance(
                            currentLat, currentLon, 
                            uiState.currentTarget?.latitude ?: 0.0, 
                            uiState.currentTarget?.longitude ?: 0.0
                        )
                        Text(
                            text = "${dist.toInt()}m to target",
                            color = Color.White,
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Capture Button
                    Button(
                        onClick = {
                            takePhoto(
                                context = context,
                                imageCapture = imageCapture,
                                executor = ContextCompat.getMainExecutor(context),
                                onImageCaptured = { uri ->
                                    viewModel.onCaptureButtonPressed(
                                        imageUrl = uri.toString(),
                                        hunterLat = currentLat,
                                        hunterLon = currentLon,
                                        hunterHeading = currentHeading,
                                        capturedAt = System.currentTimeMillis()
                                    )
                                },
                                onError = { Log.e("Camera", "Capture failed", it) }
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = uiState.verificationStatus != VerificationStatus.VERIFYING && uiState.currentTarget != null,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (uiState.verificationStatus == VerificationStatus.VERIFYING) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("CAPTURE SNIPE", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // Permission Request UI
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Camera and Location permissions are required.", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("We need your location to verify snipes and your camera to capture them.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                    Text("Grant Permissions")
                }
            }
        }
    }
}

@Composable
fun TacticalCompass(
    currentHeading: Double,
    targetBearing: Double,
    modifier: Modifier = Modifier
) {
    // animatedHeading represents where the PHONE is pointing
    val animatedHeading by animateFloatAsState(targetValue = currentHeading.toFloat())
    
    val diff = abs(currentHeading - targetBearing)
    val wrappedDiff = if (diff > 180) 360 - diff else diff
    val isAligned = wrappedDiff <= 30.0

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Outer ring - rotates so that the "Red Tab" always points at the phone's current heading relative to North
        Canvas(modifier = Modifier.fillMaxSize()) {
            rotate(degrees = -animatedHeading) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.3f),
                    radius = size.minDimension / 2,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
                // North indicator on the outer ring
                drawRect(
                    color = Color.White,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width / 2 - 2.dp.toPx(), 0f),
                    size = androidx.compose.ui.geometry.Size(4.dp.toPx(), 10.dp.toPx())
                )
            }
        }

        // Target Arrow - rotates to point at the target's bearing relative to the phone's heading
        Canvas(modifier = Modifier.fillMaxSize()) {
            // The arrow should point at (targetBearing - currentHeading) degrees relative to the top of the phone
            rotate(degrees = (targetBearing - animatedHeading).toFloat()) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width / 2, 10.dp.toPx())
                    lineTo(size.width / 2 - 12.dp.toPx(), 35.dp.toPx())
                    lineTo(size.width / 2 + 12.dp.toPx(), 35.dp.toPx())
                    close()
                }
                drawPath(path = path, color = if (isAligned) Color.Green else Color.Red)
            }
        }

        // Static "Forward" marker (Top of phone)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                color = Color.Cyan.copy(alpha = 0.5f),
                topLeft = androidx.compose.ui.geometry.Offset(size.width / 2 - 1.dp.toPx(), 0f),
                size = androidx.compose.ui.geometry.Size(2.dp.toPx(), 20.dp.toPx())
            )
        }

        Box(
            modifier = Modifier.size(45.dp).background(if (isAligned) Color.Green.copy(alpha = 0.2f) else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "${currentHeading.toInt()}°", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CameraPreview(modifier: Modifier = Modifier, imageCapture: ImageCapture) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder()
                    .setTargetRotation(previewView.display.rotation)
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                } catch (e: Exception) { Log.e("CameraPreview", "Use case binding failed", e) }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = modifier
    )
}

private fun takePhoto(context: Context, imageCapture: ImageCapture, executor: Executor, onImageCaptured: (Uri) -> Unit, onError: (ImageCaptureException) -> Unit) {
    val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())
    val file = File(context.cacheDir, "$name.jpg")
    
    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
    
    imageCapture.takePicture(outputOptions, executor, object : ImageCapture.OnImageSavedCallback {
        override fun onError(exception: ImageCaptureException) {
            Log.e("Camera", "Photo capture failed: ${exception.message}", exception)
            onError(exception)
        }
        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
            val savedUri = Uri.fromFile(file)
            Log.d("Camera", "Photo capture succeeded: $savedUri")
            onImageCaptured(savedUri)
        }
    })
}
