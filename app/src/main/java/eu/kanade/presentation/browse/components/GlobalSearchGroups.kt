package eu.kanade.presentation.browse.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.group.GroupEntry
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.group.GroupSort
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.group.LastChapter
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.group.LastChapterEntry
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.group.LastChapterTracker
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.group.SearchGroup
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.group.SearchGrouping
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import java.text.DateFormat
import java.util.Date

// KMK -->
@Composable
fun GlobalSearchGroupToolbar(
    grouped: Boolean,
    sort: GroupSort,
    onToggleGrouped: () -> Unit,
    onSortChange: (GroupSort) -> Unit,
    onRefresh: () -> Unit,
) {
    val entries by LastChapterTracker.entries.collectAsState()
    val lastUpdated = entries.values.maxOfOrNull { it.fetchedAt }
    Column(modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = grouped,
                onClick = onToggleGrouped,
                label = { Text(stringResource(KMR.strings.kmk_group_results)) },
            )
            if (grouped) {
                FilterChip(
                    selected = sort == GroupSort.Title,
                    onClick = { onSortChange(GroupSort.Title) },
                    label = { Text(stringResource(KMR.strings.kmk_sort_title)) },
                )
                FilterChip(
                    selected = sort == GroupSort.LatestChapter,
                    onClick = { onSortChange(GroupSort.LatestChapter) },
                    label = { Text(stringResource(KMR.strings.kmk_sort_latest_chapter)) },
                )
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = stringResource(KMR.strings.kmk_refresh_chapters),
                    )
                }
            }
        }
        if (grouped && lastUpdated != null) {
            Text(
                text = stringResource(
                    KMR.strings.kmk_chapters_updated_at,
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(lastUpdated)),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun GlobalSearchGroupedList(
    groups: List<SearchGroup>,
    sort: GroupSort,
    contentPadding: PaddingValues,
    onClickItem: (Manga) -> Unit,
) {
    val entries by LastChapterTracker.entries.collectAsState()
    val lookup: (GroupEntry) -> LastChapterEntry? = {
        entries[LastChapterTracker.key(it.source.id, it.manga.url)]
    }
    val sorted = SearchGrouping.sort(groups, sort) { lookup(it)?.chapter }

    LazyColumn(contentPadding = contentPadding) {
        items(sorted, key = { it.key }) { group ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = MaterialTheme.padding.medium,
                        vertical = MaterialTheme.padding.small,
                    ),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
            ) {
                MangaCover.Book(
                    data = group.cover.asMangaCover(),
                    modifier = Modifier.width(64.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    group.entries.forEach { entry ->
                        val cached = lookup(entry)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onClickItem(entry.manga) }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${entry.source.name} (${LocaleHelper.getLocalizedDisplayName(entry.source.lang)})",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = chapterText(cached),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.padding(start = MaterialTheme.padding.small),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun chapterText(entry: LastChapterEntry?): String {
    val chapter: LastChapter? = entry?.chapter
    return when {
        entry == null -> stringResource(KMR.strings.kmk_latest_chapter_loading)
        chapter == null -> stringResource(KMR.strings.kmk_latest_chapter_unknown)
        else -> {
            val text = if (chapter.number != null) {
                stringResource(KMR.strings.kmk_latest_chapter, chapter.label)
            } else {
                chapter.label
            }
            if (entry.failed) stringResource(KMR.strings.kmk_latest_chapter_stale, text) else text
        }
    }
}
// KMK <--
