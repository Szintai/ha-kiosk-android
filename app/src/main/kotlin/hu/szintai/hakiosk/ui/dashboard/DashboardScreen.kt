package hu.szintai.hakiosk.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import hu.szintai.hakiosk.config.AppConfig

@Composable
fun DashboardScreen(
    onOpenSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val config by viewModel.config.collectAsState()

    Box(Modifier.fillMaxSize()) {
        when (val c = config) {
            null -> Unit
            else -> if (c.isConfigured) {
                // Recreate the WebView whenever the connection settings change.
                androidx.compose.runtime.key(c.dashboardUrl, c.haToken) {
                    HaWebView(config = c, modifier = Modifier.fillMaxSize())
                }
            } else {
                NotConfigured(onOpenSettings)
            }
        }

        // Small, unobtrusive entry point to the settings screen.
        TextButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp),
        ) { Text("⚙") }
    }
}

@Composable
private fun NotConfigured(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Home Assistant nincs beállítva (URL + token szükséges).")
        Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 16.dp)) {
            Text("Beállítások")
        }
    }
}

@Suppress("unused")
private val previewConfig = AppConfig()
