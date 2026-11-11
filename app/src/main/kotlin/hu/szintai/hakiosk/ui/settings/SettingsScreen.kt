package hu.szintai.hakiosk.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import hu.szintai.hakiosk.config.AppConfig

@Composable
fun SettingsScreen(
    onDone: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    var haUrl by remember { mutableStateOf("") }
    var haToken by remember { mutableStateOf("") }
    var dashboardPath by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val loaded = viewModel.load()
        haUrl = loaded.haUrl
        haToken = loaded.haToken
        dashboardPath = loaded.dashboardPath
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Beállítások")
            OutlinedTextField(
                value = haUrl,
                onValueChange = { haUrl = it },
                label = { Text("Home Assistant URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = haToken,
                onValueChange = { haToken = it },
                label = { Text("Long-lived access token") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = dashboardPath,
                onValueChange = { dashboardPath = it },
                label = { Text("Dashboard útvonal (pl. dashboard-kiosk/home)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    viewModel.save(
                        AppConfig(
                            haUrl = haUrl.trim(),
                            haToken = haToken.trim(),
                            dashboardPath = dashboardPath.trim(),
                        ),
                        onSaved = onDone,
                    )
                }) { Text("Mentés") }
                OutlinedButton(onClick = onDone) { Text("Mégse") }
            }
        }
    }
}
