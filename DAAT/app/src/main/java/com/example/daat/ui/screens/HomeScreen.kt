package com.example.daat.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.util.Log
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
    val permissionsState = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    // Compass/Heading tracking
    var currentHeading by remember { mutableDoubleStateOf(0.0) }
    var currentUserLocation by remember { mutableStateOf<android.location.Location?>(null) }
    
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        
        val listener = object : SensorEventListener {
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
        
        sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        
        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    // Periodically update current location for the compass
    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted) {
            while (true) {
                try {
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { location ->
                            currentUserLocation = location
                        }
                } catch (e: SecurityException) {
                    Log.e("HomeScreen", "Location permission missing", e)
                }
                kotlinx.coroutines.delay(5000) // Update every 5 seconds
            }
        }
    }

    val imageCapture = remember { ImageCapture.Builder().build() }

    LaunchedEffect(uiState.verificationStatus) {
        when (uiState.verificationStatus) {
            VerificationStatus.SUCCESS -> {
                snackbarHostState.showSnackbar("Target Eliminated! +${uiState.lastPointsAwarded} pts")
            }
            VerificationStatus.FAILED_LOCATION -> {
                snackbarHostState.showSnackbar("Too far away from target!")
            }
            VerificationStatus.FAILED_TIME -> {
                snackbarHostState.showSnackbar("Photo is too old!")
            }
            VerificationStatus.FAILED_ORIENTATION -> {
                snackbarHostState.showSnackbar("You're not facing the target!")
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (permissionsState.allPermissionsGranted) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    imageCapture = imageCapture
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "DAILY TARGET",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                            Text(
                                text = uiState.currentTarget?.name ?: "No Target Assigned",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    val targetBearing = remember(uiState.currentTarget, currentUserLocation) {
                        val target = uiState.currentTarget
                        val hunter = currentUserLocation
                        if (target != null && hunter != null && target.latitude != null && target.longitude != null) {
                            VerificationUtils.calculateBearing(
                                hunter.latitude, hunter.longitude,
                                target.latitude, target.longitude
                            )
                        } else 0.0
                    }
                    
                    TacticalCompass(
                        currentHeading = currentHeading,
                        targetBearing = targetBearing,
                        modifier = Modifier.size(200.dp)
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = {
                            captureSnipe(
                                context = context,
                                fusedLocationClient = fusedLocationClient,
                                imageCapture = imageCapture,
                                viewModel = viewModel,
                                currentHeading = currentHeading
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
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
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Camera and Location permissions are required.")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                    Text("Grant Permissions")
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun captureSnipe(
    context: Context,
    fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient,
    imageCapture: ImageCapture,
    viewModel: GameViewModel,
    currentHeading: Double
) {
    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
        .addOnSuccessListener { location ->
            if (location != null) {
                takePhoto(
                    context = context,
                    imageCapture = imageCapture,
                    executor = ContextCompat.getMainExecutor(context),
                    onImageCaptured = { uri ->
                        viewModel.onCaptureButtonPressed(
                            imageUrl = uri.toString(),
                            hunterLat = location.latitude,
                            hunterLon = location.longitude,
                            hunterHeading = currentHeading,
                            capturedAt = System.currentTimeMillis()
                        )
                    },
                    onError = { Log.e("Camera", "Capture failed", it) }
                )
            } else {
                Log.e("HomeScreen", "Failed to get current location for snipe")
            }
        }
}

@Composable
fun TacticalCompass(
    currentHeading: Double,
    targetBearing: Double,
    modifier: Modifier = Modifier
) {
    val animatedHeading by animateFloatAsState(targetValue = currentHeading.toFloat())
    
    val diff = abs(currentHeading - targetBearing)
    val wrappedDiff = if (diff > 180) 360 - diff else diff
    val isAligned = wrappedDiff <= 30.0

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            rotate(degrees = -animatedHeading) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.3f),
                    radius = size.minDimension / 2,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
                
                drawRect(
                    color = Color.Red,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width / 2 - 2.dp.toPx(), 0f),
                    size = androidx.compose.ui.geometry.Size(4.dp.toPx(), 15.dp.toPx())
                )
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            rotate(degrees = (targetBearing - currentHeading).toFloat()) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width / 2, 20.dp.toPx())
                    lineTo(size.width / 2 - 10.dp.toPx(), 40.dp.toPx())
                    lineTo(size.width / 2 + 10.dp.toPx(), 40.dp.toPx())
                    close()
                }
                drawPath(
                    path = path,
                    color = if (isAligned) Color.Green else Color.Red
                )
            }
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    if (isAligned) Color.Green.copy(alpha = 0.2f) else Color.Transparent,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${currentHeading.toInt()}°",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    imageCapture: ImageCapture
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Use case binding failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = modifier
    )
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    executor: Executor,
    onImageCaptured: (Uri) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {
    val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
        .format(System.currentTimeMillis())
    val file = File(context.cacheDir, "$name.jpg")

    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exception: ImageCaptureException) {
                onError(exception)
            }

            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onImageCaptured(Uri.fromFile(file))
            }
        }
    )
}
