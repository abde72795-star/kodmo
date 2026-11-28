package eu.kanade.tachiyomi.ui.browse.globalsearch

import eu.kanade.tachiyomi.ui.browse.source.globalsearch.group.LastChapterResolver
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.group.LastChapterResolver.Item
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.group.TitleNormalizer
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

// KMK -->
class LastChapterLogicTest {

    @Test
    fun `identical titles normalize equal`() {
        TitleNormalizer.normalize("One-Punch Man!") shouldBe TitleNormalizer.normalize("one punch  man")
        TitleNormalizer.normalize("Café") shouldBe TitleNormalizer.normalize("cafe")
    }

    @Test
    fun `similar titles stay different`() {
        (TitleNormalizer.normalize("Solo Leveling") == TitleNormalizer.normalize("Solo Leveling Ragnarok")) shouldBe false
        (TitleNormalizer.normalize("Title 2") == TitleNormalizer.normalize("Title")) shouldBe false
    }

    @Test
    fun `decimal chapter wins over base`() {
        val result = LastChapterResolver.resolve("X", listOf(Item("Chapter 10", 10f), Item("Chapter 10.5", 10.5f)))
        result?.label shouldBe "10.5"
    }

    @Test
    fun `unnumbered chapters fall back to name`() {
        val result = LastChapterResolver.resolve("X", listOf(Item("Special", -1f)))
        result?.label shouldBe "Special"
        result?.number shouldBe null
    }

    @Test
    fun `empty list gives null`() {
        LastChapterResolver.resolve("X", emptyList()) shouldBe null
    }
}
// KMK <--
