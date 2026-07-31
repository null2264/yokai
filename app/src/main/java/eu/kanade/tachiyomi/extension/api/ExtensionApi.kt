package eu.kanade.tachiyomi.extension.api

import android.content.Context
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.LoadResult
import eu.kanade.tachiyomi.extension.util.ExtensionLoader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.system.withIOContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import okio.BufferedSource
import okio.buffer
import okio.gzip
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import yokai.domain.extension.repo.interactor.GetExtensionRepo
import yokai.domain.extension.repo.interactor.UpdateExtensionRepo
import yokai.domain.extension.repo.model.ExtensionRepo

internal class ExtensionApi {

    private val networkService: NetworkHelper by injectLazy()
    private val getExtensionRepo: GetExtensionRepo by injectLazy()
    private val updateExtensionRepo: UpdateExtensionRepo by injectLazy()
    private val protoBuf: ProtoBuf by injectLazy()

    suspend fun findExtensions(): List<Extension.Available> {
        return withIOContext {
            getExtensionRepo.getAll()
                .map { async { getExtensions(it) } }
                .awaitAll()
                .flatten()
        }
    }

    private suspend fun getExtensions(
        repo: ExtensionRepo,
    ): List<Extension.Available> {
        val repoBaseUrl = repo.baseUrl
        return try {
            // Prefer protobuf index (Keiyoushi stubbed index.min.json for outdated apps)
            fetchProtobufExtensions(repoBaseUrl)
                ?.takeIf { it.isNotEmpty() }
                ?: fetchJsonExtensions(repoBaseUrl)
        } catch (e: Throwable) {
            Logger.e(e) { "Failed to get extensions from $repoBaseUrl" }
            emptyList()
        }
    }

    private suspend fun fetchProtobufExtensions(repoBaseUrl: String): List<Extension.Available>? {
        return try {
            val response = networkService.client
                .newCall(GET("$repoBaseUrl/index.pb"))
                .awaitSuccess()

            response.body.source().decompressIfGzipped().use { source ->
                protoBuf.decodeFromByteArray<ExtensionIndexDto>(source.readByteArray())
                    .extensionList
                    ?.extensions
                    .orEmpty()
                    .toAvailableExtensions(repoBaseUrl)
            }
        } catch (e: Throwable) {
            Logger.d(e) { "No protobuf index at $repoBaseUrl, falling back to JSON" }
            null
        }
    }

    private suspend fun fetchJsonExtensions(repoBaseUrl: String): List<Extension.Available> {
        val response = networkService.client
            .newCall(GET("$repoBaseUrl/index.min.json"))
            .awaitSuccess()

        return response
            .parseAs<List<ExtensionJsonObject>>()
            .toExtensions(repoBaseUrl)
    }

    suspend fun checkForUpdates(context: Context, prefetchedExtensions: List<Extension.Available>? = null): List<Extension.Available> {
        return withIOContext {
            val extensions = prefetchedExtensions ?: findExtensions()

            // Update extension repo details
            updateExtensionRepo.awaitAll()

            val extensionManager: ExtensionManager = Injekt.get()
            val installedExtensions = extensionManager.installedExtensionsFlow.value.ifEmpty {
                ExtensionLoader.loadExtensionAsync(context)
                    .filterIsInstance<LoadResult.Success>()
                    .map { it.extension }
            }

            val extensionsWithUpdate = mutableListOf<Extension.Available>()
            for (installedExt in installedExtensions) {
                val pkgName = installedExt.pkgName
                val availableExt = extensions.find { it.pkgName == pkgName } ?: continue
                val hasUpdatedVer = availableExt.versionCode > installedExt.versionCode
                val hasUpdatedLib = availableExt.libVersion > installedExt.libVersion
                val hasUpdate = hasUpdatedVer || hasUpdatedLib
                if (hasUpdate) {
                    extensionsWithUpdate.add(availableExt)
                }
            }

            extensionsWithUpdate
        }
    }

    private fun List<ExtensionIndexDto.Extension>.toAvailableExtensions(repoUrl: String): List<Extension.Available> {
        return this
            .mapNotNull { ext ->
                val libVersion = ext.extensionLib.toDoubleOrNull()
                    ?: ext.versionName.substringBeforeLast('.').toDoubleOrNull()
                    ?: return@mapNotNull null
                if (libVersion < ExtensionLoader.LIB_VERSION_MIN || libVersion > ExtensionLoader.LIB_VERSION_MAX) {
                    return@mapNotNull null
                }
                val resources = ext.resources ?: return@mapNotNull null
                val apkName = resources.apkUrl.substringAfterLast('/')
                if (apkName.isEmpty()) return@mapNotNull null

                val languages = ext.sources.map { it.language }.toSet()
                Extension.Available(
                    name = ext.name,
                    pkgName = ext.packageName,
                    versionName = ext.versionName,
                    versionCode = ext.versionCode,
                    libVersion = libVersion,
                    lang = if (languages.size == 1) languages.first() else "all",
                    isNsfw = ext.contentWarning >= ExtensionIndexDto.ContentWarning.MIXED,
                    sources = ext.sources.map { source ->
                        Extension.AvailableSource(
                            name = source.name,
                            id = source.id,
                            lang = source.language,
                            baseUrl = source.homeUrl,
                        )
                    },
                    apkName = apkName,
                    iconUrl = resources.iconUrl.ifEmpty { "$repoUrl/icon/${ext.packageName}.png" },
                    repoUrl = repoUrl,
                )
            }
    }

    private fun List<ExtensionJsonObject>.toExtensions(repoUrl: String): List<Extension.Available> {
        return this
            .filter {
                val libVersion = it.extractLibVersion()
                libVersion >= ExtensionLoader.LIB_VERSION_MIN && libVersion <= ExtensionLoader.LIB_VERSION_MAX
            }
            .map {
                Extension.Available(
                    name = it.name.substringAfter("Tachiyomi: "),
                    pkgName = it.pkg,
                    versionName = it.version,
                    versionCode = it.code,
                    libVersion = it.extractLibVersion(),
                    lang = it.lang,
                    isNsfw = it.nsfw == 1,
                    sources = it.sources ?: emptyList(),
                    apkName = it.apk,
                    iconUrl = "$repoUrl/icon/${it.pkg}.png",
                    repoUrl = repoUrl,
                )
            }
    }

    fun getApkUrl(extension: ExtensionManager.ExtensionInfo): String {
        return "${extension.repoUrl}/apk/${extension.apkName}"
    }

    private fun ExtensionJsonObject.extractLibVersion(): Double {
        return version.substringBeforeLast('.').toDouble()
    }

    private fun BufferedSource.decompressIfGzipped(): BufferedSource {
        val isGzip = peek().use { peeked ->
            try {
                peeked.readShort().toInt() and 0xFFFF == 0x1f8b
            } catch (_: Exception) {
                false
            }
        }
        return if (isGzip) gzip().buffer() else this
    }
}

@Serializable
private data class ExtensionJsonObject(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int,
    val hasReadme: Int = 0,
    val hasChangelog: Int = 0,
    val sources: List<Extension.AvailableSource>?,
)
