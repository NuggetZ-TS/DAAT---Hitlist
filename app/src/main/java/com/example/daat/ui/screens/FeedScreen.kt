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
import androidx.compose.material.icons.filled.MyLocation
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
import com.example.daat.ui.viewmodel.GameViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(viewModel: GameViewModel) {
    val snipes by viewModel.snipeFeed.collectAsState(initial = emptyList())

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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(snipes) { snipe ->
                SnipeCard(snipe, viewModel)
            }
        }
    }
}

@Composable
fun SnipeCard(snipe: Snipe, viewModel: GameViewModel) {
    val hunter by viewModel.getUserById(snipe.hunterId).collectAsState(initial = null)
    val target by viewModel.getUserById(snipe.targetId).collectAsState(initial = null)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = "${hunter?.name ?: "Unknown"} sniped ${target?.name ?: "Unknown"}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(text = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(snipe.timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Box {
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
                
                // Distance Badge
                Surface(
                    modifier = Modifier.padding(8.dp).align(Alignment.TopEnd),
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "${String.format("%.1f", snipe.distance)}m", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Special Bonus Badge
                val bonusTag = when {
                    snipe.distance < 3.0 -> "POINT BLANK"
                    snipe.distance < 7.0 -> "CLOSE UP"
                    snipe.distance > 20.0 -> "LONG SHOT"
                    else -> null
                }
                
                if (bonusTag != null) {
                    Surface(
                        modifier = Modifier.padding(8.dp).align(Alignment.BottomStart),
                        color = MaterialTheme.colorScheme.tertiary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = bonusTag,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onTertiary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "+${snipe.pointsAwarded} pts", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.onLikeClicked(snipe.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = if (snipe.isLikedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Like", tint = if (snipe.isLikedByMe) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant)
                        if (snipe.likes > 0) {
                            Text(text = " ${snipe.likes}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
