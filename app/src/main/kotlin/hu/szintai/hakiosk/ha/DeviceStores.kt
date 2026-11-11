package hu.szintai.hakiosk.ha

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** What we know about this tablet's `mobile_app` registration in Home Assistant. */
data class HaRegistration(
    val deviceId: String,
    val webhookId: String?,
    /** Version of the sensor set registered in HA; bump [MobileAppHaReporter] to register new ones. */
    val sensorsVersion: Int,
)

@Singleton
class HaDeviceStore @Inject constructor(private val dataStore: DataStore<Preferences>) {

    suspend fun read(): HaRegistration {
        var prefs = dataStore.data.first()
        if (prefs[DEVICE_ID] == null) {
            dataStore.edit { it[DEVICE_ID] = UUID.randomUUID().toString() }
            prefs = dataStore.data.first()
        }
        return HaRegistration(
            deviceId = prefs[DEVICE_ID]!!,
            webhookId = prefs[WEBHOOK_ID],
            sensorsVersion = prefs[SENSORS_VERSION] ?: 0,
        )
    }

    suspend fun saveWebhook(webhookId: String) = dataStore.edit {
        it[WEBHOOK_ID] = webhookId
        it[SENSORS_VERSION] = 0
    }

    suspend fun markSensorsRegistered(version: Int) = dataStore.edit { it[SENSORS_VERSION] = version }

    /** HA forgot us (webhook gone): register again on the next report. */
    suspend fun clearRegistration() = dataStore.edit {
        it.remove(WEBHOOK_ID)
        it[SENSORS_VERSION] = 0
    }

    private companion object {
        val DEVICE_ID = stringPreferencesKey("ha_device_id")
        val WEBHOOK_ID = stringPreferencesKey("ha_webhook_id")
        val SENSORS_VERSION = intPreferencesKey("ha_sensors_version")
    }
}

/** Persistent count of motion wake-ups (the `total_increasing` value reported to HA). */
@Singleton
class WakeStatsStore @Inject constructor(private val dataStore: DataStore<Preferences>) {

    suspend fun count(): Long = dataStore.data.first()[WAKE_COUNT] ?: 0L

    suspend fun increment(): Long {
        var next = 0L
        dataStore.edit {
            next = (it[WAKE_COUNT] ?: 0L) + 1
            it[WAKE_COUNT] = next
        }
        return next
    }

    private companion object {
        val WAKE_COUNT = longPreferencesKey("wake_count")
    }
}
