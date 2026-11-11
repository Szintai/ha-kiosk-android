package hu.szintai.hakiosk.ha

import android.os.Build
import android.util.Log
import hu.szintai.hakiosk.BuildConfig
import hu.szintai.hakiosk.config.AppConfig
import hu.szintai.hakiosk.config.ConfigRepository
import hu.szintai.hakiosk.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reports to Home Assistant through the built-in `mobile_app` integration: the tablet registers
 * itself once (with the long-lived token) and then only talks to its own webhook. Entities:
 *  - `sensor.kiosk_tablet_wake_count`   (total_increasing, so HA keeps long-term statistics)
 *  - `binary_sensor.kiosk_tablet_presence`
 */
@Singleton
class MobileAppHaReporter @Inject constructor(
    private val api: HaApi,
    private val store: HaDeviceStore,
    private val configRepository: ConfigRepository,
    @ApplicationScope scope: CoroutineScope,
) : HaReporter {

    private val latest = MutableStateFlow<PresenceSnapshot?>(null)

    init {
        scope.launch {
            latest.filterNotNull().collectLatest { snapshot ->
                // collectLatest cancels the retry wait as soon as a newer snapshot arrives.
                while (true) {
                    try {
                        send(snapshot)
                        break
                    } catch (e: Exception) {
                        Log.w(TAG, "HA report failed, retrying: ${e.message}")
                        delay(RETRY_MS)
                    }
                }
            }
        }
    }

    override fun update(snapshot: PresenceSnapshot) {
        latest.value = snapshot
    }

    private suspend fun send(snapshot: PresenceSnapshot) {
        val config = configRepository.config.first()
        if (!config.isConfigured || !config.reportToHa) return

        var registration = store.read()
        if (registration.webhookId == null) {
            registration = registration.copy(webhookId = registerDevice(config, registration.deviceId))
        }
        val webhook = "${config.haUrl.trimEnd('/')}/api/webhook/${registration.webhookId}"

        if (!registration.sensorsRegistered) {
            registerSensors(webhook, snapshot)
            store.markSensorsRegistered()
            return
        }
        val response = api.postJson(webhook, null, request("update_sensor_states", sensorStates(snapshot)))
        if (response.code == HTTP_GONE) { // webhook removed in HA -> register again
            store.clearRegistration()
            throw IllegalStateException("webhook gone, re-registering")
        }
        check(response.ok) { "update_sensor_states -> ${response.code}" }
    }

    private suspend fun registerDevice(config: AppConfig, deviceId: String): String {
        val body = JSONObject()
            .put("device_id", deviceId)
            .put("app_id", BuildConfig.APPLICATION_ID)
            .put("app_name", "HA Kiosk")
            .put("app_version", BuildConfig.VERSION_NAME)
            .put("device_name", DEVICE_NAME)
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("os_name", "Android")
            .put("os_version", Build.VERSION.RELEASE)
            .put("supports_encryption", false)
            .put("app_data", JSONObject())
        val response = api.postJson("${config.haUrl.trimEnd('/')}/api/mobile_app/registrations", config.haToken, body)
        check(response.ok) { "register device -> ${response.code}" }
        val webhookId = JSONObject(response.body).getString("webhook_id")
        store.saveWebhook(webhookId)
        return webhookId
    }

    private suspend fun registerSensors(webhook: String, snapshot: PresenceSnapshot) {
        val wake = JSONObject()
            .put("unique_id", WAKE_COUNT).put("name", "Wake count").put("type", "sensor")
            .put("state", snapshot.wakeCount).put("state_class", "total_increasing")
            .put("icon", "mdi:motion-sensor")
        val presence = JSONObject()
            .put("unique_id", PRESENCE).put("name", "Presence").put("type", "binary_sensor")
            .put("state", snapshot.present).put("device_class", "occupancy")
            .put("icon", "mdi:account-eye")
        for (sensor in listOf(wake, presence)) {
            val response = api.postJson(webhook, null, request("register_sensor", sensor))
            check(response.ok) { "register_sensor ${sensor.getString("unique_id")} -> ${response.code}" }
        }
    }

    private fun sensorStates(snapshot: PresenceSnapshot): JSONArray = JSONArray()
        .put(JSONObject().put("unique_id", WAKE_COUNT).put("type", "sensor").put("state", snapshot.wakeCount).put("icon", "mdi:motion-sensor"))
        .put(JSONObject().put("unique_id", PRESENCE).put("type", "binary_sensor").put("state", snapshot.present).put("icon", "mdi:account-eye"))

    private fun request(type: String, data: Any): JSONObject = JSONObject().put("type", type).put("data", data)

    private companion object {
        const val TAG = "HaReporter"
        const val DEVICE_NAME = "Kiosk Tablet"
        const val WAKE_COUNT = "wake_count"
        const val PRESENCE = "presence"
        const val RETRY_MS = 30_000L
        const val HTTP_GONE = 410
    }
}
