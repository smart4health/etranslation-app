package de.hpi.etranslation.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.hpi.etranslation.MainEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MainModule {

    @Provides
    @Singleton
    fun provideMainEventChannel(): Channel<MainEvent> = Channel(capacity = Channel.BUFFERED)

    @Provides
    fun provideMainEventSender(
        mainEventChannel: Channel<MainEvent>,
    ): SendChannel<MainEvent> = mainEventChannel

    @Provides
    fun provideMainEventReceiver(
        mainEventChannel: Channel<MainEvent>,
    ): ReceiveChannel<MainEvent> = mainEventChannel

    @Provides
    @Singleton
    fun provideLocaleMutableStateFlow(): MutableStateFlow<List<Locale>> = MutableStateFlow(listOf())

    @Provides
    @Singleton
    fun provideLocaleStateFlow(
        localeMutableStateFlow: MutableStateFlow<List<Locale>>,
    ): Flow<List<Locale>> = localeMutableStateFlow

    @Provides
    @Singleton
    @ApplicationCoroutineScope
    fun provideApplicationCoroutineScope(): CoroutineScope = MainScope()
}

@Qualifier
annotation class ApplicationCoroutineScope
