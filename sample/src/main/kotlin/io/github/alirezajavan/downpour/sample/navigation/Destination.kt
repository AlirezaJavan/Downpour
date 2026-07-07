package io.github.alirezajavan.downpour.sample.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Troubleshoot
import androidx.compose.ui.graphics.vector.ImageVector

/** One entry per bottom-navigation tab, each showcasing a distinct slice of the library's API surface. */
sealed class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    data object Downloads : Destination("downloads", "Downloads", Icons.Filled.CloudDownload)

    data object Groups : Destination("groups", "Groups", Icons.Filled.Groups)

    data object Diagnostics : Destination("diagnostics", "Diagnostics", Icons.Filled.Troubleshoot)

    data object Settings : Destination("settings", "Settings", Icons.Filled.Settings)

    companion object {
        val all = listOf(Downloads, Groups, Diagnostics, Settings)
    }
}
