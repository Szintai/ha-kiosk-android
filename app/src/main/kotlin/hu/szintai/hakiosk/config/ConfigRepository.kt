package hu.szintai.hakiosk.config

import kotlinx.coroutines.flow.Flow

interface ConfigRepository {
    val config: Flow<AppConfig>

    suspend fun update(transform: (AppConfig) -> AppConfig)
}
