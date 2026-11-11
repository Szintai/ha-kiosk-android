package hu.szintai.hakiosk.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
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
    var presenceEnabled by remember { mutableStateOf(true) }
    var idleTimeout by remember { mutableStateOf("") }
    var motionArea by remember { mutableStateOf("") }
    var dimBrightness by remember { mutableStateOf("") }
    var reportToHa by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val c = viewModel.load()
        haUrl = c.haUrl
        haToken = c.haToken
        dashboardPath = c.dashboardPath
        presenceEnabled = c.presenceEnabled
        idleTimeout = c.idleTimeoutSeconds.toString()
        motionArea = c.motionMinAreaPercent.toString()
        dimBrightness = c.dimBrightnessPercent.toString()
        reportToHa = c.reportToHa
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Beállítások")
            Field("Home Assistant URL", haUrl) { haUrl = it }
            Field("Long-lived access token", haToken) { haToken = it }
            Field("Dashboard útvonal (pl. dashboard-kiosk2/home)", dashboardPath) { dashboardPath = it }

            SwitchRow("Kamerás jelenlét-érzékelés (sötétítés / ébredés)", presenceEnabled) { presenceEnabled = it }
            Field("Sötétítés ennyi mozdulatlan másodperc után", idleTimeout, number = true) { idleTimeout = it }
            Field("Mozgás érzékenység: változó terület %-a (kisebb = érzékenyebb)", motionArea, number = true) { motionArea = it }
            Field("Fényerő sötétítve (%)", dimBrightness, number = true) { dimBrightness = it }
            SwitchRow("Ébredések küldése a Home Assistantnak", reportToHa) { reportToHa = it }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    val d = AppConfig()
                    viewModel.save(
                        AppConfig(
                            haUrl = haUrl.trim(),
                            haToken = haToken.trim(),
                            dashboardPath = dashboardPath.trim(),
                            presenceEnabled = presenceEnabled,
                            idleTimeoutSeconds = idleTimeout.toIntOrNull()?.coerceIn(5, 3600) ?: d.idleTimeoutSeconds,
                            motionMinAreaPercent = motionArea.toIntOrNull()?.coerceIn(1, 50) ?: d.motionMinAreaPercent,
                            dimBrightnessPercent = dimBrightness.toIntOrNull()?.coerceIn(1, 100) ?: d.dimBrightnessPercent,
                            reportToHa = reportToHa,
                        ),
                        onSaved = onDone,
                    )
                }) { Text("Mentés") }
                OutlinedButton(onClick = onDone) { Text("Mégse") }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, number: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
