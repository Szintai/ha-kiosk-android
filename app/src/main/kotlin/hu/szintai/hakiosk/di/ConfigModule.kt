package hu.szintai.hakiosk.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import hu.szintai.hakiosk.config.ConfigRepository
import hu.szintai.hakiosk.config.DataStoreConfigRepository
import javax.inject.Singleton

private val Context.configDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_config")

@Module
@InstallIn(SingletonComponent::class)
abstract class ConfigModule {

    @Binds
    @Singleton
    abstract fun bindConfigRepository(impl: DataStoreConfigRepository): ConfigRepository

    companion object {
        @Provides
        @Singleton
        fun provideConfigDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            context.configDataStore
    }
}
