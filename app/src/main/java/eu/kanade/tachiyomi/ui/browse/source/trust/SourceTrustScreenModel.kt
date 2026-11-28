package eu.kanade.tachiyomi.ui.browse.source.trust

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK -->
class SourceTrustScreenModel(
    private val sourceManager: SourceManager = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val preferences: SourcePreferences = Injekt.get(),
) : StateScreenModel<SourceTrustScreenModel.State>(State()) {

    init {
        screenModelScope.launch {
            val rows = withIOContext {
                val extensionNames = HashMap<Long, String>()
                extensionManager.installedExtensionsFlow.value.forEach { extension ->
                    extension.sources.forEach { extensionNames[it.id] = extension.name }
                }
                sourceManager.getVisibleSources()
                    .sortedWith(compareBy({ it.name.lowercase() }, { it.lang }, { it.id }))
                    .map { Row(it.id, it.name, it.lang, extensionNames[it.id]) }
            }
            mutableState.update { it.copy(rows = rows, loading = false) }
        }
        screenModelScope.launch {
            preferences.untrustedSources().changes().collect { value ->
                mutableState.update { it.copy(untrusted = value) }
            }
        }
        screenModelScope.launch {
            preferences.trustBulkExcluded().changes().collect { value ->
                mutableState.update { it.copy(excluded = value) }
            }
        }
    }

    fun setQuery(query: String?) = mutableState.update { it.copy(query = query) }

    fun toggleSelect(id: Long) = mutableState.update {
        it.copy(selection = if (id in it.selection) it.selection - id else it.selection + id)
    }

    fun selectFirst(count: Int) = mutableState.update {
        it.copy(selection = SourceTrust.selectable(it.filtered.map { row -> row.id }, it.excluded, count))
    }

    fun selectAll() = selectFirst(Int.MAX_VALUE)

    fun clearSelection() = mutableState.update { it.copy(selection = emptySet()) }

    fun toggleTrust(id: Long) {
        val current = preferences.untrustedSources().get()
        preferences.untrustedSources().set(
            if (SourceTrust.isTrusted(id, current)) {
                SourceTrust.untrust(current, listOf(id))
            } else {
                current - id.toString()
            },
        )
    }

    fun toggleExcluded(id: Long) {
        val current = preferences.trustBulkExcluded().get()
        val key = id.toString()
        preferences.trustBulkExcluded().set(if (key in current) current - key else current + key)
    }

    fun trustSelected() {
        val s = state.value
        preferences.untrustedSources().set(SourceTrust.trust(s.untrusted, s.selection, s.excluded))
    }

    fun untrustSelected() {
        val s = state.value
        preferences.untrustedSources().set(SourceTrust.untrust(s.untrusted, s.selection))
    }

    fun resetToDefaults() {
        preferences.untrustedSources().set(emptySet())
        preferences.trustBulkExcluded().set(emptySet())
        clearSelection()
    }

    @Immutable
    data class Row(val id: Long, val name: String, val lang: String, val extension: String?)

    @Immutable
    data class State(
        val loading: Boolean = true,
        val rows: List<Row> = emptyList(),
        val query: String? = null,
        val selection: Set<Long> = emptySet(),
        val untrusted: Set<String> = emptySet(),
        val excluded: Set<String> = emptySet(),
    ) {
        val filtered: List<Row> = query?.trim()?.takeIf { it.isNotEmpty() }?.let { q ->
            rows.filter {
                it.name.contains(q, ignoreCase = true) ||
                    it.lang.contains(q, ignoreCase = true) ||
                    it.extension?.contains(q, ignoreCase = true) == true
            }
        } ?: rows
        val trustedCount: Int = rows.count { SourceTrust.isTrusted(it.id, untrusted) }
    }
}
// KMK <--
