package com.example.daat

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.daat.data.repository.FirebaseGameRepository
import com.example.daat.data.repository.fake.FakeGameRepository
import com.example.daat.ui.screens.FeedScreen
import com.example.daat.ui.screens.HomeScreen
import com.example.daat.ui.screens.LeaderboardScreen
import com.example.daat.ui.screens.ProfileScreen
import com.example.daat.ui.screens.SignInScreen
import com.example.daat.ui.theme.DAATTheme
import com.example.daat.ui.viewmodel.GameViewModel
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        setContent {
            DAATTheme {
                // For development, we keep the toggle. 
                // In production, useFirebase would be true by default.
                var useFirebase by rememberSaveable { mutableStateOf(false) }
                val auth = remember { FirebaseAuth.getInstance() }
                
                val repository = remember(useFirebase) {
                    if (useFirebase) FirebaseGameRepository() else FakeGameRepository()
                }
                val viewModel = remember(repository) { GameViewModel(repository) }
                val uiState by viewModel.uiState.collectAsState()

                if (uiState.currentUser == null) {
                    // Show Login Screen if no user is authenticated
                    SignInScreen(viewModel)
                } else {
                    // Show Main App if logged in
                    Scaffold(
                        bottomBar = {
                            // Debug Toggle for Testing (Optional, can be removed later)
                            Surface(
                                tonalElevation = 8.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Data Source: ${if (useFirebase) "FIREBASE" else "DUMMY"}")
                                    Switch(
                                        checked = useFirebase,
                                        onCheckedChange = { useFirebase = it }
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            DAATApp(viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DAATApp(viewModel: GameViewModel) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            it.icon,
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (currentDestination) {
                    AppDestinations.HOME -> HomeScreen(viewModel)
                    AppDestinations.FEED -> FeedScreen(viewModel)
                    AppDestinations.LEADERBOARD -> LeaderboardScreen(viewModel)
                    AppDestinations.PROFILE -> ProfileScreen(viewModel)
                }
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Default.Home),
    FEED("Feed", Icons.Default.RssFeed),
    LEADERBOARD("Ranking", Icons.Default.List),
    PROFILE("Profile", Icons.Default.AccountCircle),
}
