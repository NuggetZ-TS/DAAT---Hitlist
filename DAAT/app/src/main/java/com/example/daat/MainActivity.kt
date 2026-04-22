package com.example.daat

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.daat.data.repository.FirebaseGameRepository
import com.example.daat.ui.screens.FeedScreen
import com.example.daat.ui.screens.HomeScreen
import com.example.daat.ui.screens.LeaderboardScreen
import com.example.daat.ui.screens.ProfileScreen
import com.example.daat.ui.screens.SignInScreen
import com.example.daat.ui.theme.DAATTheme
import com.example.daat.ui.viewmodel.GameViewModel

class MainActivity : ComponentActivity() {

    private lateinit var locationManager: LocationManager

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
                || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) fetchLocation()
        else Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
    }

    private fun fetchLocation() {
        locationManager.getAndSaveLocation(
            onSuccess = { lat, lng, heading ->
                println("✅ Location saved — lat: $lat, lng: $lng, heading: $heading°")
            },
            onFailure = { error ->
                Toast.makeText(this, "Location error: $error", Toast.LENGTH_LONG).show()
            }
        )
    }

    fun startLocationFlow() {
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationManager = LocationManager(this)

        enableEdgeToEdge()
        setContent {
            DAATTheme {
                val repository = remember { FirebaseGameRepository() }
                val viewModel = remember { GameViewModel(repository) }
                val uiState by viewModel.uiState.collectAsState()

                // Guest mode — local flag only, no Firebase involved
                var guestMode by rememberSaveable { mutableStateOf(false) }
                val isLoggedIn = uiState.currentUser != null || guestMode

                // ── Location triggers ─────────────────────────────
                // 1. After Google login or registration completes
                LaunchedEffect(viewModel) {
                    viewModel.onLoginSuccessEvent.collect {
                        startLocationFlow()
                    }
                }
                // 2. After a successful snipe
                LaunchedEffect(viewModel) {
                    viewModel.onSnipeSuccessEvent.collect {
                        startLocationFlow()
                    }
                }

                if (!isLoggedIn) {
                    SignInScreen(
                        viewModel = viewModel,
                        onGuestBypass = { guestMode = true }
                    )
                } else {
                    DAATApp(
                        viewModel = viewModel,
                        onRequestLocation = { startLocationFlow() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::locationManager.isInitialized) locationManager.startOrientationUpdates()
    }

    override fun onPause() {
        super.onPause()
        if (::locationManager.isInitialized) locationManager.stopOrientationUpdates()
    }
}

@Composable
fun DAATApp(
    viewModel: GameViewModel,
    onRequestLocation: () -> Unit = {}
) {
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
            Box(modifier = Modifier.padding(innerPadding)) {
                when (currentDestination) {
                    AppDestinations.HOME        -> HomeScreen(viewModel)
                    AppDestinations.FEED        -> FeedScreen(viewModel)
                    AppDestinations.LEADERBOARD -> LeaderboardScreen(viewModel)
                    AppDestinations.PROFILE     -> ProfileScreen(viewModel)
                }
            }
        }
    }
}

enum class AppDestinations(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    FEED("Feed", Icons.Default.RssFeed),
    LEADERBOARD("Ranking", Icons.Default.List),
    PROFILE("Profile", Icons.Default.AccountCircle),
}