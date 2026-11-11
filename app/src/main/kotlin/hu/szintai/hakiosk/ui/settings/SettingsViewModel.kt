package hu.szintai.hakiosk.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.szintai.hakiosk.config.AppConfig
import hu.szintai.hakiosk.config.ConfigRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
) : ViewModel() {

    suspend fun load(): AppConfig = configRepository.config.first()

    fun save(config: AppConfig, onSaved: () -> Unit) {
        viewModelScope.launch {
            configRepository.update { config }
            onSaved()
        }
    }
}
