package com.example.daat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.daat.data.model.Snipe
import com.example.daat.data.model.SnipeStatus
import com.example.daat.ui.viewmodel.GameViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(viewModel: GameViewModel) {
    val snipes by viewModel.snipeFeed.collectAsState(initial = emptyList())
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("LIVE FEED", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        if (snipes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("No snipes yet. Go get 'em!")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(snipes) { snipe ->
                    val group = uiState.userGroups.find { it.id == snipe.groupId }
                    val isAdmin = group?.adminId == uiState.currentUser?.id
                    val isTarget = snipe.targetId == uiState.currentUser?.id
                    
                    SnipeCard(
                        snipe = snipe,
                        viewModel = viewModel,
                        isTarget = isTarget,
                        isAdmin = isAdmin
                    )
                }
            }
        }
    }
}

@Composable
fun SnipeCard(
    snipe: Snipe, 
    viewModel: GameViewModel, 
    isTarget: Boolean,
    isAdmin: Boolean
) {
    val hunter by viewModel.getUserById(snipe.hunterId).collectAsState(initial = null)
    val target by viewModel.getUserById(snipe.targetId).collectAsState(initial = null)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "${hunter?.name ?: "Unknown"} sniped ${target?.name ?: "Unknown"}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(snipe.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                if (snipe.targetConfirmed) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = "Confirmed by Target",
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            AsyncImage(
                model = snipe.imageUrl,
                contentDescription = "Snipe photo",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.LightGray),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "+${snipe.pointsAwarded} pts",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                
                // Like Button
                IconButton(onClick = { viewModel.onLikeClicked(snipe.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (snipe.isLikedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (snipe.isLikedByMe) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (snipe.likes > 0) {
                            Text(
                                text = " ${snipe.likes}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                val statusColor = when (snipe.status) {
                    SnipeStatus.VERIFIED -> Color(0xFFE8F5E9)
                    SnipeStatus.REJECTED -> Color(0xFFFFEBEE)
                    SnipeStatus.CHALLENGED -> Color(0xFFFFF3E0)
                    else -> Color(0xFFF5F5F5)
                }
                val contentColor = when (snipe.status) {
                    SnipeStatus.VERIFIED -> Color(0xFF2E7D32)
                    SnipeStatus.REJECTED -> Color(0xFFC62828)
                    SnipeStatus.CHALLENGED -> Color(0xFFEF6C00)
                    else -> Color.Gray
                }

                Surface(
                    color = statusColor,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = snipe.status.name,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor
                    )
                }
            }

            // Target/Admin Controls
            if (isTarget && snipe.status == SnipeStatus.PENDING) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.onConfirmSnipe(snipe.id) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Verify It's Me", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { viewModel.onChallengeSnipe(snipe.id) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF6C00))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Challenge", fontSize = 12.sp)
                    }
                }
            }

            if (isAdmin && (snipe.status == SnipeStatus.CHALLENGED || snipe.status == SnipeStatus.PENDING)) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text("ADMIN MODERATION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { viewModel.onModerateSnipe(snipe.id, true) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Gavel, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Approve", fontSize = 12.sp)
                    }
                    FilledTonalButton(
                        onClick = { viewModel.onModerateSnipe(snipe.id, false) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text("Reject", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
