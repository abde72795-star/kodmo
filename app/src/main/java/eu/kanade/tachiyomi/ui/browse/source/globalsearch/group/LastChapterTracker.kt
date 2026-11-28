package eu.kanade.tachiyomi.ui.browse.source.globalsearch.group

import android.app.Application
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.concurrent.ConcurrentHashMap

// KMK -->
/** Cached latest-chapter result for one title on one source. */
data class LastChapterEntry(
    val chapter: LastChapter?,
    val fetchedAt: Long,
    val failed: Boolean,
)

/**
 * Fetches and caches the latest chapter of titles, a few at a time.
 *
 * - Results are cached in memory and on disk; successes are reused for [SUCCESS_TTL_MS],
 *   failures are retried after [FAILURE_TTL_MS] so a broken source never blocks the others.
 * - A failing source keeps its previous successful value (marked stale) instead of being wiped.
 * - Concurrent requests for the same title share one network call.
 */
object LastChapterTracker {

    const val SUCCESS_TTL_MS = 6L * 60 * 60 * 1000
    const val FAILURE_TTL_MS = 15L * 60 * 1000
    private const val MAX_ENTRIES = 3000

    private val limiter = Semaphore(3)
    private val inFlight = ConcurrentHashMap<String, Deferred<Unit>>()
    private val _entries = MutableStateFlow<Map<String, LastChapterEntry>>(emptyMap())
    val entries: StateFlow<Map<String, LastChapterEntry>> = _entries

    @Volatile private var loaded = false

    fun key(sourceId: Long, mangaUrl: String) = "$sourceId:$mangaUrl"

    fun isFresh(entry: LastChapterEntry?, now: Long = System.currentTimeMillis()): Boolean {
        entry ?: return false
        val ttl = if (entry.failed) FAILURE_TTL_MS else SUCCESS_TTL_MS
        return now - entry.fetchedAt < ttl
    }

    fun get(sourceId: Long, mangaUrl: String): LastChapterEntry? {
        ensureLoaded()
        return _entries.value[key(sourceId, mangaUrl)]
    }

    /** Fetches the latest chapter unless a fresh cached value exists (or [force]). Never throws except on cancellation. */
    suspend fun refresh(source: Source, manga: Manga, force: Boolean = false) {
        ensureLoaded()
        val key = key(source.id, manga.url)
        if (!force && isFresh(_entries.value[key])) return

        coroutineScope {
            val job = inFlight.getOrPut(key) {
                async {
                    try {
                        limiter.withPermit { fetch(source, manga, key) }
                    } finally {
                        inFlight.remove(key)
                    }
                }
            }
            job.await()
        }
    }

    private suspend fun fetch(source: Source, manga: Manga, key: String) {
        val now = System.currentTimeMillis()
        val result = try {
            val update = source.getMangaUpdate(manga.toSManga(), emptyList(), false, true)
            val items = update.chapters.map { LastChapterResolver.Item(it.name, it.chapter_number) }
            LastChapterEntry(LastChapterResolver.resolve(manga.title, items), now, failed = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val previous = _entries.value[key]?.chapter
            LastChapterEntry(previous, now, failed = true)
        }
        _entries.update { current ->
            val next = current + (key to result)
            if (next.size > MAX_ENTRIES) {
                next.entries.sortedByDescending { it.value.fetchedAt }.take(MAX_ENTRIES).associate { it.key to it.value }
            } else {
                next
            }
        }
        persist()
    }

    // region disk cache
    private fun file() = File(Injekt.get<Application>().cacheDir, "last_chapters.tsv")

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching {
            val f = file()
            if (!f.exists()) return@runCatching
            val map = HashMap<String, LastChapterEntry>()
            f.forEachLine { line ->
                val p = line.split('\t', limit = 5)
                if (p.size < 5) return@forEachLine
                val number = p[3].toFloatOrNull()
                val chapter = if (p[4].isEmpty()) null else LastChapter(number, p[4])
                map[p[0]] = LastChapterEntry(chapter, p[1].toLongOrNull() ?: 0L, p[2] == "1")
            }
            _entries.update { map + it }
        }
    }

    @Synchronized
    private fun persist() {
        runCatching {
            val text = _entries.value.entries.joinToString("\n") { (k, v) ->
                val label = v.chapter?.label.orEmpty().replace('\t', ' ').replace('\n', ' ')
                "${k.replace('\t', ' ').replace('\n', ' ')}\t${v.fetchedAt}\t${if (v.failed) 1 else 0}\t${v.chapter?.number ?: ""}\t$label"
            }
            file().writeText(text)
        }
    }
    // endregion
}
// KMK <--
