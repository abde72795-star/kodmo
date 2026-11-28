package eu.kanade.presentation.browse.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.group.LastChapterTracker
import eu.kanade.tachiyomi.ui.browse.source.trust.SourceTrust
import kotlinx.coroutines.delay
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
internal fun InLibraryBadge(enabled: Boolean) {
    if (enabled) {
        Badge(
            imageVector = Icons.Outlined.CollectionsBookmark,
        )
    }
}

// KMK -->
/**
 * Total chapters the browsed source lists for [manga].
 *
 * Order of data: chapters already in the database, then the shared latest-chapter cache, then one
 * throttled request (max 3 at once, only for cards that stay on screen, cached for hours).
 * Nothing is shown while the count is unknown.
 */
@Composable
internal fun ChapterCountBadge(manga: Manga) {
    val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
    val enabled by sourcePreferences.showBrowseChapterCount().collectAsState()
    if (!enabled) return

    val key = remember(manga.source, manga.url) { LastChapterTracker.key(manga.source, manga.url) }
    var dbCount by remember(manga.id) { mutableStateOf(0) }
    val entries by LastChapterTracker.entries.collectAsState()
    val fetched by remember(key) { derivedStateOf { entries[key]?.count } }

    LaunchedEffect(key) {
        val known = Injekt.get<GetChaptersByMangaId>().await(manga.id).size
        if (known > 0) {
            dbCount = known
            return@LaunchedEffect
        }
        if (fetched != null && LastChapterTracker.isFresh(LastChapterTracker.get(manga.source, manga.url))) {
            return@LaunchedEffect
        }
        if (!SourceTrust.isTrusted(manga.source, sourcePreferences.untrustedSources().get())) return@LaunchedEffect
        val source = Injekt.get<SourceManager>().get(manga.source) ?: return@LaunchedEffect
        // Cards scrolled past quickly are cancelled here and never hit the network.
        delay(400)
        LastChapterTracker.refresh(source, manga)
    }

    val count = if (dbCount > 0) dbCount else fetched
    if (count != null && count > 0) {
        Badge(
            text = count.toString(),
            color = MaterialTheme.colorScheme.primary,
            textColor = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
// KMK <--
