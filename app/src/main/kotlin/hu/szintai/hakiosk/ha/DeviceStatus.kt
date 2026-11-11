package hu.szintai.hakiosk.ha

/** Hardware / network state of the tablet reported to Home Assistant. */
data class DeviceStatus(
    val batteryLevel: Int,
    val charging: Boolean,
    /** `ac`, `usb`, `wireless` or `none`. */
    val charger: String,
    val batteryTempC: Float?,
    val ip: String?,
    /** Present only while camera sharing is enabled. */
    val cameraStreamUrl: String?,
    val cameraSnapshotUrl: String?,
)
