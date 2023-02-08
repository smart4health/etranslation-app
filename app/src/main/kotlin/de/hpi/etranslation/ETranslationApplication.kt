package de.hpi.etranslation

import android.app.Application
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.hilt.work.HiltWorkerFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp
import de.hpi.etranslation.di.ApplicationCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import androidx.work.Configuration as WorkConfiguration

@HiltAndroidApp
class ETranslationApplication : Application(), WorkConfiguration.Provider {

    @Inject
    lateinit var localeMutableStateFlow: MutableStateFlow<List<Locale>>

    @Inject
    lateinit var settingsDataStore: DataStore<Settings>

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    @ApplicationCoroutineScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()

        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)

        localeMutableStateFlow.value =
            ConfigurationCompat.getLocales(resources.configuration).toList()

        applicationScope.launch {
            settingsDataStore.data.collect { settings ->
                when (settings.theme) {
                    Theme.DAY -> AppCompatDelegate.MODE_NIGHT_NO
                    Theme.NIGHT -> AppCompatDelegate.MODE_NIGHT_YES
                    Theme.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }.let(AppCompatDelegate::setDefaultNightMode)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        localeMutableStateFlow.value =
            ConfigurationCompat.getLocales(newConfig).toList()
    }

    override fun getWorkManagerConfiguration(): WorkConfiguration =
        WorkConfiguration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}

private fun LocaleListCompat.toList(): List<Locale> = (0 until size()).mapNotNull(this::get)
