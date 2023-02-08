package de.hpi.etranslation.feature.onboard.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.hpi.etranslation.feature.onboard.OnboardEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OnboardModule {
    @Provides
    @Singleton
    fun provideOnboardEventChannel(): Channel<OnboardEvent> = Channel(capacity = Channel.BUFFERED)

    @Provides
    fun provideOnboardEventSender(
        onboardEventChannel: Channel<OnboardEvent>,
    ): SendChannel<OnboardEvent> = onboardEventChannel

    @Provides
    fun provideOnboardEventReceiver(
        onboardEventChannel: Channel<OnboardEvent>,
    ): ReceiveChannel<OnboardEvent> = onboardEventChannel
}
