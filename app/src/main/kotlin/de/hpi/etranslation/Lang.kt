package de.hpi.etranslation

import java.util.Locale

/**
 * Supported subset of ISO 639-1 language codes as
 * used by CEF
 *
 * CEF of course supports more than these, but these
 * are the languages in the grant agreement
 *
 * CEF also deals in language pairs: all languages
 * here can be translated to and from one another
 *
 * Not using Locales directly because they encode
 * more info than necessary (country, sub languages)
 * and are missing a Portuguese constant as well
 */
enum class Lang {
    EN,
    DE,
    FR,
    PT,
    IT,
    NL,
}

/**
 * Two things: Encode the order separate from the declaration, and remove the need for
 *             the memory hungry .values() call
 */
val ALL_LANGS = listOf(
    Lang.EN,
    Lang.DE,
    Lang.FR,
    Lang.PT,
    Lang.IT,
    Lang.NL,
)

fun Lang.asFlag(): String = when (this) {
    Lang.EN -> "🇺🇸"
    Lang.DE -> "🇩🇪"
    Lang.FR -> "🇫🇷"
    Lang.PT -> "🇵🇹"
    Lang.IT -> "🇮🇹"
    Lang.NL -> "🇳🇱"
}

fun Locale.toLang(): Lang? = ALL_LANGS.find { lang ->
    lang
        .toString()
        .equals(language, ignoreCase = true)
}

fun String.toLang(): Lang? = ALL_LANGS.find { lang ->
    lang
        .toString()
        .equals(this, ignoreCase = true)
}
