package dev.yokai.domain.extension.interactor

import android.content.pm.PackageInfo
import androidx.core.content.pm.PackageInfoCompat
import dev.yokai.domain.extension.repo.ExtensionRepoRepository
import dev.yokai.domain.source.SourcePreferences
import eu.kanade.tachiyomi.core.preference.getAndSet
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TrustExtension(
    private val extensionRepoRepository: ExtensionRepoRepository,
    private val sourcePreferences: SourcePreferences,
) {
    suspend fun isTrustedByRepo(fingerprints: List<String>): Boolean {
        val trustedFingerprints = extensionRepoRepository.getAll().map { it.signingKeyFingerprint }.toHashSet()
        return trustedFingerprints.any { fingerprints.contains(it) }
    }

    suspend fun isTrusted(pkgInfo: PackageInfo, fingerprints: List<String>): Boolean {
        val key = "${pkgInfo.packageName}:${PackageInfoCompat.getLongVersionCode(pkgInfo)}:${fingerprints.last()}"
        return isTrustedByRepo(fingerprints) || key in sourcePreferences.trustedExtensions().get()
    }

    fun trust(pkgName: String, versionCode: Long, signatureHash: String) {
       sourcePreferences.trustedExtensions().getAndSet { exts ->
           val removed = exts.filterNot { it.startsWith("$pkgName:") }.toMutableSet()

           removed.also { it += "$pkgName:$versionCode:$signatureHash" }
       }
    }

    fun revokeAll() {
        sourcePreferences.trustedExtensions().delete()
    }
}