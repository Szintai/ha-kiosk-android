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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reports to Home Assistant through the built-in `mobile_app` integration: the tablet registers
 * itself once (with the long-lived token) and then only talks to its own webhook. Entities
 * (device "Kiosk Tablet"):
 *  - `sensor.kiosk_tablet_wake_count`          total_increasing, long-term statistics
 *  - `binary_sensor.kiosk_tablet_presence`     on = display active, off = dimmed
 *  - `sensor.kiosk_tablet_battery_level`       %
 *  - `binary_sensor.kiosk_tablet_battery_charging`
 *  - `sensor.kiosk_tablet_battery_temperature` °C
 *  - `sensor.kiosk_tablet_ip_address`          attributes carry the camera URLs when sharing is on
 */
@Singleton
class MobileAppHaReporter @Inject constructor(
    private val api: HaApi,
    private val store: HaDeviceStore,
    private val configRepository: ConfigRepository,
    @ApplicationScope scope: CoroutineScope,
) : HaReporter {

    private data class Report(val presence: PresenceSnapshot? = null, val device: DeviceStatus? = null)

    private val latest = MutableStateFlow(Report())

    init {
        scope.launch {
            latest.collectLatest { report ->
                if (report.presence == null && report.device == null) return@collectLatest
                // collectLatest cancels the retry wait as soon as newer values arrive.
                while (true) {
                    try {
                        send(report)
                        break
                    } catch (e: Exception) {
                        Log.w(TAG, "HA report failed, retrying: ${e.message}")
                        delay(RETRY_MS)
                    }
                }
            }
        }
    }

    override fun update(snapshot: PresenceSnapshot) = latest.update { it.copy(presence = snapshot) }

    override fun updateDevice(status: DeviceStatus) = latest.update { it.copy(device = status) }

    private suspend fun send(report: Report) {
        val config = configRepository.config.first()
        if (!config.isConfigured || !config.reportToHa) return

        var registration = store.read()
        if (registration.webhookId == null) {
            registration = registration.copy(webhookId = registerDevice(config, registration.deviceId))
        }
        val webhook = "${config.haUrl.trimEnd('/')}/api/webhook/${registration.webhookId}"

        if (registration.sensorsVersion < SENSORS_VERSION) {
            registerSensors(webhook, report)
            store.markSensorsRegistered(SENSORS_VERSION)
            return
        }
        val response = api.postJson(webhook, null, request("update_sensor_states", sensorStates(report)))
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

    /** Registers (or re-registers, which updates) every sensor, with the current values as initial state. */
    private suspend fun registerSensors(webhook: String, report: Report) {
        val p = report.presence ?: PresenceSnapshot(0, true)
        val d = report.device
        val sensors = listOf(
            JSONObject().put("unique_id", WAKE_COUNT).put("name", "Wake count").put("type", "sensor")
                .put("state", p.wakeCount).put("state_class", "total_increasing").put("icon", "mdi:motion-sensor"),
            JSONObject().put("unique_id", PRESENCE).put("name", "Presence").put("type", "binary_sensor")
                .put("state", p.present).put("device_class", "occupancy").put("icon", "mdi:account-eye"),
            JSONObject().put("unique_id", BATTERY_LEVEL).put("name", "Battery level").put("type", "sensor")
                .put("state", d?.batteryLevel ?: 0).put("device_class", "battery").put("unit_of_measurement", "%")
                .put("state_class", "measurement").put("entity_category", "diagnostic"),
            JSONObject().put("unique_id", BATTERY_CHARGING).put("name", "Battery charging").put("type", "binary_sensor")
                .put("state", d?.charging ?: false).put("device_class", "battery_charging").put("entity_category", "diagnostic"),
            JSONObject().put("unique_id", BATTERY_TEMP).put("name", "Battery temperature").put("type", "sensor")
                .put("state", d?.batteryTempC ?: 0f).put("device_class", "temperature").put("unit_of_measurement", "°C")
                .put("state_class", "measurement").put("entity_category", "diagnostic"),
            JSONObject().put("unique_id", IP_ADDRESS).put("name", "IP address").put("type", "sensor")
                .put("state", d?.ip ?: "unknown").put("icon", "mdi:ip-network").put("entity_category", "diagnostic")
                .put("attributes", ipAttributes(d)),
        )
        for (sensor in sensors) {
            val response = api.postJson(webhook, null, request("register_sensor", sensor))
            check(response.ok) { "register_sensor ${sensor.getString("unique_id")} -> ${response.code}" }
        }
    }

    private fun sensorStates(report: Report): JSONArray {
        val states = JSONArray()
        report.presence?.let { p ->
            states.put(JSONObject().put("unique_id", WAKE_COUNT).put("type", "sensor").put("state", p.wakeCount).put("icon", "mdi:motion-sensor"))
            states.put(JSONObject().put("unique_id", PRESENCE).put("type", "binary_sensor").put("state", p.present).put("icon", "mdi:account-eye"))
        }
        report.device?.let { d ->
            states.put(JSONObject().put("unique_id", BATTERY_LEVEL).put("type", "sensor").put("state", d.batteryLevel)
                .put("icon", batteryIcon(d.batteryLevel, d.charging)))
            states.put(JSONObject().put("unique_id", BATTERY_CHARGING).put("type", "binary_sensor").put("state", d.charging)
                .put("attributes", JSONObject().put("charger", d.charger)))
            d.batteryTempC?.let {
                states.put(JSONObject().put("unique_id", BATTERY_TEMP).put("type", "sensor").put("state", it))
            }
            states.put(JSONObject().put("unique_id", IP_ADDRESS).put("type", "sensor").put("state", d.ip ?: "unknown")
                .put("attributes", ipAttributes(d)))
        }
        return states
    }

    private fun ipAttributes(d: DeviceStatus?) = JSONObject().apply {
        put("camera_stream_url", d?.cameraStreamUrl ?: JSONObject.NULL)
        put("camera_snapshot_url", d?.cameraSnapshotUrl ?: JSONObject.NULL)
    }

    private fun batteryIcon(level: Int, charging: Boolean): String {
        val step = (level / 10) * 10
        return when {
            charging -> "mdi:battery-charging-${step.coerceIn(10, 100)}".takeIf { level >= 10 } ?: "mdi:battery-charging-outline"
            level >= 95 -> "mdi:battery"
            level < 10 -> "mdi:battery-outline"
            else -> "mdi:battery-$step"
        }
    }

    private fun request(type: String, data: Any): JSONObject = JSONObject().put("type", type).put("data", data)

    private companion object {
        const val TAG = "HaReporter"
        const val DEVICE_NAME = "Kiosk Tablet"
        const val WAKE_COUNT = "wake_count"
        const val PRESENCE = "presence"
        const val BATTERY_LEVEL = "battery_level"
        const val BATTERY_CHARGING = "battery_charging"
        const val BATTERY_TEMP = "battery_temperature"
        const val IP_ADDRESS = "ip_address"
        const val SENSORS_VERSION = 2
        const val RETRY_MS = 30_000L
        const val HTTP_GONE = 410
    }
}
