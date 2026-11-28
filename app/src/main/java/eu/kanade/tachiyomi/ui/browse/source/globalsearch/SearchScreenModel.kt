package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.group.GroupSort
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.group.LastChapterTracker
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.group.SearchGrouping
import eu.kanade.tachiyomi.ui.browse.source.trust.SourceTrust
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.preference.toggle
import tachiyomi.core.common.util.QuerySanitizer.sanitize
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

abstract class SearchScreenModel(
    initialState: State = State(),
    sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val preferences: SourcePreferences = Injekt.get(),
) : StateScreenModel<SearchScreenModel.State>(initialState) {

    private val coroutineDispatcher = Dispatchers.IO.limitedParallelism(5)
    private var searchJob: Job? = null

    private val enabledLanguages = sourcePreferences.enabledLanguages().get()
    private val disabledSources = sourcePreferences.disabledSources().get()
    protected val pinnedSources = sourcePreferences.pinnedSources().get()

    // KMK -->
    private val untrustedSources = sourcePreferences.untrustedSources().get()
    // KMK <--

    private var lastQuery: String? = null
    private var lastSourceFilter: SourceFilter? = null

    protected var extensionFilter: String? = null

    open val sortComparator = { map: Map<Source, SearchItemResult> ->
        compareBy<Source>(
            { (map[it] as? SearchItemResult.Success)?.isEmpty ?: true },
            { "${it.id}" !in pinnedSources },
            { "${it.name.lowercase()} (${it.lang})" },
            // KMK -->
            // toSortedMap() drops keys that compare equal, so sources sharing a name and language need a unique tie-breaker
            { it.id },
            // KMK <--
        )
    }

    init {
        screenModelScope.launch {
            preferences.globalSearchFilterState().changes().collectLatest { state ->
                mutableState.update { it.copy(onlyShowHasResults = state) }
            }
        }
        // KMK -->
        screenModelScope.launch {
            preferences.globalSearchPinnedState().changes().collectLatest { state ->
                mutableState.update { it.copy(sourceFilter = state) }
            }
        }
        screenModelScope.launch {
            preferences.globalSearchRelevantOnly().changes().collectLatest { value ->
                mutableState.update { it.copy(relevantOnly = value) }
            }
        }
        screenModelScope.launch {
            preferences.globalSearchGroupSort().changes().collectLatest { sort ->
                mutableState.update { it.copy(groupSort = sort) }
            }
        }
        // KMK <--
    }

    // KMK -->
    private val requestedChapterKeys = HashSet<String>()
    private val chapterScope get() = ioCoroutineScope

    fun toggleRelevantOnly() {
        preferences.globalSearchRelevantOnly().toggle()
    }

    /** Re-runs the search only for sources that failed, keeping every successful result. */
    fun retryFailed() {
        val query = lastQuery ?: return
        val failed = state.value.items.filter { it.value is SearchItemResult.Error }.keys.toList()
        if (failed.isEmpty()) return
        failed.forEach { updateItem(it, SearchItemResult.Loading) }
        ioCoroutineScope.launch {
            failed.map { source ->
                async {
                    try {
                        val page = withContext(coroutineDispatcher) {
                            source.getSearchManga(1, query.sanitize(), source.getFilterList())
                        }
                        val titles = page.mangas
                            .map { it.toDomainManga(source.id) }
                            .distinctBy { it.url }
                            .let { networkToLocalManga(it) }
                        if (isActive) updateItem(source, SearchItemResult.Success(titles))
                    } catch (e: Exception) {
                        if (isActive) updateItem(source, SearchItemResult.Error(e))
                    }
                }
            }.awaitAll()
        }
    }

    fun setGroupSort(sort: GroupSort) {
        preferences.globalSearchGroupSort().set(sort)
    }

    /** Fetches the latest chapter of every grouped entry, a few at a time. Failing sources are skipped. */
    fun fetchLastChapters(force: Boolean = false) {
        if (!state.value.grouped) return
        val groups = SearchGrouping.group(
            state.value.items,
            state.value.searchedQuery.takeIf { state.value.relevantOnly },
        )
        for (entry in groups.flatMap { it.entries }) {
            val key = LastChapterTracker.key(entry.source.id, entry.manga.url)
            if (force) {
                requestedChapterKeys.add(key)
            } else if (!requestedChapterKeys.add(key)) {
                continue
            }
            chapterScope.launch {
                LastChapterTracker.refresh(entry.source, entry.manga, force)
            }
        }
    }
    // KMK <--

    @Composable
    fun getManga(initialManga: Manga): androidx.compose.runtime.State<Manga> {
        return produceState(initialValue = initialManga) {
            getManga.subscribe(initialManga.url, initialManga.source)
                .filterNotNull()
                .collectLatest { manga ->
                    value = manga
                }
        }
    }

    open fun getEnabledSources(): List<Source> {
        return sourceManager.getVisibleSources()
            .filter { it.lang in enabledLanguages && "${it.id}" !in disabledSources }
            // KMK -->
            .filter { SourceTrust.isTrusted(it.id, untrustedSources) }
            // KMK <--
            .sortedWith(
                compareBy(
                    { "${it.id}" !in pinnedSources },
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )
    }

    // KMK -->
    fun hasPinnedSources(): Boolean = getEnabledSources().any { "${it.id}" in pinnedSources }

    fun shouldPinnedSourcesHidden() {
        if (!hasPinnedSources()) {
            preferences.globalSearchPinnedState().set(SourceFilter.All)
        }
    }
    // KMK <--

    private fun getSelectedSources(): List<Source> {
        val enabledSources = getEnabledSources()

        val filter = extensionFilter
        if (filter.isNullOrEmpty()) {
            return enabledSources
        }

        // SY -->
        val filteredSourceIds = extensionManager.installedExtensionsFlow.value
            .filter { it.pkgName == filter }
            .flatMap { it.sources }
            .map { it.id }
        return enabledSources.filter { it.id in filteredSourceIds }
        // SY <--
    }

    fun updateSearchQuery(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun setSourceFilter(filter: SourceFilter) {
        preferences.globalSearchPinnedState().set(filter)
        search()
    }

    fun toggleFilterResults() {
        preferences.globalSearchFilterState().toggle()
    }

    fun search() {
        val query = state.value.searchQuery
        val sourceFilter = state.value.sourceFilter

        if (query.isNullOrBlank()) return

        val sameQuery = this.lastQuery == query
        if (sameQuery && this.lastSourceFilter == sourceFilter) return

        this.lastQuery = query
        this.lastSourceFilter = sourceFilter
        // KMK -->
        mutableState.update { it.copy(searchedQuery = query) }
        // KMK <--

        searchJob?.cancel()
        // KMK -->
        if (!sameQuery) requestedChapterKeys.clear()
        // KMK <--

        val sources = getSelectedSources()

        // Reuse previous results if possible
        if (sameQuery) {
            val existingResults = state.value.items
            updateItems(
                sources
                    .associateWith { existingResults[it] ?: SearchItemResult.Loading }
                    .toPersistentMap(),
            )
        } else {
            updateItems(
                sources
                    .associateWith { SearchItemResult.Loading }
                    .toPersistentMap(),
            )
        }

        searchJob = ioCoroutineScope.launch {
            sources.map { source ->
                async {
                    if (state.value.items[source] !is SearchItemResult.Loading) {
                        return@async
                    }

                    try {
                        val page = withContext(coroutineDispatcher) {
                            source.getSearchManga(1, query.sanitize(), source.getFilterList())
                        }

                        val titles = page.mangas
                            .map { it.toDomainManga(source.id) }
                            .distinctBy { it.url }
                            .let { networkToLocalManga(it) }

                        if (isActive) {
                            updateItem(source, SearchItemResult.Success(titles))
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            updateItem(source, SearchItemResult.Error(e))
                        }
                    }
                }
            }
                .awaitAll()
        }
    }

    private fun updateItems(items: Map<Source, SearchItemResult>) {
        mutableState.update {
            it.copy(
                items = items
                    .toSortedMap(sortComparator(items))
                    .toPersistentMap(),
            )
        }
    }

    private fun updateItem(source: Source, result: SearchItemResult) {
        mutableState.update { currentState ->
            val newItems = currentState.items + (source to result)
            currentState.copy(
                items = newItems
                    .toSortedMap(sortComparator(newItems))
                    .toPersistentMap(),
            )
        }
    }

    fun setMigrateDialog(currentId: Long, target: Manga) {
        screenModelScope.launchIO {
            val current = getManga.await(currentId) ?: return@launchIO
            mutableState.update { it.copy(dialog = Dialog.Migrate(target, current)) }
        }
    }

    fun clearDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    @Immutable
    data class State(
        val from: Manga? = null,
        val searchQuery: String? = null,
        val sourceFilter: SourceFilter = SourceFilter.PinnedOnly,
        val onlyShowHasResults: Boolean = false,
        val items: PersistentMap<Source, SearchItemResult> = persistentMapOf(),
        val dialog: Dialog? = null,
        // KMK -->
        val grouped: Boolean = true,
        val relevantOnly: Boolean = true,
        val searchedQuery: String? = null,
        val groupSort: GroupSort = GroupSort.Title,
        // KMK <--
    ) {
        val progress: Int = items.count { it.value !is SearchItemResult.Loading }
        val total: Int = items.size
        val filteredItems = items.filter { (_, result) -> result.isVisible(onlyShowHasResults) }
            .toImmutableMap()
    }

    sealed interface Dialog {
        data class Migrate(val target: Manga, val current: Manga) : Dialog
    }
}

enum class SourceFilter {
    All,
    PinnedOnly,
}

sealed interface SearchItemResult {
    data object Loading : SearchItemResult

    data class Error(
        val throwable: Throwable,
    ) : SearchItemResult

    data class Success(
        val result: List<Manga>,
    ) : SearchItemResult {
        val isEmpty: Boolean
            get() = result.isEmpty()
    }

    fun isVisible(onlyShowHasResults: Boolean): Boolean {
        return !onlyShowHasResults || (this is Success && !this.isEmpty)
    }
}
