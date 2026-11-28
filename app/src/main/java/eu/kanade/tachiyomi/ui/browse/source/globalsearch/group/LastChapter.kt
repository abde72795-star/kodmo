package eu.kanade.tachiyomi.ui.browse.source.globalsearch.group

import tachiyomi.domain.chapter.service.ChapterRecognition

// KMK -->
/**
 * Latest chapter of a title on one source.
 *
 * @property number numeric chapter number, null when the source only exposes unnumbered chapters
 * (e.g. "Special", "Extra").
 * @property label text shown to the user, e.g. "10.5" or "Special".
 */
data class LastChapter(
    val number: Float?,
    val label: String,
)

object LastChapterResolver {

    /** One chapter as reported by a source. [number] is the source's own number (-1 when unknown). */
    data class Item(val name: String, val number: Float)

    /**
     * Picks the highest numbered chapter. Decimal chapters (10.5) win over their integer base (10).
     * When no chapter has a usable number the first listed chapter (sources list newest first) is used
     * and its name becomes the label.
     */
    fun resolve(mangaTitle: String, chapters: List<Item>): LastChapter? {
        if (chapters.isEmpty()) return null

        var best: Pair<Double, Item>? = null
        for (chapter in chapters) {
            val parsed = ChapterRecognition.parseChapterNumber(mangaTitle, chapter.name, chapter.number.toDouble())
            if (parsed < 0.0) continue
            if (best == null || parsed > best.first) best = parsed to chapter
        }

        val (number, chapter) = best ?: return chapters.first().let {
            LastChapter(number = null, label = it.name.trim().ifEmpty { "?" })
        }

        return if (isSpecialFraction(number)) {
            // "10 extra" style chapters are encoded as 10.99; show the real name instead of a fake number.
            LastChapter(number = number.toFloat(), label = chapter.name.trim().ifEmpty { format(number) })
        } else {
            LastChapter(number = number.toFloat(), label = format(number))
        }
    }

    fun format(number: Double): String {
        val rounded = Math.round(number * 100.0) / 100.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    }

    private fun isSpecialFraction(number: Double): Boolean {
        val fraction = Math.round((number - Math.floor(number)) * 100.0)
        return fraction in 97..99
    }
}
// KMK <--
