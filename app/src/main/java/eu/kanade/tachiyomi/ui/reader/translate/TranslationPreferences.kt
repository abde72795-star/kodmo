package eu.kanade.tachiyomi.ui.reader.translate

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * Preferences of the on-device page translation feature.
 * API keys use private keys so that they are not exported with backups.
 */
class TranslationPreferences(
    private val preferenceStore: PreferenceStore,
) {
    fun enabled() = preferenceStore.getBoolean("pref_page_translation_enabled", false)

    /** Language code of the text in the page, or [TranslationLanguages.AUTO]. */
    fun sourceLanguage() = preferenceStore.getString("pref_page_translation_source", TranslationLanguages.AUTO)

    fun targetLanguage() = preferenceStore.getString("pref_page_translation_target", "en")

    fun provider() = preferenceStore.getString("pref_page_translation_provider", TranslationProviderId.OPENAI.id)

    fun apiKey(provider: TranslationProviderId) =
        preferenceStore.getString(Preference.privateKey("page_translation_key_${provider.id}"), "")

    fun baseUrl(provider: TranslationProviderId) =
        preferenceStore.getString("pref_page_translation_url_${provider.id}", provider.defaultBaseUrl)

    fun model(provider: TranslationProviderId) =
        preferenceStore.getString("pref_page_translation_model_${provider.id}", provider.defaultModel)

    /** 0.5 .. 1.5 multiplier applied to the fitted text size. */
    fun fontScale() = preferenceStore.getFloat("pref_page_translation_font_scale", 1f)

    /** One of [TextColorMode]. */
    fun textColorMode() = preferenceStore.getString("pref_page_translation_text_color", TextColorMode.AUTO.id)

    fun cacheMaxMb() = preferenceStore.getInt("pref_page_translation_cache_mb", 256)

    fun currentProvider(): TranslationProviderId = TranslationProviderId.fromId(provider().get())
}

enum class TextColorMode(val id: String) {
    AUTO("auto"),
    BLACK("black"),
    WHITE("white"),
    ;

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id } ?: AUTO
    }
}

enum class TranslationProviderId(
    val id: String,
    val label: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val needsModel: Boolean,
) {
    OPENAI("openai", "OpenAI-compatible", "https://api.openai.com/v1", "gpt-4o-mini", true),
    GEMINI("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta", "gemini-2.0-flash", true),
    DEEPL("deepl", "DeepL", "", "", false),
    ;

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id } ?: OPENAI
    }
}

object TranslationLanguages {
    const val AUTO = "auto"

    /** code to English name; the name is also used inside LLM prompts. */
    val all: List<Pair<String, String>> = listOf(
        "en" to "English",
        "ar" to "Arabic",
        "ja" to "Japanese",
        "ko" to "Korean",
        "zh" to "Chinese",
        "fr" to "French",
        "de" to "German",
        "es" to "Spanish",
        "pt" to "Portuguese",
        "it" to "Italian",
        "ru" to "Russian",
        "tr" to "Turkish",
        "id" to "Indonesian",
        "vi" to "Vietnamese",
        "th" to "Thai",
        "hi" to "Hindi",
    )

    fun nameOf(code: String): String = all.firstOrNull { it.first == code }?.second ?: code
}
