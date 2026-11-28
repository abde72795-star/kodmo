package eu.kanade.tachiyomi.ui.browse.source.globalsearch.group

import java.text.Normalizer

// KMK -->
/**
 * Conservative title normalizer used to group *identical* works across sources.
 *
 * Only case, accents, punctuation and spacing are ignored. Titles that merely look similar
 * (different words, extra subtitles, other numbers) stay in separate groups on purpose.
 */
object TitleNormalizer {

    private val combiningMarks = Regex("\\p{M}+")
    private val nonAlphanumeric = Regex("[^\\p{L}\\p{N}]+")

    fun normalize(title: String): String {
        return Normalizer.normalize(title, Normalizer.Form.NFKD)
            .replace(combiningMarks, "")
            .lowercase()
            .replace(nonAlphanumeric, " ")
            .trim()
    }
}
// KMK <--
