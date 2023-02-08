package de.hpi.etranslation.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.hpi.etranslation.Settings
import de.hpi.etranslation.SettingsSerializer
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext
        applicationContext: Context,
    ): DataStore<Settings> = DataStoreFactory.create(
        serializer = SettingsSerializer,
        produceFile = { applicationContext.dataStoreFile("settings.json") },
    )
}
