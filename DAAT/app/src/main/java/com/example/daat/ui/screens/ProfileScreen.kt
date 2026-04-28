package com.example.daat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.daat.data.model.Group
import com.example.daat.data.model.User
import com.example.daat.ui.viewmodel.GameViewModel

// ── ProfileScreen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(viewModel: GameViewModel, onSignOut: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()
    val user = uiState.currentUser

    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showJoinGroupDialog by remember { mutableStateOf(false) }
    var groupToLeave by remember { mutableStateOf<Group?>(null) }
    var groupForAdminPanel by remember { mutableStateOf<Group?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("PROFILE", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {
                        viewModel.onSignOut()
                        onSignOut()
                    }) {
                        Icon(
                            Icons.Default.Logout,
                            contentDescription = "Logout",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user?.name?.take(1) ?: "?",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = user?.name ?: "Agent",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = user?.username ?: "@unknown",
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(
                            label = "TOTAL SCORE",
                            value = user?.totalScore?.toString() ?: "0"
                        )
                        Divider(
                            modifier = Modifier
                                .height(40.dp)
                                .width(1.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        StatItem(
                            label = "STREAK",
                            value = "${user?.currentStreak ?: 0} 🔥"
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MY GROUPS",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Row {
                        IconButton(onClick = { showJoinGroupDialog = true }) {
                            Icon(
                                Icons.Default.GroupAdd,
                                contentDescription = "Join Group",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { showCreateGroupDialog = true }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Create Group",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (uiState.userGroups.isEmpty()) {
                item {
                    Text(
                        "You haven't joined any groups yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(vertical = 32.dp)
                    )
                }
            } else {
                items(uiState.userGroups) { group ->
                    val isAdmin = group.adminId == user?.id
                    GroupCard(
                        group = group,
                        isAdmin = isAdmin,
                        onLeaveClick = { groupToLeave = group },
                        onAdminClick = { groupForAdminPanel = group }
                    )
                }
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    if (showCreateGroupDialog) {
        CreateGroupDialog(
            onDismiss = { showCreateGroupDialog = false },
            onCreate = { name ->
                viewModel.onCreateGroup(name)
                showCreateGroupDialog = false
            }
        )
    }

    if (showJoinGroupDialog) {
        JoinGroupDialog(
            onDismiss = { showJoinGroupDialog = false },
            onJoin = { code ->
                viewModel.onJoinGroup(code)
                showJoinGroupDialog = false
            }
        )
    }

    groupToLeave?.let { group ->
        AlertDialog(
            onDismissRequest = { groupToLeave = null },
            title = { Text("Leave Group") },
            text = { Text("Are you sure you want to leave '${group.name}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onLeaveGroup(group.id)
                        groupToLeave = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Leave") }
            },
            dismissButton = {
                TextButton(onClick = { groupToLeave = null }) { Text("Cancel") }
            }
        )
    }

    // ── Admin bottom sheet ────────────────────────────────────────────────────

    groupForAdminPanel?.let { group ->
        AdminGroupSheet(
            group = group,
            currentUserId = user?.id ?: "",
            viewModel = viewModel,
            onDismiss = { groupForAdminPanel = null }
        )
    }
}

// ── Admin Bottom Sheet ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminGroupSheet(
    group: Group,
    currentUserId: String,
    viewModel: GameViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val members = uiState.groupMembers  // loaded by viewModel when sheet opens

    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var memberToKick by remember { mutableStateOf<User?>(null) }
    var memberToTransfer by remember { mutableStateOf<User?>(null) }

    LaunchedEffect(group.id) {
        viewModel.loadGroupMembers(group.id)
    }

    ModalBottomSheet(onDismissRequest = {
        viewModel.clearGroupMembers()
        onDismiss()
    }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.ManageAccounts,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Admin Panel",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            HorizontalDivider()

            // Quick actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { showRenameDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rename")
                }
                Button(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }
            }

            // Members section
            Text(
                text = "MEMBERS (${members.size})",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )

            if (members.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    members.forEach { member ->
                        val isSelf = member.id == currentUserId
                        val isGroupAdmin = member.id == group.adminId
                        MemberRow(
                            member = member,
                            isSelf = isSelf,
                            isAdmin = isGroupAdmin,
                            onKick = { memberToKick = member },
                            onTransferAdmin = { memberToTransfer = member }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // ── Sub-dialogs ───────────────────────────────────────────────────────────

    if (showRenameDialog) {
        RenameGroupDialog(
            currentName = group.name,
            onDismiss = { showRenameDialog = false },
            onRename = { newName ->
                viewModel.onRenameGroup(group.id, newName)
                showRenameDialog = false
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Group") },
            text = {
                Text("This will permanently delete '${group.name}' and remove all ${members.size} members. This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onDeleteGroup(group.id)
                        showDeleteConfirm = false
                        viewModel.clearGroupMembers()
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete Forever") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    memberToKick?.let { target ->
        AlertDialog(
            onDismissRequest = { memberToKick = null },
            title = { Text("Kick Member") },
            text = { Text("Remove ${target.name} (${target.username}) from '${group.name}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onKickMember(group.id, target.id)
                        memberToKick = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Kick") }
            },
            dismissButton = {
                TextButton(onClick = { memberToKick = null }) { Text("Cancel") }
            }
        )
    }

    memberToTransfer?.let { target ->
        AlertDialog(
            onDismissRequest = { memberToTransfer = null },
            title = { Text("Transfer Admin") },
            text = {
                Text("Make ${target.name} (${target.username}) the new admin of '${group.name}'? You will lose admin privileges.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onTransferAdmin(group.id, target.id)
                        memberToTransfer = null
                        viewModel.clearGroupMembers()
                        onDismiss()
                    }
                ) { Text("Transfer") }
            },
            dismissButton = {
                TextButton(onClick = { memberToTransfer = null }) { Text("Cancel") }
            }
        )
    }
}

// ── Member Row ────────────────────────────────────────────────────────────────

@Composable
fun MemberRow(
    member: User,
    isSelf: Boolean,
    isAdmin: Boolean,
    onKick: () -> Unit,
    onTransferAdmin: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isAdmin)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isAdmin) 1f else 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = member.name.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Name + username
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = member.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    if (isAdmin) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Admin",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (isSelf) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "you",
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
                Text(
                    text = member.username,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            // Score badge
            Text(
                text = "${member.totalScore} pts",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            // Admin can't kick themselves or act on themselves
            if (!isSelf && !isAdmin) {
                Spacer(modifier = Modifier.width(4.dp))
                // Transfer admin
                IconButton(onClick = onTransferAdmin, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = "Make Admin",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                // Kick
                IconButton(onClick = onKick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.PersonRemove,
                        contentDescription = "Kick",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ── Group Card ────────────────────────────────────────────────────────────────

@Composable
fun GroupCard(
    group: Group,
    isAdmin: Boolean,
    onLeaveClick: () -> Unit,
    onAdminClick: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = group.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (isAdmin) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Admin",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = "${group.members.size} Member${if (group.members.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Invite code chip
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    onClick = { clipboardManager.setText(AnnotatedString(group.inviteCode)) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = group.inviteCode,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy code",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (group.id != "global") {
                    if (isAdmin) {
                        // Admin panel button
                        IconButton(onClick = onAdminClick) {
                            Icon(
                                Icons.Default.ManageAccounts,
                                contentDescription = "Manage Group",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        // Leave button for non-admins
                        IconButton(onClick = onLeaveClick) {
                            Icon(
                                Icons.Default.RemoveCircleOutline,
                                contentDescription = "Leave Group",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Dialogs ───────────────────────────────────────────────────────────────────

@Composable
fun CreateGroupDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Group") },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Group Name") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onCreate(name) }) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun JoinGroupDialog(onDismiss: () -> Unit, onJoin: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join a Group") },
        text = {
            TextField(
                value = code,
                onValueChange = { code = it.uppercase() },
                label = { Text("Invite Code") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { if (code.isNotBlank()) onJoin(code) }) { Text("Join") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RenameGroupDialog(currentName: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Group") },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("New Name") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank() && name != currentName) onRename(name) }) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}