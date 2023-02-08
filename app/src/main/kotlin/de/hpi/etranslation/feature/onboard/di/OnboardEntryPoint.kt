package de.hpi.etranslation.feature.onboard.di

import androidx.datastore.core.DataStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.hpi.etranslation.MainEvent
import de.hpi.etranslation.Settings
import de.hpi.etranslation.di.ApplicationCoroutineScope
import de.hpi.etranslation.feature.dashboard.usecase.RefreshUseCase
import de.hpi.etranslation.feature.onboard.OnboardEvent
import de.hpi.etranslation.lib.chdp.AsyncData4LifeClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel

@EntryPoint
@InstallIn(SingletonComponent::class)
interface OnboardEntryPoint {
    val onboardEventSender: @JvmSuppressWildcards SendChannel<OnboardEvent>

    val onboardEventReceiver: @JvmSuppressWildcards ReceiveChannel<OnboardEvent>

    val mainEventSender: @JvmSuppressWildcards SendChannel<MainEvent>

    val dataStore: DataStore<Settings>

    val d4lClient: AsyncData4LifeClient

    @get:ApplicationCoroutineScope
    val applicationScope: CoroutineScope

    val refreshUseCase: RefreshUseCase
}
