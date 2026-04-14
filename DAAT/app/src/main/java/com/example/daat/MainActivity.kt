package com.example.daat

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import com.example.daat.ui.theme.DAATTheme

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

        // Initialize location manager
        locationManager = LocationManager(this)

        enableEdgeToEdge()
        setContent {
            DAATTheme {
                DAATApp(
                    onRequestLocation = { startLocationFlow() } // pass it down to UI
                )
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun DAATApp(onRequestLocation: () -> Unit = {}) {
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
            Greeting(
                name = "Android",
                modifier = Modifier.padding(innerPadding),
                onGetLocation = onRequestLocation // wire up the button
            )
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Default.Home),
    FAVORITES("Favorites", Icons.Default.Favorite),
    PROFILE("Profile", Icons.Default.AccountBox),
}

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