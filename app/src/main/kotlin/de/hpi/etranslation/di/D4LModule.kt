package de.hpi.etranslation.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.hpi.etranslation.lib.chdp.AsyncData4LifeClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object D4LModule {
    @Provides
    @Singleton
    fun provideAsyncData4LifeClient(
        @ApplicationContext
        applicationContext: Context,
    ) = AsyncData4LifeClient(applicationContext)
}
