package com.example.daat.ui.screens

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.ScaleGestureDetector
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.daat.logic.ScoringManager
import com.example.daat.ui.viewmodel.GameViewModel
import com.example.daat.ui.viewmodel.VerificationStatus
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(viewModel: GameViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val snackbarHostState = remember { SnackbarHostState() }
    var flashEnabled by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        while(true) {
            viewModel.checkSurvivalReward()
            delay(30000)
        }
    }

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
            else -> {}
        }
    }

    LaunchedEffect(uiState.survivalRewardAwarded) {
        uiState.survivalRewardAwarded?.let { points ->
            snackbarHostState.showSnackbar("Survival Bonus! +$points pts for staying hidden.")
            viewModel.clearSurvivalNotification()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (cameraPermissionState.status.isGranted) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    flashEnabled = flashEnabled
                )

                Crosshair(modifier = Modifier.align(Alignment.Center))

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        // Target Info
                        Surface(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("DAILY TARGET", style = MaterialTheme.typography.labelLarge, color = Color.White)
                                Text(
                                    text = uiState.currentTarget?.name ?: "No Target",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        // Survival Multiplier Badge
                        val days = ScoringManager.getDaysSurvived(uiState.currentUser?.lastSnipedAt)
                        Surface(
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("DAY $days", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text("${days}x BONUS", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { flashEnabled = !flashEnabled },
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Flash",
                                tint = if (flashEnabled) Color(0xFFFFA000) else Color.White
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable(enabled = uiState.verificationStatus != VerificationStatus.VERIFYING && uiState.currentTarget != null) {
                                    performRecoilVibration(context)
                                    val targetLat = uiState.currentTarget?.latitude ?: 0.0
                                    val targetLon = uiState.currentTarget?.longitude ?: 0.0
                                    viewModel.onCaptureButtonPressed(
                                        imageUrl = "https://picsum.photos/seed/${System.currentTimeMillis()}/800/600",
                                        hunterLat = targetLat,
                                        hunterLon = targetLon,
                                        capturedAt = System.currentTimeMillis()
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (uiState.verificationStatus == VerificationStatus.VERIFYING) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                            } else {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Capture", modifier = Modifier.size(40.dp), tint = Color.White)
                            }
                        }
                        
                        // Placeholder to balance the row
                        Spacer(modifier = Modifier.size(48.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Camera permission is required.")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                    Text("Grant Permission")
                }
            }
        }
    }
}

@Composable
fun Crosshair(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(150.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 2.dp.toPx()
            val color = Color.White.copy(alpha = 0.7f)
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2
            drawCircle(color = color, center = center, radius = radius, style = Stroke(width = strokeWidth))
            drawCircle(color = color, center = center, radius = radius * 0.1f, style = Stroke(width = strokeWidth))
            val lineLength = radius * 0.8f
            val lineGap = radius * 0.2f
            drawLine(color = color, start = Offset(center.x - lineLength, center.y), end = Offset(center.x - lineGap, center.y), strokeWidth = strokeWidth)
            drawLine(color = color, start = Offset(center.x + lineGap, center.y), end = Offset(center.x + lineLength, center.y), strokeWidth = strokeWidth)
            drawLine(color = color, start = Offset(center.x, center.y - lineLength), end = Offset(center.x, center.y - lineGap), strokeWidth = strokeWidth)
            drawLine(color = color, start = Offset(center.x, center.y + lineGap), end = Offset(center.x, center.y + lineLength), strokeWidth = strokeWidth)
        }
    }
}

@Composable
fun CameraPreview(modifier: Modifier = Modifier, flashEnabled: Boolean = false) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var cameraInstance by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(flashEnabled) { cameraInstance?.cameraControl?.enableTorch(flashEnabled) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            val listener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    cameraInstance?.let { cam ->
                        val zoomState = cam.cameraInfo.zoomState.value
                        val currentZoomRatio = zoomState?.zoomRatio ?: 1f
                        cam.cameraControl.setZoomRatio(currentZoomRatio * detector.scaleFactor)
                    }
                    return true
                }
            }
            val scaleGestureDetector = ScaleGestureDetector(ctx, listener)
            previewView.setOnTouchListener { view, event ->
                scaleGestureDetector.onTouchEvent(event)
                view.performClick()
                true
            }
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                try {
                    cameraProvider.unbindAll()
                    cameraInstance = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                } catch (e: Exception) { Log.e("CameraPreview", "Binding failed", e) }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = modifier
    )
}

fun performRecoilVibration(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 50, 40, 30), intArrayOf(0, 255, 0, 100), -1))
    } else {
        @Suppress("DEPRECATION") vibrator.vibrate(100)
    }
}
