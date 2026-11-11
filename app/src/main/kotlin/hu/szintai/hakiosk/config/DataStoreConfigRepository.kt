package hu.szintai.hakiosk.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreConfigRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ConfigRepository {

    override val config: Flow<AppConfig> = dataStore.data.map { prefs ->
        val defaults = AppConfig()
        AppConfig(
            haUrl = prefs[HA_URL] ?: defaults.haUrl,
            haToken = prefs[HA_TOKEN] ?: defaults.haToken,
            dashboardPath = prefs[DASHBOARD_PATH] ?: defaults.dashboardPath,
        )
    }

    override suspend fun update(transform: (AppConfig) -> AppConfig) {
        dataStore.edit { prefs ->
            val current = AppConfig(
                haUrl = prefs[HA_URL] ?: AppConfig.DEFAULT_HA_URL,
                haToken = prefs[HA_TOKEN] ?: "",
                dashboardPath = prefs[DASHBOARD_PATH] ?: AppConfig.DEFAULT_DASHBOARD_PATH,
            )
            val next = transform(current)
            prefs[HA_URL] = next.haUrl
            prefs[HA_TOKEN] = next.haToken
            prefs[DASHBOARD_PATH] = next.dashboardPath
        }
    }

    private companion object {
        val HA_URL = stringPreferencesKey("ha_url")
        val HA_TOKEN = stringPreferencesKey("ha_token")
        val DASHBOARD_PATH = stringPreferencesKey("dashboard_path")
    }
}
