@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.daat.ui.screens

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.daat.data.model.Group
import com.example.daat.data.model.Message
import com.example.daat.ui.viewmodel.GameViewModel
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

@Composable
fun GroupsScreen(viewModel: GameViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var showScanner by remember { mutableStateOf(false) }
    var groupToInvite by remember { mutableStateOf<Group?>(null) }
    var selectedGroup by remember { mutableStateOf<Group?>(null) }

    if (selectedGroup != null) {
        ChatScreen(
            group = selectedGroup!!,
            viewModel = viewModel,
            onBack = { selectedGroup = null }
        )
    } else {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("GROUPS", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = { showScanner = true }) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan to Join")
                        }
                    }
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.groups) { group ->
                    GroupCard(
                        group = group,
                        onInviteClick = { groupToInvite = group },
                        onJoinClick = { viewModel.onJoinGroup(group.id) },
                        onCardClick = { if (group.isJoined) selectedGroup = group }
                    )
                }
            }

            if (showScanner) {
                QrScannerDialog(
                    onCodeScanned = { code ->
                        if (code.startsWith("DAAT_GROUP_")) {
                            val groupId = code.removePrefix("DAAT_GROUP_")
                            viewModel.onJoinGroup(groupId)
                            showScanner = false
                        }
                    },
                    onDismiss = { showScanner = false }
                )
            }

            if (groupToInvite != null) {
                InviteQrDialog(
                    group = groupToInvite!!,
                    onDismiss = { groupToInvite = null }
                )
            }
        }
    }
}

@Composable
fun ChatScreen(group: Group, viewModel: GameViewModel, onBack: () -> Unit) {
    val messages by viewModel.getMessages(group.id).collectAsState(initial = emptyList())
    val currentUser by viewModel.uiState.collectAsState()
    var messageText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(group.name, style = MaterialTheme.typography.titleMedium)
                        Text("${group.membersCount} members", style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth().imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message...") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                    IconButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                viewModel.sendMessage(group.id, messageText)
                                messageText = ""
                            }
                        },
                        enabled = messageText.isNotBlank()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            reverseLayout = true
        ) {
            items(messages.reversed()) { message ->
                MessageBubble(
                    message = message,
                    isMine = message.senderId == currentUser.currentUser?.id
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message, isMine: Boolean) {
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeString = dateFormat.format(Date(message.timestamp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        if (!isMine) {
            Text(
                text = message.senderName,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
            )
        }
        Surface(
            color = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMine) 16.dp else 0.dp,
                bottomEnd = if (isMine) 0.dp else 16.dp
            )
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = message.content,
                    color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = timeString,
                    style = MaterialTheme.typography.labelSmall,
                    color = (if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
fun GroupCard(group: Group, onInviteClick: () -> Unit, onJoinClick: () -> Unit, onCardClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onCardClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (group.isJoined) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = group.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(text = group.description, style = MaterialTheme.typography.bodySmall)
                Text(text = "${group.membersCount} hunters", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            
            if (group.isJoined) {
                IconButton(onClick = onInviteClick) {
                    Icon(Icons.Default.QrCode, contentDescription = "Show Invite QR", tint = MaterialTheme.colorScheme.primary)
                }
            } else {
                Button(onClick = onJoinClick) {
                    Text("Join")
                }
            }
        }
    }
}

@Composable
fun QrScannerDialog(onCodeScanned: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().height(450.dp).padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Scan Group QR", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(8.dp))) {
                    QrScannerCamera(onCodeScanned)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Point your camera at a friend's invite QR code.", textAlign = TextAlign.Center, fontSize = 14.sp)
                Button(onClick = onDismiss, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
fun QrScannerCamera(onCodeScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val scanner = remember { BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    @androidx.annotation.OptIn(ExperimentalGetImage::class)
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        scanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                for (barcode in barcodes) {
                                    barcode.rawValue?.let { onCodeScanned(it) }
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    } else {
                        imageProxy.close()
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
                } catch (e: Exception) { Log.e("QR", "Binding failed", e) }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun InviteQrDialog(group: Group, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Group Invite", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text(group.name, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(24.dp))
                
                QrCodeImage(content = "DAAT_GROUP_${group.id}", size = 250)
                
                Spacer(modifier = Modifier.height(24.dp))
                Text("Show this code to a friend to let them join this group.", textAlign = TextAlign.Center)
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
fun QrCodeImage(content: String, size: Int) {
    val bitmap = remember(content) {
        try {
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bmp
        } catch (e: Exception) { null }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "QR Code",
            modifier = Modifier.size(size.dp).clip(RoundedCornerShape(8.dp))
        )
    } else {
        Box(modifier = Modifier.size(size.dp).background(Color.LightGray))
    }
}
