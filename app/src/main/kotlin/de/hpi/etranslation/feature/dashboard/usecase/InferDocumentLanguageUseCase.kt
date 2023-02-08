package de.hpi.etranslation.feature.dashboard.usecase

import androidx.datastore.core.DataStore
import de.hpi.etranslation.Lang
import de.hpi.etranslation.Settings
import de.hpi.etranslation.toLang
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

class InferDocumentLanguageUseCase @Inject constructor() {
    operator fun invoke(
        documentLanguage: Lang?,
        globalOverrideLanguage: Lang?,
        locales: List<Locale>,
    ): Pair<Lang, Source> {
        val deviceLang = locales.mapNotNull(Locale::toLang).firstOrNull()

        return when {
            documentLanguage != null -> documentLanguage to Source.LOCAL_OVERRIDE
            globalOverrideLanguage != null -> globalOverrideLanguage to Source.GLOBAL_OVERRIDE
            deviceLang != null -> deviceLang to Source.LOCALE
            else -> Lang.EN to Source.FALLBACK
        }
    }

    enum class Source {
        LOCAL_OVERRIDE,
        GLOBAL_OVERRIDE,
        LOCALE,
        FALLBACK,
    }
}

@Singleton
class InferDocumentLanguagePartial @Inject constructor(
    settingsDataStore: DataStore<Settings>,
    localeFlow: @JvmSuppressWildcards Flow<List<Locale>>,
    private val inferDocumentLanguageUseCase: InferDocumentLanguageUseCase,
) {
    val flow = settingsDataStore.data.combine(localeFlow) { settings, locales ->
        { documentLanguage: Lang? ->
            inferDocumentLanguageUseCase(
                documentLanguage = documentLanguage,
                globalOverrideLanguage = settings.langOverride?.toLang(),
                locales = locales,
            )
        }
    }
}
