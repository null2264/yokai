package eu.kanade.tachiyomi.data.connections.discord

import androidx.annotation.StringRes
import eu.kanade.tachiyomi.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Constant for logging tag
const val RICH_PRESENCE_TAG = "discord_rpc"

// Constant for application id
private const val RICH_PRESENCE_APPLICATION_ID = "1229438545437921392"

// Constant for buttons list
private val RICH_PRESENCE_BUTTONS = null

// Constant for metadata list
private val RICH_PRESENCE_METADATA = null

@Serializable
data class Activity(
    @SerialName("application_id")
    val applicationId: String? = RICH_PRESENCE_APPLICATION_ID,
    val name: String? = null,
    val details: String? = null,
    val state: String? = null,
    val type: Int? = null,
    val timestamps: Timestamps? = null,
    val assets: Assets? = null,
    val buttons: List<String>? = RICH_PRESENCE_BUTTONS,
    val metadata: Metadata? = RICH_PRESENCE_METADATA,
) {
    @Serializable
    data class Assets(
        @SerialName("large_image")
        val largeImage: String? = null,
        @SerialName("large_text")
        val largeText: String? = null,
        @SerialName("small_image")
        val smallImage: String? = null,
        @SerialName("small_text")
        val smallText: String? = "TachiyomiSY",
    )

    @Serializable
    data class Metadata(
        @SerialName("button_urls")
        val buttonUrls: List<String>,
    )

    @Serializable
    data class Timestamps(
        val start: Long? = null,
        val stop: Long? = null,
    )
}

@Serializable
data class Presence(
    val activities: List<Activity> = listOf(),
    val afk: Boolean = true,
    val since: Long? = null,
    val status: String? = null,
) {
    @Serializable
    data class Response(
        val op: Long,
        val d: Presence,
    )
}

@Serializable
data class Identity(
    val token: String,
    val properties: Properties,
    val compress: Boolean,
    val intents: Long,
) {

    @Serializable
    data class Response(
        val op: Long,
        val d: Identity,
    )

    @Serializable
    data class Properties(
        @SerialName("\$os")
        val os: String,

        @SerialName("\$browser")
        val browser: String,

        @SerialName("\$device")
        val device: String,
    )
}

@Serializable
data class Res(
    val t: String?,
    val s: Int?,
    val op: Int,
    val d: JsonElement,
)

enum class OpCode(val value: Int) {
    /** An event was dispatched. */
    DISPATCH(0),

    /** Fired periodically by the client to keep the connection alive. */
    HEARTBEAT(1),

    /** Starts a new session during the initial handshake. */
    IDENTIFY(2),

    /** Update the client's presence. */
    PRESENCE_UPDATE(3),

    /** Joins/leaves or moves between voice channels. */
    VOICE_STATE(4),

    /** Resume a previous session that was disconnected. */
    RESUME(6),

    /** You should attempt to reconnect and resume immediately. */
    RECONNECT(7),

    /** Request information about offline guild members in a large guild. */
    REQUEST_GUILD_MEMBERS(8),

    /** The session has been invalidated. You should reconnect and identify/resume accordingly */
    INVALID_SESSION(9),

    /** Sent immediately after connecting, contains the heartbeat_interval to use. */
    HELLO(10),

    /** Sent in response to receiving a heartbeat to acknowledge that it has been received. */
    HEARTBEAT_ACK(11),

    /** For future use or unknown opcodes. */
    UNKNOWN(-1),
}

data class PlayerData(
    val incognitoMode: Boolean = false,
    val animeId: Long? = null,
    val animeTitle: String? = null,
    val episodeNumber: String? = null,
    val thumbnailUrl: String? = null,
)

data class ReaderData(
    val incognitoMode: Boolean = false,
    val mangaId: Long? = null,
    val mangaTitle: String? = null,
    val chapterNumber: Pair<Float, Int> = Pair(0f, 0),
    val chapterTitle: String? = null,
    val thumbnailUrl: String? = null,
)

// Enum class for standard Rich Presence in-app screens
enum class DiscordScreen(
    @StringRes val text: Int,
    @StringRes val details: Int,
    @StringRes val state: Int,
    val imageUrl: String,
) {
    APP(R.string.app_name, R.string.browsing, R.string.label_library, tachiyomiImageUrl),
    LIBRARY(R.string.app_name, R.string.browsing, R.string.label_library, libraryImageUrl),
    UPDATES(R.string.app_name, R.string.scrolling, R.string.label_recent_updates, updatesImageUrl),
    HISTORY(R.string.app_name, R.string.scrolling, R.string.label_recent_manga, historyImageUrl),
    BROWSE(R.string.app_name, R.string.browsing, R.string.label_sources, browseImageUrl),
    MORE(R.string.app_name, R.string.messing, R.string.label_settings, moreImageUrl),
    WEBVIEW(R.string.app_name, R.string.browsing, R.string.action_web_view, webviewImageUrl),
    MANGA(R.string.app_name, R.string.comic, R.string.reading, mangaImageUrl),
}

// Constants for standard Rich Presence image urls
private const val tachiyomiImageUrl = "emojis/1229456525026787409.webp?quality=lossless"
private const val libraryImageUrl = "emojis/1229715147250077736.webp?size=128&quality=lossless"
private const val updatesImageUrl = "emojis/1216122475688231003.webp?quality=lossless"
private const val historyImageUrl = "emojis/1216122387515310170.webp?quality=lossless"
private const val browseImageUrl = "emojis/1216122371501723718.webp?quality=lossless"
private const val moreImageUrl = "emojis/1216122403219050536.webp?quality=lossless"
private const val webviewImageUrl = "emojis/1216122455618490509.webp?quality=lossless"
private const val mangaImageUrl = "emojis/1216122415751626782.webp?quality=lossless"
