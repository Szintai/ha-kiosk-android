package hu.szintai.hakiosk.ui.settings

import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import hu.szintai.hakiosk.config.AppConfig
import hu.szintai.hakiosk.config.VoiceMode
import hu.szintai.hakiosk.ui.theme.Palette

@Composable
fun SettingsScreen(
    onDone: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    var draft by remember { mutableStateOf<AppConfig?>(null) }
    LaunchedEffect(Unit) { draft = viewModel.load() }
    val c = draft ?: return Box(Modifier.fillMaxSize().background(Palette.Bg))
    fun edit(transform: (AppConfig) -> AppConfig) { draft = transform(c) }

    Box(Modifier.fillMaxSize().background(Palette.Bg), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            Header(
                onCancel = onDone,
                onSave = { viewModel.save(c.normalised(), onSaved = onDone) },
            )

            SectionLabel("Home Assistant")
            GroupCard {
                FieldRow("Cím", c.haUrl) { v -> edit { it.copy(haUrl = v) } }
                Divider()
                FieldRow("Long-lived access token", c.haToken, secret = true) { v -> edit { it.copy(haToken = v) } }
                Divider()
                FieldRow("Dashboard útvonal", c.dashboardPath) { v -> edit { it.copy(dashboardPath = v) } }
                Divider()
                SwitchRow(
                    "Ébredések küldése",
                    "A tablet saját eszközként jelenik meg a Home Assistantban (ébredések száma, jelenlét).",
                    c.reportToHa,
                ) { v -> edit { it.copy(reportToHa = v) } }
            }

            SectionLabel("Jelenlét")
            GroupCard {
                SwitchRow(
                    "Kamerás érzékelés",
                    "Elsötétül, ha nincs mozgás, és mozgásra azonnal felébred. A képek nem hagyják el a tabletet.",
                    c.presenceEnabled,
                ) { v -> edit { it.copy(presenceEnabled = v) } }
                if (c.presenceEnabled) {
                    Divider()
                    StepperRow("Sötétítés ennyi után", "mozdulatlanság esetén", c.idleTimeoutSeconds, 10..900, 10, "mp") { v -> edit { it.copy(idleTimeoutSeconds = v) } }
                    Divider()
                    StepperRow("Érzékenység", "a kép ekkora részének változása számít mozgásnak", c.motionMinAreaPercent, 1..30, 1, "%") { v -> edit { it.copy(motionMinAreaPercent = v) } }
                    Divider()
                    StepperRow("Fényerő sötétítve", null, c.dimBrightnessPercent, 1..50, 1, "%") { v -> edit { it.copy(dimBrightnessPercent = v) } }
                }
            }

            SectionLabel("Kamera előnézet")
            GroupCard {
                Column(Modifier.padding(vertical = 14.dp)) {
                    CameraPreview(
                        enabled = c.presenceEnabled || c.cameraShare,
                        frames = viewModel.previewFrames,
                        onActive = viewModel::setPreviewActive,
                    )
                    HintText("Ezt látja az előlapi kamera a kiválasztott elforgatással: a mozgásérzékelés és a Home Assistantban megosztott kép is ez. Csak amíg a Beállítások nyitva vannak, készül kép.")
                    Text("Kép elforgatása", color = Palette.Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Box(Modifier.padding(top = 10.dp))
                    Segmented(
                        options = ROTATIONS.map { "$it°" },
                        selected = ROTATIONS.indexOf(c.cameraRotation).coerceAtLeast(0),
                        onSelect = { i -> edit { it.copy(cameraRotation = ROTATIONS[i]) } },
                    )
                }
            }
            LaunchedEffect(c.cameraRotation) { viewModel.setPreviewRotation(c.cameraRotation) }

            SectionLabel("Hangvezérlés")
            GroupCard {
                Column(Modifier.padding(vertical = 14.dp)) {
                    Segmented(
                        options = VOICE_LABELS,
                        selected = c.voiceMode.ordinal,
                        onSelect = { i -> edit { it.copy(voiceMode = VoiceMode.entries[i]) } },
                    )
                    HintText(voiceHint(c.voiceMode))
                    if (c.voiceMode == VoiceMode.Continuous) {
                        HintText(
                            "A mikrofon folyamatosan figyel, és a hang a Google beszédfelismerőjéhez kerül. " +
                                "Csak az ébresztő szót tartalmazó mondat lesz parancs.",
                            Palette.Orange,
                        )
                    }
                }
                if (c.voiceMode == VoiceMode.Continuous) {
                    Divider()
                    FieldRow("Ébresztő szó (több változat vesszővel)", c.voiceWakePhrase) { v -> edit { it.copy(voiceWakePhrase = v) } }
                }
                if (c.voiceEnabled) {
                    Divider()
                    SwitchRow(
                        "Válasz hangon",
                        "A Home Assistant válaszát kimondja a tablet (Google beszédszintézis).",
                        c.voiceSpeak,
                    ) { v -> edit { it.copy(voiceSpeak = v) } }
                    Divider()
                    FieldRow("Beszédfelismerés és hang nyelve", c.voiceLanguage) { v -> edit { it.copy(voiceLanguage = v) } }
                }
                if (c.voiceMode == VoiceMode.Continuous) {
                    DndAccessRow()
                }
            }
            SectionLabel("Kamerakép megosztása")
            GroupCard {
                SwitchRow(
                    "Kamerakép kérésre",
                    "Az előlapi kamera képe csak akkor látszik a Home Assistantban, ha ott megnyitod. Egyébként semmit nem küld.",
                    c.cameraShare,
                ) { v -> edit { it.copy(cameraShare = v) } }
                if (c.cameraShare) {
                    Divider()
                    HintText(
                        "A kép a tablet házi hálózati címén érhető el, titkos kulccsal védve (port ${c.cameraPort}). " +
                            "A pontos cím a Home Assistant „Kiosk Tablet IP address” entitásának attribútumaiban van.",
                    )
                }
            }
            Box(Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun Header(onCancel: () -> Unit, onSave: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Beállítások", color = Palette.Ink, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Mégse", primary = false, onClick = onCancel)
            PillButton("Mentés", primary = true, onClick = onSave)
        }
    }
}

private val VOICE_LABELS = listOf("Ki", "Ébredéskor", "Mozgásra", "Folyamatos")
private val ROTATIONS = listOf(0, 90, 180, 270)

private fun voiceHint(mode: VoiceMode) = when (mode) {
    VoiceMode.Off -> "Nincs hangvezérlés."
    VoiceMode.OnWake -> "Ha felébredsz előtte a sötétítésből, meghallgat egy parancsot. A mikrofon gomb is használható."
    VoiceMode.OnMotion -> "Valahányszor elhaladsz előtte, meghallgat egy parancsot (15 mp-es várakozással)."
    VoiceMode.Continuous -> "Mindig figyel az ébresztő szóra, így távolabbról is vezérelhető."
}

/** Sensible bounds and trimmed text before saving. */
private fun AppConfig.normalised(): AppConfig {
    val d = AppConfig()
    return copy(
        haUrl = haUrl.trim(),
        haToken = haToken.trim(),
        dashboardPath = dashboardPath.trim(),
        idleTimeoutSeconds = idleTimeoutSeconds.coerceIn(5, 3600),
        motionMinAreaPercent = motionMinAreaPercent.coerceIn(1, 50),
        dimBrightnessPercent = dimBrightnessPercent.coerceIn(1, 100),
        voiceWakePhrase = voiceWakePhrase.trim().ifBlank { d.voiceWakePhrase },
        voiceLanguage = voiceLanguage.trim().ifBlank { d.voiceLanguage },
    )
}

/** Muting the recogniser's beeps needs "Do Not Disturb" access; offer a shortcut to grant it. */
@Composable
private fun DndAccessRow() {
    val context = LocalContext.current
    val manager = context.getSystemService(NotificationManager::class.java)
    var granted by remember { mutableStateOf(manager.isNotificationPolicyAccessGranted) }
    LifecycleResumeEffect(Unit) {
        granted = manager.isNotificationPolicyAccessGranted
        onPauseOrDispose { }
    }
    Divider()
    Column(Modifier.padding(vertical = 14.dp)) {
        Text("Sípolás némítása", color = Palette.Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        HintText(
            if (granted) "Engedélyezve: folyamatos hallgatás közben a felismerő sípolása el van némítva."
            else "A felismerő minden újraindításkor sípol. Ennek elnyomásához add meg a „Ne zavarjanak” hozzáférést az appnak.",
            if (granted) Palette.Green else Palette.Mute,
        )
        if (!granted) {
            PillButton("Hozzáférés megadása", primary = false) {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
        }
    }
}

/** Live preview of the front camera; asks for frames only while this composable is shown. */
@Composable
private fun CameraPreview(enabled: Boolean, frames: StateFlow<ByteArray?>, onActive: (Boolean) -> Unit) {
    DisposableEffect(enabled) {
        onActive(enabled)
        onDispose { onActive(false) }
    }
    val frame by frames.collectAsState()
    val bitmap = remember(frame) { frame?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when {
            !enabled -> Text("A kamera ki van kapcsolva (kapcsold be a kamerás érzékelést vagy a kamerakép megosztását).", color = Palette.Mute, modifier = Modifier.padding(24.dp))
            bitmap == null -> Text("Kép betöltése…", color = Palette.Mute)
            else -> Image(bitmap = bitmap, contentDescription = "Kamera előnézet", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
    }
}
