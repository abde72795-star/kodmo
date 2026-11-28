package eu.kanade.tachiyomi.ui.reader.translate

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import okio.Buffer
import okio.BufferedSource
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Entry point of the page translation pipeline:
 * cache lookup, OCR, translation through the selected provider, rendering, cache write.
 * The original page is never modified.
 */
object PageTranslationService {

    sealed interface Status {
        data object Idle : Status
        data class Working(val pages: Int) : Status
        data class Done(val cached: Boolean) : Status
        data class Failed(val message: String) : Status
    }

    private val application: Application by injectLazy()
    private val preferences: TranslationPreferences by injectLazy()
    private val network: NetworkHelper by injectLazy()

    private val ocr = MlKitOcrEngine()

    val cache: TranslatedPageCache by lazy {
        TranslatedPageCache(
            directory = File(application.cacheDir, "translated_pages"),
            maxBytes = { preferences.cacheMaxMb().get().toLong() * 1024L * 1024L },
        )
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status

    private val running = MutableStateFlow(0)

    /** OCR and drawing are memory heavy: one page at a time. */
    private val gate = Semaphore(1)

    /**
     * Returns the translated image for [source], or null when the page cannot or need not be translated
     * (the reader then shows the original). Failures are reported through [status].
     */
    suspend fun translate(source: BufferedSource): BufferedSource? {
        val bytes = withIOContext { source.peek().readByteArray() }
        return translateBytes(bytes)?.let { Buffer().write(it) }
    }

    suspend fun translateBytes(bytes: ByteArray): ByteArray? {
        running.update { it + 1 }
        _status.value = Status.Working(running.value)
        try {
            val result = withIOContext { run(bytes) }
            return result
        } catch (e: CancellationException) {
            throw e
        } catch (e: PageTranslationException) {
            logcat(LogPriority.WARN, e) { "Page translation failed" }
            _status.value = Status.Failed(e.message ?: "Translation failed")
            return null
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Page translation failed" }
            _status.value = Status.Failed(e.message ?: e.javaClass.simpleName)
            return null
        } finally {
            val left = running.updateAndGet { it - 1 }
            if (left == 0 && _status.value is Status.Working) _status.value = Status.Idle
        }
    }

    private fun currentTranslator(): TextTranslator {
        val id = preferences.currentProvider()
        val key = preferences.apiKey(id).get().trim()
        return when (id) {
            TranslationProviderId.OPENAI -> OpenAiCompatibleTranslator(
                network.client,
                preferences.baseUrl(id).get().trim(),
                preferences.model(id).get().trim(),
                key,
            )
            TranslationProviderId.GEMINI -> GeminiTranslator(
                network.client,
                preferences.baseUrl(id).get().trim(),
                preferences.model(id).get().trim(),
                key,
            )
            TranslationProviderId.DEEPL -> DeepLTranslator(network.client, key)
        }
    }

    private suspend fun run(bytes: ByteArray): ByteArray? {
        val source = preferences.sourceLanguage().get()
        val target = preferences.targetLanguage().get()
        val style = RenderStyle(
            fontScale = preferences.fontScale().get(),
            textColor = TextColorMode.fromId(preferences.textColorMode().get()),
        )
        val translator = currentTranslator()
        val key = TranslatedPageCache.key(
            imageHash = TranslatedPageCache.sha256(bytes),
            ocrId = ocr.id,
            source = source,
            target = target,
            providerSignature = translator.signature,
            renderVersion = PageRenderer.VERSION,
            style = style,
        )
        cache.get(key)?.let {
            _status.value = Status.Done(cached = true)
            return it
        }

        return gate.withPermit {
            val bitmap = decode(bytes)
            try {
                val blocks = recogniseTall(bitmap, source)
                if (blocks.isEmpty()) {
                    _status.value = Status.Failed("No text found in this page")
                    return@withPermit null
                }
                val texts = blocks.map { if (isCjk(it.text)) it.text.replace("\n", "") else it.text.replace('\n', ' ') }
                val translated = translator.translate(texts, source, target)
                val rendered = PageRenderer.render(
                    bitmap,
                    blocks.mapIndexed { i, b -> TranslatedBlock(b.box, b.text, translated[i]) },
                    style,
                )
                val out = encode(rendered)
                cache.put(key, out)
                _status.value = Status.Done(cached = false)
                out
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun isCjk(text: String) = ScriptDetector.detect(text).let {
        it == Script.JAPANESE || it == Script.CHINESE
    }

    private fun decode(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw PageTranslationException("Unsupported image format")
        }
        if (bounds.outWidth.toLong() * bounds.outHeight > MAX_PIXELS) {
            throw PageTranslationException("This image is too large to translate")
        }
        val options = BitmapFactory.Options().apply {
            inMutable = true
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw PageTranslationException("Could not decode the image")
    }

    /** OCR in vertical strips so very long webtoon pages stay within ML Kit and memory limits. */
    private suspend fun recogniseTall(bitmap: Bitmap, source: String): List<TextBlock> {
        if (bitmap.height <= STRIP_HEIGHT + STRIP_OVERLAP) return ocr.recognise(bitmap, source)
        val all = ArrayList<TextBlock>()
        var y = 0
        while (y < bitmap.height) {
            val last = y + STRIP_HEIGHT + STRIP_OVERLAP >= bitmap.height
            val h = if (last) bitmap.height - y else STRIP_HEIGHT + STRIP_OVERLAP
            val strip = Bitmap.createBitmap(bitmap, 0, y, bitmap.width, h)
            try {
                for (block in ocr.recognise(strip, source)) {
                    val centre = block.box.centerY()
                    val keepFrom = if (y > 0) STRIP_OVERLAP / 2 else 0
                    val keepTo = if (last) h else h - STRIP_OVERLAP / 2
                    if (centre in keepFrom until keepTo) {
                        block.box.offset(0, y)
                        all.add(block)
                    }
                }
            } finally {
                strip.recycle()
            }
            if (last) break
            y += STRIP_HEIGHT
        }
        return all
    }

    private fun encode(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream(bitmap.byteCount / 8)
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.JPEG
        }
        if (!bitmap.compress(format, 95, out)) throw PageTranslationException("Could not encode the translated page")
        return out.toByteArray()
    }

    private const val MAX_PIXELS = 40_000_000L
    private const val STRIP_HEIGHT = 2400
    private const val STRIP_OVERLAP = 240
}
