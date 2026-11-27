package eu.kanade.tachiyomi.ui.reader.translation

import java.security.MessageDigest

/**
 * Stable, non-secret configuration for translating one reader page.
 *
 * Provider credentials are deliberately kept out of this model and out of cache keys.
 */
data class PageTranslationConfig(
    val sourceLanguage: String,
    val targetLanguage: String,
    val ocrEngineId: String,
    val ocrModelVersion: String,
    val translatorId: String,
    val translatorModel: String,
    val rendererVersion: String = "1",
)

data class PageTextRegion(
    /** Normalized coordinates in the range 0..1, relative to the original image. */
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val text: String,
    val confidence: Float,
    val readingOrder: Int,
) {
    init {
        require(left in 0f..1f && top in 0f..1f)
        require(right in 0f..1f && bottom in 0f..1f)
        require(right >= left && bottom >= top)
        require(confidence in 0f..1f)
    }
}

data class TranslatedTextRegion(
    val region: PageTextRegion,
    val translatedText: String,
)

data class PageTranslationRequest(
    /** Stable page identity from the reader; must not contain credentials. */
    val pageId: String,
    val originalImage: ByteArray,
    val config: PageTranslationConfig,
)

data class PageTranslationResult(
    val translatedImage: ByteArray,
    val regions: List<TranslatedTextRegion>,
    val fromCache: Boolean,
)

interface PageOcrEngine {
    val id: String
    val modelVersion: String

    suspend fun recognize(
        image: ByteArray,
        sourceLanguage: String,
    ): List<PageTextRegion>
}

interface PageTextTranslator {
    val id: String
    val model: String

    /** Returns one translated string for every input string, in the same order. */
    suspend fun translate(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
    ): List<String>
}

interface PageImageRenderer {
    val version: String

    /** Must return a new image; the original byte array must remain untouched. */
    suspend fun render(
        originalImage: ByteArray,
        regions: List<TranslatedTextRegion>,
    ): ByteArray
}

interface PageTranslationCache {
    suspend fun get(key: String): ByteArray?
    suspend fun put(key: String, translatedImage: ByteArray)
    suspend fun clear()
}

/**
 * Coordinates OCR -> translation -> image reconstruction.
 *
 * This class is intentionally independent of Android views and the reader's page loader.
 * A caller can cancel the coroutine at any stage. The source image is never modified.
 */
class DefaultPageTranslationRepository(
    private val ocr: PageOcrEngine,
    private val translator: PageTextTranslator,
    private val renderer: PageImageRenderer,
    private val cache: PageTranslationCache,
) {
    suspend fun translate(request: PageTranslationRequest): PageTranslationResult {
        require(request.originalImage.isNotEmpty()) { "The source page image is empty." }
        require(request.config.ocrEngineId == ocr.id)
        require(request.config.ocrModelVersion == ocr.modelVersion)
        require(request.config.translatorId == translator.id)
        require(request.config.translatorModel == translator.model)
        require(request.config.rendererVersion == renderer.version)

        val cacheKey = createCacheKey(request)
        cache.get(cacheKey)?.let { cached ->
            return PageTranslationResult(
                translatedImage = cached,
                regions = emptyList(),
                fromCache = true,
            )
        }

        val regions = ocr.recognize(
            image = request.originalImage,
            sourceLanguage = request.config.sourceLanguage,
        ).sortedBy { it.readingOrder }

        if (regions.isEmpty()) {
            return PageTranslationResult(
                translatedImage = request.originalImage.copyOf(),
                regions = emptyList(),
                fromCache = false,
            )
        }

        val translated = translator.translate(
            texts = regions.map { it.text },
            sourceLanguage = request.config.sourceLanguage,
            targetLanguage = request.config.targetLanguage,
        )
        require(translated.size == regions.size) {
            "The translation provider returned a different number of results than requested."
        }

        val translatedRegions = regions.zip(translated).map { (region, text) ->
            TranslatedTextRegion(region, text)
        }
        val translatedImage = renderer.render(request.originalImage, translatedRegions)
        require(translatedImage.isNotEmpty()) { "The renderer returned an empty image." }
        cache.put(cacheKey, translatedImage)

        return PageTranslationResult(
            translatedImage = translatedImage,
            regions = translatedRegions,
            fromCache = false,
        )
    }

    private fun createCacheKey(request: PageTranslationRequest): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(request.originalImage)
        val config = request.config
        val metadata = listOf(
            request.pageId,
            config.sourceLanguage,
            config.targetLanguage,
            config.ocrEngineId,
            config.ocrModelVersion,
            config.translatorId,
            config.translatorModel,
            config.rendererVersion,
        ).joinToString(separator = "\u0000")
        digest.update(metadata.toByteArray(Charsets.UTF_8))
        return digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    }
}
