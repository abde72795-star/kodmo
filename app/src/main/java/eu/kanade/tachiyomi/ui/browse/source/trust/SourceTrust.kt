package eu.kanade.tachiyomi.ui.browse.source.trust

// KMK -->
/**
 * Pure rules for the user-controlled "source trust" list.
 *
 * A trusted source may be used by global search, latest-chapter checks and chapter counts.
 * This does NOT relax extension signature/security checks; it only says which installed sources
 * the app is allowed to query for those features. Everything is trusted by default.
 */
object SourceTrust {

    fun isTrusted(sourceId: Long, untrusted: Set<String>): Boolean = sourceId.toString() !in untrusted

    /** Trusts [ids] except those protected by [excluded] (bulk exclusions). */
    fun trust(untrusted: Set<String>, ids: Collection<Long>, excluded: Set<String>): Set<String> {
        val toTrust = ids.map { it.toString() }.filter { it !in excluded }.toSet()
        return untrusted - toTrust
    }

    fun untrust(untrusted: Set<String>, ids: Collection<Long>): Set<String> {
        return untrusted + ids.map { it.toString() }
    }

    /** Ids a bulk selection may pick: excluded sources are never selected by "first N" / "all". */
    fun selectable(ids: List<Long>, excluded: Set<String>, limit: Int = Int.MAX_VALUE): Set<Long> {
        return ids.filter { it.toString() !in excluded }.take(limit).toSet()
    }
}
// KMK <--
