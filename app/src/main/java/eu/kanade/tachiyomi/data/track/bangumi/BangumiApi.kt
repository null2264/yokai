package eu.kanade.tachiyomi.data.track.bangumi

import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.bangumi.dto.BGMCollectionResponse
import eu.kanade.tachiyomi.data.track.bangumi.dto.BGMOAuth
import eu.kanade.tachiyomi.data.track.bangumi.dto.BGMSearchItem
import eu.kanade.tachiyomi.data.track.bangumi.dto.BGMSearchResult
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.system.withIOContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import uy.kohesive.injekt.injectLazy

class BangumiApi(
    private val trackId: Long,
    private val client: OkHttpClient,
    interceptor: BangumiInterceptor,
) {

    private val json: Json by injectLazy()

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    suspend fun addLibManga(track: Track): Track {
        return withIOContext {
            val body = FormBody.Builder()
                .add("rating", track.score.toInt().toString())
                .add("status", track.toApiStatus())
                .build()
            authClient.newCall(POST("$API_URL/collection/${track.media_id}/update", body = body))
                .awaitSuccess()
            track
        }
    }

    suspend fun updateLibManga(track: Track): Track {
        return withIOContext {
            // read status update
            val sbody = FormBody.Builder()
                .add("rating", track.score.toInt().toString())
                .add("status", track.toApiStatus())
                .build()
            authClient.newCall(POST("$API_URL/collection/${track.media_id}/update", body = sbody))
                .awaitSuccess()

            // chapter update
            val body = FormBody.Builder()
                .add("watched_eps", track.last_chapter_read.toInt().toString())
                .build()
            authClient.newCall(
                POST("$API_URL/subject/${track.media_id}/update/watched_eps", body = body),
            ).awaitSuccess()

            track
        }
    }

    suspend fun search(search: String): List<TrackSearch> {
        return withIOContext {
            val url = "$API_URL/search/subject/${URLEncoder.encode(search, StandardCharsets.UTF_8.name())}"
                .toUri()
                .buildUpon()
                .appendQueryParameter("type", "1")
                .appendQueryParameter("responseGroup", "large")
                .appendQueryParameter("max_results", "20")
                .build()
            with(json) {
                authClient.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<BGMSearchResult>()
                    .let { result ->
                        if (result.code == 404) emptyList<TrackSearch>()

                        result.list
                            ?.map { it.toTrackSearch(trackId) }
                            .orEmpty()
                    }
            }
        }
    }

    suspend fun findLibManga(track: Track): Track? {
        return withIOContext {
            with(json) {
                authClient.newCall(GET("$API_URL/subject/${track.media_id}"))
                    .awaitSuccess()
                    .parseAs<BGMSearchItem>()
                    .toTrackSearch(trackId)
            }
        }
    }

    suspend fun statusLibManga(track: Track): Track? {
        return withIOContext {
            val urlUserRead = "$API_URL/collection/${track.media_id}"
            val requestUserRead = Request.Builder()
                .url(urlUserRead)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .get()
                .build()

            // TODO: get user readed chapter here
            with(json) {
                authClient.newCall(requestUserRead)
                    .awaitSuccess()
                    .parseAs<BGMCollectionResponse>()
                    .let {
                        if (it.code == 400) return@let null

                        track.status = it.status?.id?.toInt() ?: Bangumi.DEFAULT_STATUS
                        track.last_chapter_read = it.epStatus!!.toFloat()
                        track.score = it.rating!!.toFloat()
                        track
                    }
            }
        }
    }

    suspend fun accessToken(code: String): BGMOAuth {
        return withIOContext {
            with(json) {
                client.newCall(accessTokenRequest(code))
                    .awaitSuccess()
                    .parseAs()
            }
        }
    }

    private fun accessTokenRequest(code: String) = POST(
        OAUTH_URL,
        body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .add("code", code)
            .add("redirect_uri", REDIRECT_URL)
            .build(),
    )

    companion object {
        private const val CLIENT_ID = "bgm291865b0b16054d89"
        private const val CLIENT_SECRET = "4edfce12a760731c3d497603c2480c21"

        private const val API_URL = "https://api.bgm.tv"
        private const val OAUTH_URL = "https://bgm.tv/oauth/access_token"
        private const val LOGIN_URL = "https://bgm.tv/oauth/authorize"

        private const val REDIRECT_URL = "yokai://bangumi-auth"
        private const val BASE_MANGA_URL = "$API_URL/mangas"

        fun mangaUrl(remoteId: Int): String {
            return "$BASE_MANGA_URL/$remoteId"
        }

        fun authUrl(): Uri =
            LOGIN_URL.toUri().buildUpon()
                .appendQueryParameter("client_id", CLIENT_ID)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", REDIRECT_URL)
                .build()

        fun refreshTokenRequest(token: String) = POST(
            OAUTH_URL,
            body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("refresh_token", token)
                .add("redirect_uri", REDIRECT_URL)
                .build(),
        )
    }
}
