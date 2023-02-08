package de.hpi.etranslation.feature.dashboard.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.hpi.etranslation.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HttpModule {

    @Provides
    @Singleton
    fun provideHttpClient() = HttpClient {
        install(ContentNegotiation) {
            json()
        }

        defaultRequest {
            // https://api.ktor.io/ktor-client/ktor-client-core/io.ktor.client.plugins/-default-request/index.html
            url("https://etranslation.smart4health.eu/api/v1/")
        }

        install(Auth) {
            basic {
                credentials {
                    BasicAuthCredentials(
                        username = BuildConfig.ES_USER,
                        password = BuildConfig.ES_PASSWORD,
                    )
                }

                sendWithoutRequest { true }
            }
        }

        expectSuccess = false
    }
}
