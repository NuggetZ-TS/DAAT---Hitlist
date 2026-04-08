package com.example.daat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.daat.data.repository.fake.FakeGameRepository
import com.example.daat.ui.screens.FeedScreen
import com.example.daat.ui.screens.HomeScreen
import com.example.daat.ui.screens.LeaderboardScreen
import com.example.daat.ui.screens.ProfileScreen
import com.example.daat.ui.theme.DAATTheme
import com.example.daat.ui.viewmodel.GameViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Manual DI for demo purposes
        val repository = FakeGameRepository()
        val viewModel = GameViewModel(repository)
        
        enableEdgeToEdge()
        setContent {
            DAATTheme {
                DAATApp(viewModel)
            }
        }
    }
}

@Composable
fun DAATApp(viewModel: GameViewModel) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    val uiState by viewModel.uiState.collectAsState()

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
