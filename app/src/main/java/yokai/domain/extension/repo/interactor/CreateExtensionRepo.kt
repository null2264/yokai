package yokai.domain.extension.repo.interactor

import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.OkHttpClient
import uy.kohesive.injekt.injectLazy
import yokai.domain.extension.repo.ExtensionRepoRepository
import yokai.domain.extension.repo.exception.SaveExtensionRepoException
import yokai.domain.extension.repo.model.ExtensionRepo
import yokai.domain.extension.repo.service.ExtensionRepoService

class CreateExtensionRepo(
    private val extensionRepoRepository: ExtensionRepoRepository
) {
    private val networkService: NetworkHelper by injectLazy()

    private val client: OkHttpClient
        get() = networkService.client

    private val extensionRepoService = ExtensionRepoService(client)

    suspend fun await(repoUrl: String): Result {
        val baseUrl = normalizeToBaseUrl(repoUrl) ?: return Result.InvalidUrl
        return extensionRepoService.fetchRepoDetails(baseUrl)?.let { insert(it) } ?: Result.InvalidUrl
    }

    /**
     * Accepts Keiyoushi/Mihon repo URLs in any of these forms and returns the repo base URL:
     * - `.../index.pb` (current Keiyoushi official URL)
     * - `.../index.min.json` (legacy)
     * - `.../repo.json`
     * - bare base URL (e.g. `https://raw.githubusercontent.com/keiyoushi/extensions/repo`)
     */
    private fun normalizeToBaseUrl(repoUrl: String): String? {
        val trimmed = repoUrl.trim()
            .substringBefore('?')
            .substringBefore('#')
            .toRawGithubusercontentIfNeeded()
            .trimEnd('/')
        if (!trimmed.startsWith("https://")) return null

        val base = when {
            trimmed.endsWith("/index.min.json") -> trimmed.removeSuffix("/index.min.json")
            trimmed.endsWith("/index.json") -> trimmed.removeSuffix("/index.json")
            trimmed.endsWith("/index.pb") -> trimmed.removeSuffix("/index.pb")
            trimmed.endsWith("/repo.json") -> trimmed.removeSuffix("/repo.json")
            else -> trimmed
        }.trimEnd('/')

        // Require at least https://host/path
        return base.takeIf { url -> url.count { c -> c == '/' } >= 3 }
    }

    /**
     * `https://github.com/{owner}/{repo}/raw/{ref}/{path}`
     * → `https://raw.githubusercontent.com/{owner}/{repo}/{ref}/{path}`
     */
    private fun String.toRawGithubusercontentIfNeeded(): String {
        val match = GITHUB_RAW_REGEX.matchEntire(this) ?: return this
        val (owner, repo, ref, path) = match.destructured
        return "https://raw.githubusercontent.com/$owner/$repo/$ref/$path"
    }

    private suspend fun insert(repo: ExtensionRepo): Result {
        return try {
            extensionRepoRepository.insertRepository(
                repo.baseUrl,
                repo.name,
                repo.shortName,
                repo.website,
                repo.signingKeyFingerprint,
            )
            Result.Success
        } catch (e: SaveExtensionRepoException) {
            Logger.e(e) { "SQL Conflict attempting to add new repository ${repo.baseUrl}" }
            return handleInsertionError(repo)
        }
    }

    /**
     * Error Handler for insert when there are trying to create new repositories
     *
     * SaveExtensionRepoException doesn't provide constraint info in exceptions.
     * First check if the conflict was on primary key. if so return RepoAlreadyExists
     * Then check if the conflict was on fingerprint. if so Return DuplicateFingerprint
     * If neither are found, there was some other Error, and return Result.Error
     *
     * @param repo Extension Repo holder for passing to DB/Error Dialog
     */
    @Suppress("ReturnCount")
    private suspend fun handleInsertionError(repo: ExtensionRepo): Result {
        val repoExists = extensionRepoRepository.getRepository(repo.baseUrl)
        if (repoExists != null) {
            return Result.RepoAlreadyExists
        }
        val matchingFingerprintRepo =
            extensionRepoRepository.getRepositoryBySigningKeyFingerprint(repo.signingKeyFingerprint)
        if (matchingFingerprintRepo != null) {
            return Result.DuplicateFingerprint(matchingFingerprintRepo, repo)
        }
        return Result.Error
    }

    sealed interface Result {
        data class DuplicateFingerprint(val oldRepo: ExtensionRepo, val newRepo: ExtensionRepo) : Result
        data object InvalidUrl : Result
        data object RepoAlreadyExists : Result
        data object Success : Result
        data object Error : Result
    }

    companion object {
        private val GITHUB_RAW_REGEX =
            Regex("""^https://github\.com/([^/]+)/([^/]+)/raw/([^/]+)/(.*)$""")
    }
}
