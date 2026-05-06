package com.example.daat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.daat.data.model.ChallengeStatus
import com.example.daat.data.model.Snipe
import com.example.daat.data.model.SnipeChallenge
import com.example.daat.data.model.SnipeStatus
import com.example.daat.ui.viewmodel.GameViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(viewModel: GameViewModel) {
    val snipes by viewModel.snipeFeed.collectAsState(initial = emptyList())
    val uiState by viewModel.uiState.collectAsState()

    val currentUser = uiState.currentUser
    val pendingChallenges = uiState.pendingChallenges
    val selectedChallenge = uiState.selectedChallenge

    // Is this user an admin of any group?
    val isAdminOfAnyGroup = uiState.userGroups.any { it.adminId == currentUser?.id }

    var showNotifications by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("LIVE FEED", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    // Notification bell — visible to everyone
                    BadgedBox(
                        badge = {
                            if (pendingChallenges.isNotEmpty()) {
                                Badge { Text("${pendingChallenges.size}") }
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        IconButton(onClick = { showNotifications = true }) {
                            Icon(
                                imageVector = if (pendingChallenges.isNotEmpty())
                                    Icons.Default.Notifications
                                else
                                    Icons.Default.NotificationsNone,
                                contentDescription = "Notifications"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(snipes, key = { it.id }) { snipe ->
                SnipeCard(
                    snipe = snipe,
                    currentUserId = currentUser?.id ?: "",
                    viewModel = viewModel
                )
            }
        }
    }

    // ── Notification sheet ────────────────────────────────────────────────────

    if (showNotifications) {
        ModalBottomSheet(onDismissRequest = { showNotifications = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "NOTIFICATIONS",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider()

                if (!isAdminOfAnyGroup || pendingChallenges.isEmpty()) {
                    // Non-admin or no pending challenges
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "You have no notifications.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                } else {
                    // Admin sees pending challenges
                    Text(
                        text = "Pending Challenges (${pendingChallenges.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    pendingChallenges.forEach { challenge ->
                        ChallengeNotificationCard(
                            challenge = challenge,
                            onClick = {
                                viewModel.onSelectChallenge(challenge)
                                showNotifications = false
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // ── Admin challenge decision popup ────────────────────────────────────────

    selectedChallenge?.let { challenge ->
        ChallengeDecisionDialog(
            challenge = challenge,
            viewModel = viewModel,
            onDismiss = { viewModel.onSelectChallenge(null) }
        )
    }
}

// ── Snipe card ────────────────────────────────────────────────────────────────

@Composable
fun SnipeCard(snipe: Snipe, currentUserId: String, viewModel: GameViewModel) {
    val hunter by viewModel.getUserById(snipe.hunterId).collectAsState(initial = null)
    val target by viewModel.getUserById(snipe.targetId).collectAsState(initial = null)

    // The challenge button is only shown to the person who was sniped,
    // only if the snipe is VERIFIED, and only if not already challenged
    val isTarget = snipe.targetId == currentUserId
    val canChallenge = isTarget &&
            snipe.status == SnipeStatus.VERIFIED &&
            !snipe.hasBeenChallenged &&
            snipe.groupId.isNotEmpty()

    var showChallengeConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (snipe.status) {
                SnipeStatus.OVERTURNED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = hunter?.name?.take(1) ?: "?",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "${hunter?.name ?: "Unknown"} sniped ${target?.name ?: "Unknown"}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = try {
                            SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(snipe.timestamp))
                        } catch (e: Exception) { "Unknown date" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Snipe photo
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

            // Footer row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Points
                Text(
                    text = if (snipe.status == SnipeStatus.OVERTURNED)
                        "OVERTURNED"
                    else
                        "+${snipe.pointsAwarded} pts",
                    color = if (snipe.status == SnipeStatus.OVERTURNED)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.weight(1f))

                // Challenge button — only the targeted person sees this
                if (canChallenge) {
                    OutlinedButton(
                        onClick = { showChallengeConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error)
                        ),
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(
                            Icons.Default.Flag,
                            contentDescription = "Challenge",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Challenge", fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                } else if (isTarget && snipe.hasBeenChallenged) {
                    // Already challenged — show muted indicator
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Challenged",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Like button — one like per user, toggles off
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

                // Status chip
                val statusName = snipe.status.name
                Surface(
                    color = when (snipe.status) {
                        SnipeStatus.VERIFIED   -> Color(0xFFE8F5E9)
                        SnipeStatus.OVERTURNED -> MaterialTheme.colorScheme.errorContainer
                        else                   -> Color(0xFFF5F5F5)
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = statusName,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = when (snipe.status) {
                            SnipeStatus.VERIFIED   -> Color(0xFF2E7D32)
                            SnipeStatus.OVERTURNED -> MaterialTheme.colorScheme.error
                            else                   -> Color.Gray
                        }
                    )
                }
            }
        }
    }

    // Challenge confirmation dialog
    if (showChallengeConfirm) {
        AlertDialog(
            onDismissRequest = { showChallengeConfirm = false },
            title = { Text("Challenge This Snipe?") },
            text = {
                Text(
                    "This will flag the snipe to your group admin for review. " +
                            "If overturned, ${hunter?.name ?: "the hunter"}'s points will be reversed. " +
                            "You can only challenge this snipe once."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.onSubmitChallenge(snipe)
                        showChallengeConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Challenge") }
            },
            dismissButton = {
                TextButton(onClick = { showChallengeConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

// ── Challenge notification card ───────────────────────────────────────────────

@Composable
fun ChallengeNotificationCard(challenge: SnipeChallenge, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Flag,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${challenge.challengerName} challenged a snipe",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    text = "Hunter: ${challenge.hunterName.ifEmpty { challenge.hunterId.take(8) }}  ·  ${challenge.pointsAtStake} pts at stake",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                        .format(Date(challenge.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Text(
                text = "Review →",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── Admin challenge decision dialog ───────────────────────────────────────────

@Composable
fun ChallengeDecisionDialog(
    challenge: SnipeChallenge,
    viewModel: GameViewModel,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text("Snipe Challenge", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Snipe photo
                AsyncImage(
                    model = challenge.imageUrl,
                    contentDescription = "Disputed snipe photo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.LightGray),
                    contentScale = ContentScale.Crop
                )

                HorizontalDivider()

                // Details
                DetailRow("Challenged by", challenge.challengerName.ifEmpty { challenge.challengerId.take(8) })
                DetailRow("Hunter", challenge.hunterName.ifEmpty { challenge.hunterId.take(8) })
                DetailRow("Points at stake", "${challenge.pointsAtStake} pts")
                DetailRow(
                    "Submitted",
                    SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(challenge.timestamp))
                )

                HorizontalDivider()

                Text(
                    text = "Uphold: snipe stands, points kept.\nOverturn: snipe voided, ${challenge.pointsAtStake} pts deducted from hunter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        },
        confirmButton = {
            // Uphold — green
            Button(
                onClick = { viewModel.onUpholdChallenge(challenge) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C))
            ) { Text("Uphold Snipe") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("Later") }
                // Overturn — red
                Button(
                    onClick = { viewModel.onOverturnChallenge(challenge) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Overturn") }
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary)
        Text(value, style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold)
    }
}