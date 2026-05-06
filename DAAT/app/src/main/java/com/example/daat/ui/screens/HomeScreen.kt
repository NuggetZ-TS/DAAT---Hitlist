package com.example.daat.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.example.daat.LocationManager
import com.example.daat.data.model.Group
import com.example.daat.data.model.User
import com.example.daat.logic.VerificationUtils
import com.example.daat.ui.viewmodel.GameViewModel
import com.example.daat.ui.viewmodel.VerificationStatus
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(viewModel: GameViewModel, locationManager: LocationManager) {
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

    // ── Heading — poll the shared sensor value at 100 ms ─────────────────────
    // The sensor runs at SENSOR_DELAY_UI (~60 ms); 100 ms polling is plenty.
    var displayHeading by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(Unit) {
        while (true) {
            displayHeading = locationManager.currentHeading
            kotlinx.coroutines.delay(100L)
        }
    }

    // ── Location — collect the continuous GPS flow from LocationManager ───────
    // locationFlow emits every ~1 s from requestLocationUpdates.
    // No separate fusedLocationClient or polling loop needed here.
    val hunterLocation by locationManager.locationFlow.collectAsState()

    val imageCapture = remember { ImageCapture.Builder().build() }

    // ── Determine targets ─────────────────────────────────────────────────────
    val selectedGroup  = uiState.selectedGroup
    val assignment     = uiState.activeAssignment
    val activeTarget   = uiState.activeTarget
    val activeTargetRaw = uiState.activeTargetRaw
    val legacyTarget   = uiState.currentTarget
    val activeGroups   = uiState.userGroups.filter { it.gameActive && it.id != "global" }

    val coordTarget: User? = when {
        selectedGroup != null -> activeTargetRaw
        activeGroups.isEmpty() -> legacyTarget
        else -> null
    }

    // ── Derived values (recomputed whenever location or target changes) ────────
    val distanceToTarget: Double? = remember(coordTarget, hunterLocation) {
        val t = coordTarget ?: return@remember null
        val h = hunterLocation ?: return@remember null
        if (t.latitude == null || t.longitude == null) return@remember null
        VerificationUtils.calculateDistance(h.latitude, h.longitude, t.latitude, t.longitude)
    }

    val targetBearing: Double = remember(coordTarget, hunterLocation) {
        val t = coordTarget ?: return@remember 0.0
        val h = hunterLocation ?: return@remember 0.0
        if (t.latitude == null || t.longitude == null) return@remember 0.0
        VerificationUtils.calculateBearing(h.latitude, h.longitude, t.latitude, t.longitude)
    }

    val isAligned = remember(displayHeading, targetBearing, coordTarget) {
        if (coordTarget == null) false
        else VerificationUtils.isPointingAtTarget(displayHeading, targetBearing)
    }

    // ── Snackbar feedback ─────────────────────────────────────────────────────
    LaunchedEffect(uiState.verificationStatus) {
        val msg = when (uiState.verificationStatus) {
            VerificationStatus.SUCCESS ->
                "🎯 Target eliminated! +${uiState.lastPointsAwarded} pts"
            VerificationStatus.FAILED_LOCATION ->
                "❌ Too far away — get closer!"
            VerificationStatus.FAILED_TIME ->
                "❌ Photo too old — retake it!"
            VerificationStatus.FAILED_ORIENTATION ->
                "❌ Not aimed at target — adjust your angle!"
            VerificationStatus.FAILED_NOT_IN_GROUP ->
                "❌ Target is not in the selected group!"
            VerificationStatus.FAILED_ALREADY_ELIMINATED ->
                if (uiState.activeAssignment?.isHunterEliminated == true)
                    "💀 You've been eliminated today — come back tomorrow!"
                else "✅ Already eliminated this target today!"
            else -> null
        }
        if (msg != null) snackbarHostState.showSnackbar(msg)
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        if (permissionsState.allPermissionsGranted) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                CameraPreview(modifier = Modifier.fillMaxSize(), imageCapture = imageCapture)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Group selector chips
                    if (activeGroups.isNotEmpty()) {
                        GroupSelectorRow(
                            groups = activeGroups,
                            selectedGroupId = selectedGroup?.id,
                            onSelectGroup = { viewModel.onSelectGroup(it) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Target card
                    TargetInfoCard(
                        selectedGroup = selectedGroup,
                        activeGroups = activeGroups,
                        assignment = assignment,
                        activeTarget = activeTarget,
                        legacyTarget = legacyTarget,
                        distanceToTarget = distanceToTarget,
                        isAligned = isAligned
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Compass
                    TacticalCompass(
                        currentHeading = displayHeading,
                        targetBearing = targetBearing,
                        isAligned = isAligned,
                        distanceMeters = distanceToTarget,
                        modifier = Modifier.size(220.dp)
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // Capture button
                    val canCapture = when {
                        selectedGroup != null -> selectedGroup.gameActive &&
                                assignment?.isHunterEliminated != true &&
                                assignment?.isTargetEliminated != true
                        else -> legacyTarget != null
                    }

                    CaptureButton(
                        enabled = canCapture && uiState.verificationStatus != VerificationStatus.VERIFYING,
                        isVerifying = uiState.verificationStatus == VerificationStatus.VERIFYING,
                        isAligned = isAligned,
                        distanceMeters = distanceToTarget,
                        onClick = {
                            // Use the most recent location from the continuous flow.
                            // This is typically <1 s old — far fresher than the old
                            // getCurrentLocation() call that could take 2–5 s to resolve.
                            val loc = locationManager.locationFlow.value
                            if (loc != null) {
                                captureSnipe(
                                    context = context,
                                    imageCapture = imageCapture,
                                    viewModel = viewModel,
                                    hunterLat = loc.latitude,
                                    hunterLon = loc.longitude,
                                    currentHeading = locationManager.currentHeading
                                )
                            } else {
                                Log.w("HomeScreen", "No location fix yet — cannot capture snipe")
                            }
                        }
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Camera and Location permissions are required to play.")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                    Text("Grant Permissions")
                }
            }
        }
    }
}

// ── Target info card ──────────────────────────────────────────────────────────

@Composable
fun TargetInfoCard(
    selectedGroup: Group?,
    activeGroups: List<Group>,
    assignment: com.example.daat.data.model.GroupAssignment?,
    activeTarget: User?,
    legacyTarget: User?,
    distanceToTarget: Double?,
    isAligned: Boolean
) {
    Surface(
        color = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 14.dp)
                .widthIn(min = 260.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            when {
                selectedGroup == null && activeGroups.isEmpty() -> {
                    Text("DAILY TARGET", style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.SemiBold)
                    Text(legacyTarget?.name ?: "No Target Assigned",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, color = Color.White)
                    legacyTarget?.username?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                    }
                    distanceToTarget?.let { DistanceBadge(it, isAligned) }
                }
                selectedGroup == null -> {
                    Text("SELECT A GROUP", style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.7f))
                    Text("Tap a group above to see your target",
                        style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                }
                !selectedGroup.gameActive -> {
                    Text(selectedGroup.name.uppercase(), style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.7f))
                    Text("Game not active", style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f))
                }
                assignment?.isHunterEliminated == true -> {
                    Text(selectedGroup.name.uppercase(), style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFFFF6B6B))
                    Text("YOU'VE BEEN ELIMINATED", fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF6B6B), fontSize = 16.sp)
                    Text("Come back tomorrow!", style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f))
                }
                assignment?.isTargetEliminated == true -> {
                    Text(selectedGroup.name.uppercase(), style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF66BB6A))
                    Text("TARGET ELIMINATED ✓", fontWeight = FontWeight.Bold,
                        color = Color(0xFF66BB6A), fontSize = 16.sp)
                    Text("Resets tomorrow.", style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f))
                }
                activeTarget != null -> {
                    Text(selectedGroup.name.uppercase(), style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.7f))
                    Text(activeTarget.name, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, color = Color.White)
                    Text(activeTarget.username, style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f))
                    distanceToTarget?.let { DistanceBadge(it, isAligned) }
                        ?: Text("Locating target…", style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.5f))
                }
                assignment != null -> {
                    Text(selectedGroup.name.uppercase(), style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.7f))
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                }
                else -> {
                    Text(selectedGroup.name.uppercase(), style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.7f))
                    Text("Waiting for targets…", style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f))
                }
            }
        }
    }
}

// ── Distance badge ────────────────────────────────────────────────────────────

@Composable
fun DistanceBadge(distanceMeters: Double, isAligned: Boolean) {
    val inRange = distanceMeters <= VerificationUtils.MAX_DISTANCE_METERS
    val color = when {
        inRange && isAligned -> Color(0xFF66BB6A)
        inRange              -> Color(0xFFFFB300)
        else                 -> Color(0xFFFF6B6B)
    }
    val distText = if (distanceMeters < 1000) "${distanceMeters.toInt()} m"
    else "${"%.1f".format(distanceMeters / 1000)} km"
    val statusText = when {
        inRange && isAligned -> "In range · aimed ✓"
        inRange              -> "In range · aim camera"
        else                 -> "Too far — get closer"
    }
    Spacer(modifier = Modifier.height(4.dp))
    Surface(color = color.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(distText, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = color)
            Text("·", color = color.copy(alpha = 0.5f), fontSize = 14.sp)
            Text(statusText, fontSize = 12.sp, color = color)
        }
    }
}

// ── Group selector row ────────────────────────────────────────────────────────

@Composable
fun GroupSelectorRow(
    groups: List<Group>,
    selectedGroupId: String?,
    onSelectGroup: (Group) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        groups.forEach { group ->
            val isSelected = group.id == selectedGroupId
            FilterChip(
                selected = isSelected,
                onClick = { onSelectGroup(group) },
                label = {
                    Text(group.name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

// ── Tactical compass ──────────────────────────────────────────────────────────

@Composable
fun TacticalCompass(
    currentHeading: Double,
    targetBearing: Double,
    isAligned: Boolean,
    distanceMeters: Double?,
    modifier: Modifier = Modifier
) {
    val animatedHeading by animateFloatAsState(targetValue = currentHeading.toFloat(), label = "heading")

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            rotate(degrees = -animatedHeading) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.25f),
                    radius = size.minDimension / 2f - 4.dp.toPx(),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
                drawRect(
                    color = Color.Red,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width / 2f - 2.dp.toPx(), 8.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(4.dp.toPx(), 20.dp.toPx())
                )
            }
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val arrowColor = when {
                distanceMeters == null                                  -> Color.Gray
                distanceMeters > VerificationUtils.MAX_DISTANCE_METERS -> Color(0xFFFF6B6B)
                isAligned                                               -> Color(0xFF66BB6A)
                else                                                    -> Color(0xFFFFB300)
            }
            rotate(degrees = (targetBearing - currentHeading).toFloat()) {
                val cx = size.width / 2f
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx, 14.dp.toPx())
                    lineTo(cx - 11.dp.toPx(), 44.dp.toPx())
                    lineTo(cx + 11.dp.toPx(), 44.dp.toPx())
                    close()
                }
                drawPath(path = path, color = arrowColor)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${currentHeading.toInt()}°", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (distanceMeters != null) {
                val distText = if (distanceMeters < 1000) "${distanceMeters.toInt()}m"
                else "${"%.1f".format(distanceMeters / 1000)}km"
                Text(
                    text = distText,
                    color = if (distanceMeters <= VerificationUtils.MAX_DISTANCE_METERS) Color(0xFF66BB6A) else Color(0xFFFF6B6B),
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ── Capture button ────────────────────────────────────────────────────────────

@Composable
fun CaptureButton(
    enabled: Boolean,
    isVerifying: Boolean,
    isAligned: Boolean,
    distanceMeters: Double?,
    onClick: () -> Unit
) {
    val inRange = distanceMeters != null && distanceMeters <= VerificationUtils.MAX_DISTANCE_METERS
    val buttonColor = when {
        !enabled             -> MaterialTheme.colorScheme.surfaceVariant
        inRange && isAligned -> Color(0xFF388E3C)
        inRange              -> Color(0xFFF57C00)
        else                 -> MaterialTheme.colorScheme.primary
    }
    Button(
        onClick = onClick,
        enabled = enabled && !isVerifying,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonColor,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        if (isVerifying) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
        } else {
            Icon(Icons.Default.CameraAlt, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when {
                    inRange && isAligned -> "FIRE!"
                    inRange             -> "AIM AT TARGET"
                    else                -> "CAPTURE SNIPE"
                },
                fontSize = 18.sp, fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── Snipe capture ─────────────────────────────────────────────────────────────

/**
 * Takes a photo and immediately passes the already-known [hunterLat]/[hunterLon]
 * (from the continuous location flow) to the ViewModel.
 * No extra GPS call needed — the flow value is typically <1 s old.
 */
private fun captureSnipe(
    context: Context,
    imageCapture: ImageCapture,
    viewModel: GameViewModel,
    hunterLat: Double,
    hunterLon: Double,
    currentHeading: Double
) {
    takePhoto(
        context = context,
        imageCapture = imageCapture,
        executor = ContextCompat.getMainExecutor(context),
        onImageCaptured = { uri ->
            viewModel.onCaptureButtonPressed(
                imageUrl = uri.toString(),
                hunterLat = hunterLat,
                hunterLon = hunterLon,
                hunterHeading = currentHeading,
                capturedAt = System.currentTimeMillis()
            )
        },
        onError = { Log.e("HomeScreen", "Photo capture failed", it) }
    )
}

// ── Camera ────────────────────────────────────────────────────────────────────

@Composable
fun CameraPreview(modifier: Modifier = Modifier, imageCapture: ImageCapture) {
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
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                    )
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Binding failed", e)
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
    val file = File(
        context.cacheDir,
        "${SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())}.jpg"
    )
    imageCapture.takePicture(
        ImageCapture.OutputFileOptions.Builder(file).build(),
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exception: ImageCaptureException) = onError(exception)
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) =
                onImageCaptured(Uri.fromFile(file))
        }
    )
}