package hu.szintai.hakiosk.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.szintai.hakiosk.config.AppConfig
import hu.szintai.hakiosk.config.ConfigRepository
import hu.szintai.hakiosk.share.CameraFrameServer
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
    private val cameraServer: CameraFrameServer,
) : ViewModel() {

    /** Live camera frames for the settings preview. */
    val previewFrames: StateFlow<ByteArray?> = cameraServer.preview

    fun setPreviewActive(active: Boolean) {
        cameraServer.previewActive = active
        if (!active) cameraServer.clearPreview()
    }

    fun setPreviewRotation(degrees: Int) {
        cameraServer.previewRotation = degrees
    }

    suspend fun load(): AppConfig = configRepository.config.first()

    fun save(config: AppConfig, onSaved: () -> Unit) {
        viewModelScope.launch {
            configRepository.update { config }
            onSaved()
        }
    }
}
