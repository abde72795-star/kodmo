package eu.kanade.tachiyomi.ui.reader.translate

import android.graphics.Rect

/** A recognised piece of text (usually a speech bubble or caption) in image coordinates. */
data class TextBlock(
    val box: Rect,
    val text: String,
)

class TranslatedBlock(
    val box: Rect,
    val original: String,
    val translated: String,
)

/** Raised for failures with a message that can be shown to the reader user. */
class PageTranslationException(message: String, cause: Throwable? = null) : Exception(message, cause)
