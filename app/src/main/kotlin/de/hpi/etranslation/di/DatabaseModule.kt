package de.hpi.etranslation.di

import android.content.Context
import com.squareup.sqldelight.EnumColumnAdapter
import com.squareup.sqldelight.android.AndroidSqliteDriver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.hpi.etranslation.Lang
import de.hpi.etranslation.data.Accounts
import de.hpi.etranslation.data.Documents
import de.hpi.etranslation.data.DocumentsDatabase
import de.hpi.etranslation.data.InstantAdapter
import de.hpi.etranslation.data.Requests
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    fun provideLangEnumColumnAdapter() = EnumColumnAdapter<Lang>()

    @Provides
    @Singleton
    fun provideDriver(
        @ApplicationContext
        context: Context,
    ) = AndroidSqliteDriver(DocumentsDatabase.Schema, context, "documents.db")

    @Provides
    @Singleton
    fun provideDatabase(
        driver: AndroidSqliteDriver,
        langAdapter: EnumColumnAdapter<Lang>,
    ) = DocumentsDatabase(
        driver,
        documentsAdapter = Documents.Adapter(
            langAdapter = langAdapter,
            updated_atAdapter = InstantAdapter,
            chdp_fetched_atAdapter = InstantAdapter,
            resource_dateAdapter = InstantAdapter,
        ),
        requestsAdapter = Requests.Adapter(
            target_langAdapter = langAdapter,
            updated_atAdapter = InstantAdapter,
        ),
        accountsAdapter = Accounts.Adapter(
            typeAdapter = EnumColumnAdapter(),
        ),
    )
}
