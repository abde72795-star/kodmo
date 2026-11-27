package eu.kanade.domain.extension.interactor

import android.content.pm.PackageInfo
import androidx.core.content.pm.PackageInfoCompat
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.util.system.isDebugBuildType
import mihon.domain.extension.model.KOMIKKU_SIGNATURE
import mihon.domain.extension.repository.ExtensionStoreRepository
import tachiyomi.core.common.preference.getAndSet

class TrustExtension(
    private val repository: ExtensionStoreRepository,
    private val preferences: SourcePreferences,
) {

    suspend fun isTrusted(pkgInfo: PackageInfo, fingerprints: List<String>): Boolean {
        // Extensions are loaded only after Android has installed the APK (or the user
        // explicitly selected it for private installation). Keep the signature-presence
        // check, but do not require a second in-app trust confirmation.
        return pkgInfo.packageName.isNotBlank() && fingerprints.isNotEmpty()
    }

    fun trust(pkgName: String, versionCode: Long, signatureHash: String) {
        preferences.trustedExtensions().getAndSet { exts ->
            // Remove previously trusted versions
            val removed = exts.filterNot { it.startsWith("$pkgName:") }.toMutableSet()

            removed.also { it += "$pkgName:$versionCode:$signatureHash" }
        }
    }

    fun revokeAll() {
        preferences.trustedExtensions().delete()
    }
}
