package eu.kanade.tachiyomi.ui.reader.translate

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface OcrEngine {
    /** Identifier and version, part of the cache key. */
    val id: String

    /** Recognise the text blocks of [bitmap]. [sourceLanguage] may be [TranslationLanguages.AUTO]. */
    suspend fun recognise(bitmap: Bitmap, sourceLanguage: String): List<TextBlock>
}

/**
 * OCR using the bundled Google ML Kit text recognisers
 * (Latin, Japanese, Chinese and Korean models are packaged in the APK, no download needed).
 */
class MlKitOcrEngine : OcrEngine {

    override val id = "mlkit-16.0.1"

    private val recognisers = HashMap<Script, TextRecognizer>()

    private fun recogniser(script: Script): TextRecognizer = synchronized(recognisers) {
        recognisers.getOrPut(script) {
            when (script) {
                Script.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                Script.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
                Script.CHINESE -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            }
        }
    }

    override suspend fun recognise(bitmap: Bitmap, sourceLanguage: String): List<TextBlock> {
        if (sourceLanguage != TranslationLanguages.AUTO) {
            return read(bitmap, ScriptDetector.forLanguage(sourceLanguage))
        }
        // Automatic: Latin first, then pick the CJK recogniser that matches what was found.
        val latin = read(bitmap, Script.LATIN)
        val joined = latin.joinToString(" ") { it.text }
        val latinLetters = joined.count { it.isLetter() && it.code < 0x250 }
        val allLetters = joined.count { it.isLetter() }
        if (allLetters > 0 && latinLetters * 10 >= allLetters * 8) return latin

        var best = latin
        var bestScore = score(latin)
        for (script in listOf(Script.JAPANESE, Script.KOREAN, Script.CHINESE)) {
            val blocks = read(bitmap, script)
            val s = score(blocks, script)
            if (s > bestScore) {
                best = blocks
                bestScore = s
            }
        }
        return best
    }

    /** Number of characters in [blocks] that belong to [script] (any non-Latin letter when null). */
    private fun score(blocks: List<TextBlock>, script: Script? = null): Int {
        val text = blocks.joinToString("") { it.text }
        return if (script == null) {
            text.count { it.isLetter() && it.code >= 0x250 }
        } else {
            blocks.sumOf { if (ScriptDetector.detect(it.text) == script) it.text.length else 0 }
        }
    }

    private suspend fun read(bitmap: Bitmap, script: Script): List<TextBlock> {
        val result = recogniser(script).process(InputImage.fromBitmap(bitmap, 0)).await()
        return result.textBlocks.mapNotNull { block ->
            val box: Rect = block.boundingBox ?: return@mapNotNull null
            val text = block.text.trim()
            if (text.isEmpty() || text.none { it.isLetterOrDigit() }) null else TextBlock(Rect(box), text)
        }
    }

    fun close() {
        synchronized(recognisers) {
            recognisers.values.forEach { it.close() }
            recognisers.clear()
        }
    }
}

private suspend fun Task<Text>.await(): Text = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(PageTranslationException("OCR failed: ${it.message}", it)) }
    addOnCanceledListener { cont.cancel() }
}
