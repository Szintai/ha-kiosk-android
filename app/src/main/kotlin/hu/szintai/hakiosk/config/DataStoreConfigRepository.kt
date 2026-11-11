package hu.szintai.hakiosk.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreConfigRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ConfigRepository {

    override val config: Flow<AppConfig> = dataStore.data.map { it.toConfig() }

    override suspend fun update(transform: (AppConfig) -> AppConfig) {
        dataStore.edit { prefs ->
            val next = transform(prefs.toConfig())
            prefs[HA_URL] = next.haUrl
            prefs[HA_TOKEN] = next.haToken
            prefs[DASHBOARD_PATH] = next.dashboardPath
            prefs[PRESENCE_ENABLED] = next.presenceEnabled
            prefs[IDLE_TIMEOUT_S] = next.idleTimeoutSeconds
            prefs[MOTION_MIN_AREA] = next.motionMinAreaPercent
            prefs[DIM_BRIGHTNESS] = next.dimBrightnessPercent
            prefs[REPORT_TO_HA] = next.reportToHa
            prefs[VOICE_MODE] = next.voiceMode.key
            prefs[VOICE_WAKE_PHRASE] = next.voiceWakePhrase
            prefs[VOICE_SPEAK] = next.voiceSpeak
            prefs[CAMERA_SHARE] = next.cameraShare
            prefs[CAMERA_PORT] = next.cameraPort
            prefs[CAMERA_TOKEN] = next.cameraToken
            prefs[CAMERA_ROTATION] = next.cameraRotation
            prefs[VOICE_LANGUAGE] = next.voiceLanguage
        }
    }

    private fun Preferences.toConfig(): AppConfig {
        val d = AppConfig()
        return AppConfig(
            haUrl = this[HA_URL] ?: d.haUrl,
            haToken = this[HA_TOKEN] ?: d.haToken,
            dashboardPath = this[DASHBOARD_PATH] ?: d.dashboardPath,
            presenceEnabled = this[PRESENCE_ENABLED] ?: d.presenceEnabled,
            idleTimeoutSeconds = this[IDLE_TIMEOUT_S] ?: d.idleTimeoutSeconds,
            motionMinAreaPercent = this[MOTION_MIN_AREA] ?: d.motionMinAreaPercent,
            dimBrightnessPercent = this[DIM_BRIGHTNESS] ?: d.dimBrightnessPercent,
            reportToHa = this[REPORT_TO_HA] ?: d.reportToHa,
            // Older builds stored a plain boolean: "enabled" meant listen-on-wake.
            voiceMode = this[VOICE_MODE]?.let(VoiceMode::fromKey)
                ?: if (this[VOICE_ENABLED] == true) VoiceMode.OnWake else d.voiceMode,
            voiceWakePhrase = this[VOICE_WAKE_PHRASE] ?: d.voiceWakePhrase,
            voiceSpeak = this[VOICE_SPEAK] ?: d.voiceSpeak,
            cameraShare = this[CAMERA_SHARE] ?: d.cameraShare,
            cameraPort = this[CAMERA_PORT] ?: d.cameraPort,
            cameraToken = this[CAMERA_TOKEN] ?: d.cameraToken,
            cameraRotation = this[CAMERA_ROTATION] ?: d.cameraRotation,
            voiceLanguage = this[VOICE_LANGUAGE] ?: d.voiceLanguage,
        )
    }

    private companion object {
        val HA_URL = stringPreferencesKey("ha_url")
        val HA_TOKEN = stringPreferencesKey("ha_token")
        val DASHBOARD_PATH = stringPreferencesKey("dashboard_path")
        val PRESENCE_ENABLED = booleanPreferencesKey("presence_enabled")
        val IDLE_TIMEOUT_S = intPreferencesKey("idle_timeout_s")
        val MOTION_MIN_AREA = intPreferencesKey("motion_min_area_pct")
        val DIM_BRIGHTNESS = intPreferencesKey("dim_brightness_pct")
        val REPORT_TO_HA = booleanPreferencesKey("report_to_ha")
        val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
        val VOICE_MODE = stringPreferencesKey("voice_mode")
        val VOICE_WAKE_PHRASE = stringPreferencesKey("voice_wake_phrase")
        val VOICE_SPEAK = booleanPreferencesKey("voice_speak")
        val CAMERA_SHARE = booleanPreferencesKey("camera_share")
        val CAMERA_PORT = intPreferencesKey("camera_port")
        val CAMERA_TOKEN = stringPreferencesKey("camera_token")
        val CAMERA_ROTATION = intPreferencesKey("camera_rotation")
        val VOICE_LANGUAGE = stringPreferencesKey("voice_language")
    }
}
