package io.github.alirezajavan.downpour.sample

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.alirezajavan.downpour.sample.core.SampleEvents
import io.github.alirezajavan.downpour.sample.diagnostics.DiagnosticsScreen
import io.github.alirezajavan.downpour.sample.downloads.DownloadsScreen
import io.github.alirezajavan.downpour.sample.groups.GroupsScreen
import io.github.alirezajavan.downpour.sample.navigation.Destination
import io.github.alirezajavan.downpour.sample.settings.SettingsScreen
import io.github.alirezajavan.downpour.sample.theme.DownpourSampleTheme

class MainActivity : ComponentActivity() {
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best-effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureNotificationPermission()
        setContent {
            DownpourSampleTheme {
                Surface(modifier = Modifier) {
                    SampleApp()
                }
            }
        }
    }

    // On Android 13+ the download foreground-service notification is hidden unless the user has
    // granted POST_NOTIFICATIONS. Request it so progress/pause/cancel are actually visible.
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun SampleApp() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        SampleEvents.messages.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { SampleBottomBar(navController) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Downloads.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.Downloads.route) { DownloadsScreen() }
            composable(Destination.Groups.route) { GroupsScreen() }
            composable(Destination.Diagnostics.route) { DiagnosticsScreen() }
            composable(Destination.Settings.route) { SettingsScreen() }
        }
    }
}

@Composable
private fun SampleBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar {
        Destination.all.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(destination.label) },
            )
        }
    }
}
