package com.example.daat

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
<<<<<<< HEAD
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
=======
import androidx.compose.foundation.layout.*
>>>>>>> 1e1e69af1b199ca3f1eee03c2522ddf5b15b689a
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

    // ── Location setup ──────────────────────────────────────────
    private lateinit var locationManager: LocationManager

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            fetchLocation()
        } else {
            println("Location permission denied")
        }
    }

    private fun fetchLocation() {
        locationManager.getAndSaveLocation(
            onSuccess = { lat, lng ->
                println("Location saved to Firebase: $lat, $lng")
            },
            onFailure = { error ->
                println("Error: $error")
            }
        )
    }

    fun startLocationFlow() {
        locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    // ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
<<<<<<< HEAD

        // Initialize location manager
        locationManager = LocationManager(this)

        enableEdgeToEdge()
        setContent {
            DAATTheme {
                DAATApp(
                    onRequestLocation = { startLocationFlow() } // pass it down to UI
                )
=======
        
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
>>>>>>> 1e1e69af1b199ca3f1eee03c2522ddf5b15b689a
            }
        }
    }
}

@Composable
<<<<<<< HEAD
fun DAATApp(onRequestLocation: () -> Unit = {}) {
=======
fun DAATApp(viewModel: GameViewModel) {
>>>>>>> 1e1e69af1b199ca3f1eee03c2522ddf5b15b689a
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = { Icon(it.icon, contentDescription = it.label) },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
<<<<<<< HEAD
            Greeting(
                name = "Android",
                modifier = Modifier.padding(innerPadding),
                onGetLocation = onRequestLocation // wire up the button
            )
=======
            Box(modifier = Modifier.padding(innerPadding)) {
                when (currentDestination) {
                    AppDestinations.HOME -> HomeScreen(viewModel)
                    AppDestinations.FEED -> FeedScreen(viewModel)
                    AppDestinations.LEADERBOARD -> LeaderboardScreen(viewModel)
                    AppDestinations.PROFILE -> ProfileScreen(viewModel)
                }
            }
>>>>>>> 1e1e69af1b199ca3f1eee03c2522ddf5b15b689a
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
<<<<<<< HEAD

@Composable
fun Greeting(
    name: String,
    modifier: Modifier = Modifier,
    onGetLocation: () -> Unit = {}
) {
    androidx.compose.foundation.layout.Column(modifier = modifier) {
        Text(text = "Hello $name!")

        // Button to trigger location
        androidx.compose.material3.Button(onClick = onGetLocation) {
            Text("Get My Location")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DAATTheme {
        Greeting("Android")
    }
}
=======
>>>>>>> 1e1e69af1b199ca3f1eee03c2522ddf5b15b689a
