package de.hpi.etranslation.feature.dashboard.di

import care.data4life.fhir.r4.FhirR4Parser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.hpi.etranslation.feature.dashboard.DashboardError
import de.hpi.etranslation.feature.dashboard.DashboardEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DashboardModule {
    @Provides
    @Singleton
    fun provideDashboardEventChannel(): Channel<DashboardEvent> =
        Channel(capacity = Channel.BUFFERED)

    @Provides
    fun provideDashboardEventSender(
        dashboardEventChannel: Channel<DashboardEvent>,
    ): SendChannel<DashboardEvent> = dashboardEventChannel

    @Provides
    fun provideDashboardEventReceiver(
        dashboardEventChannel: Channel<DashboardEvent>,
    ): ReceiveChannel<DashboardEvent> = dashboardEventChannel

    @Provides
    @Singleton
    fun provideRefreshingMutableFlow(): MutableStateFlow<Boolean> = MutableStateFlow(false)

    @Provides
    fun provideRefreshingFlow(
        refreshingMutableFlow: MutableStateFlow<Boolean>,
    ): StateFlow<Boolean> = refreshingMutableFlow

    @Provides
    fun provideFhirR4Parser() = FhirR4Parser()

    @Provides
    @Singleton
    fun provideDashboardErrorChannel(): Channel<DashboardError> =
        Channel(capacity = Channel.BUFFERED)

    @Provides
    fun provideDashboardErrorSender(
        dashboardErrorChannel: Channel<DashboardError>,
    ): SendChannel<DashboardError> = dashboardErrorChannel

    @Provides
    fun provideDashboardErrorReceiver(
        dashboardErrorChannel: Channel<DashboardError>,
    ): ReceiveChannel<DashboardError> = dashboardErrorChannel
}
