package eu.kanade.tachiyomi.ui.browse.globalsearch

import eu.kanade.tachiyomi.ui.browse.source.globalsearch.group.LastChapterResolver
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.group.LastChapterResolver.Item
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.group.SearchGrouping
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.group.TitleNormalizer
import eu.kanade.tachiyomi.ui.browse.source.trust.SourceTrust
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
    fun `relevance requires every query word`() {
        val tokens = listOf("solo", "leveling")
        SearchGrouping.isRelevant(tokens, "Solo Leveling: Ragnarok") shouldBe true
        SearchGrouping.isRelevant(tokens, "Solo Max-Level Newbie") shouldBe false
    }

    @Test
    fun `trust all keeps bulk exclusions untouched`() {
        val untrusted = setOf("1", "2", "3")
        SourceTrust.trust(untrusted, listOf(1L, 2L, 3L), excluded = setOf("2")) shouldBe setOf("2")
    }

    @Test
    fun `untrust adds and selectable skips excluded`() {
        SourceTrust.untrust(emptySet(), listOf(5L)) shouldBe setOf("5")
        SourceTrust.selectable(listOf(1L, 2L, 3L, 4L), excluded = setOf("2"), limit = 2) shouldBe setOf(1L, 3L)
        SourceTrust.isTrusted(7L, setOf("7")) shouldBe false
        SourceTrust.isTrusted(8L, setOf("7")) shouldBe true
    }

    @Test
    fun `empty list gives null`() {
        LastChapterResolver.resolve("X", emptyList()) shouldBe null
    }
}
// KMK <--
