package hu.szintai.hakiosk.ha

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import hu.szintai.hakiosk.config.AppConfig
import hu.szintai.hakiosk.config.ConfigRepository
import hu.szintai.hakiosk.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

/** Watches battery / charger / network and reports it to Home Assistant when something changes. */
@Singleton
class DeviceStatusMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reporter: HaReporter,
    configRepository: ConfigRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    @Volatile private var config = AppConfig()
    @Volatile private var battery: Intent? = null
    private var lastSent: DeviceStatus? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            battery = intent
            publish()
        }
    }

    init {
        battery = ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        scope.launch { configRepository.config.collect { config = it; publish() } }
        scope.launch { // the IP can change without a battery event
            while (true) {
                delay(REFRESH_MS)
                publish()
            }
        }
    }

    @Synchronized
    private fun publish() {
        val intent = battery ?: return
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val ip = localIp()
        val share = config.cameraShare && config.cameraToken.isNotBlank() && ip != null
        val status = DeviceStatus(
            batteryLevel = level * 100 / scale,
            charging = plugged != 0,
            charger = when {
                plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> "ac"
                plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "usb"
                plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> "wireless"
                else -> "none"
            },
            batteryTempC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                .takeIf { it != Int.MIN_VALUE }?.let { it / 10f },
            ip = ip,
            cameraStreamUrl = if (share) "http://$ip:${config.cameraPort}/stream.mjpeg?t=${config.cameraToken}" else null,
            cameraSnapshotUrl = if (share) "http://$ip:${config.cameraPort}/snapshot.jpg?t=${config.cameraToken}" else null,
        )
        if (status == lastSent) return
        lastSent = status
        reporter.updateDevice(status)
    }

    private fun localIp(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLinkLocalAddress }
            ?.hostAddress
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val REFRESH_MS = 10 * 60_000L
    }
}
