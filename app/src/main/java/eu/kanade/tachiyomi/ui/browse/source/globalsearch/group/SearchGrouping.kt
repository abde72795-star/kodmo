package eu.kanade.tachiyomi.ui.browse.source.globalsearch.group

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchItemResult
import tachiyomi.domain.manga.model.Manga

// KMK -->
data class GroupEntry(val source: Source, val manga: Manga)

data class SearchGroup(
    val key: String,
    val title: String,
    val entries: List<GroupEntry>,
) {
    val cover: Manga get() = entries.first().manga
}

enum class GroupSort { Title, LatestChapter }

object SearchGrouping {

    /** Groups results whose normalized titles are identical. Source names stay on every entry. */
    fun group(items: Map<Source, SearchItemResult>, query: String? = null): List<SearchGroup> {
        val tokens = query?.let { TitleNormalizer.normalize(it) }?.split(' ')?.filter { it.isNotEmpty() }.orEmpty()
        val groups = LinkedHashMap<String, MutableList<GroupEntry>>()
        val titles = HashMap<String, String>()
        for ((source, result) in items) {
            if (result !is SearchItemResult.Success) continue
            for (manga in result.result) {
                if (tokens.isNotEmpty() && !isRelevant(tokens, manga.title)) continue
                val key = TitleNormalizer.normalize(manga.title).ifEmpty { manga.title }
                groups.getOrPut(key) { mutableListOf() }.add(GroupEntry(source, manga))
                titles.putIfAbsent(key, manga.title)
            }
        }
        return groups.map { (key, entries) -> SearchGroup(key, titles.getValue(key), entries) }
    }

    /** A title is relevant when it contains every word of the query (accents/case/punctuation ignored). */
    fun isRelevant(queryTokens: List<String>, title: String): Boolean {
        val normalized = TitleNormalizer.normalize(title)
        return queryTokens.all { normalized.contains(it) }
    }

    fun bestChapter(group: SearchGroup, lookup: (GroupEntry) -> LastChapter?): Float? {
        return group.entries.mapNotNull { lookup(it)?.number }.maxOrNull()
    }

    fun sort(
        groups: List<SearchGroup>,
        sort: GroupSort,
        lookup: (GroupEntry) -> LastChapter?,
    ): List<SearchGroup> {
        return when (sort) {
            GroupSort.Title -> groups.sortedBy { it.title.lowercase() }
            GroupSort.LatestChapter -> groups.sortedWith(
                compareByDescending<SearchGroup> { bestChapter(it, lookup) ?: Float.NEGATIVE_INFINITY }
                    .thenBy { it.title.lowercase() },
            )
        }
    }
}
// KMK <--
