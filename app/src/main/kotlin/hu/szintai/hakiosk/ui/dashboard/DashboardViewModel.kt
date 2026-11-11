package hu.szintai.hakiosk.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.szintai.hakiosk.config.AppConfig
import hu.szintai.hakiosk.config.ConfigRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    configRepository: ConfigRepository,
) : ViewModel() {

    /** `null` until the first config value has been read from storage. */
    val config: StateFlow<AppConfig?> = configRepository.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
