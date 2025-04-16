package eu.kanade.tachiyomi.data.connections.discord

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.compose.ui.util.fastAny
import eu.kanade.domain.connections.service.ConnectionsPreferences
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.ConnectionsManager
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.util.system.notificationBuilder
import kotlinx.coroutines.DelicateCoroutinesApi
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category.Companion.UNCATEGORIZED_ID
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class DiscordRPCService : Service() {

    private val connectionsManager: ConnectionsManager by injectLazy()

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        val token = connectionsPreferences.connectionsToken(connectionsManager.discord).get()
        val status = when (connectionsPreferences.discordRPCStatus().get()) {
            -1 -> "dnd"
            0 -> "idle"
            else -> "online"
        }
        rpc = if (token.isNotBlank()) DiscordRPC(token, status) else null
        if (rpc != null) {
            launchIO {
                setScreen(this@DiscordRPCService, lastUsedScreen)
            }
            notification(this)
        } else {
            connectionsPreferences.enableDiscordRPC().set(false)
        }
    }

    override fun onDestroy() {
        NotificationReceiver.dismissNotification(this, Notifications.ID_DISCORD_RPC)
        rpc?.closeRPC()
        rpc = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun notification(context: Context) {
        val builder = context.notificationBuilder(Notifications.CHANNEL_DISCORD_RPC) {
            setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            setSmallIcon(R.drawable.ic_discord_24dp)
            setContentText(context.resources.getString(R.string.pref_discord_rpc))
            setAutoCancel(false)
            setOngoing(true)
            setUsesChronometer(true)
        }

        startForeground(Notifications.ID_DISCORD_RPC, builder.build())
    }

    companion object {

        private val connectionsPreferences: ConnectionsPreferences by injectLazy()

        internal var rpc: DiscordRPC? = null

        private val handler = Handler(Looper.getMainLooper())

        fun start(context: Context) {
            handler.removeCallbacksAndMessages(null)
            if (rpc == null && connectionsPreferences.enableDiscordRPC().get()) {
                since = System.currentTimeMillis()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(Intent(context, DiscordRPCService::class.java))
                } else {
                    context.startService(Intent(context, DiscordRPCService::class.java))
                }
            }
        }

        fun stop(context: Context, delay: Long = 30000L) {
            handler.postDelayed(
                { context.stopService(Intent(context, DiscordRPCService::class.java)) },
                delay,
            )
        }

        private var since = 0L

        internal var lastUsedScreen = DiscordScreen.APP
            set(value) {
                field = if (value == DiscordScreen.MANGA || value == DiscordScreen.WEBVIEW) field else value
            }

        internal suspend fun setScreen(
            context: Context,
            discordScreen: DiscordScreen,
            readerData: ReaderData = ReaderData(),
        ) {
            lastUsedScreen = discordScreen

            if (rpc == null) return

            val name = context.resources.getString(discordScreen.text)

            val details = readerData.mangaTitle ?: context.resources.getString(discordScreen.details)

            val state = if (readerData.incognitoMode) context.resources.getString(R.string.comic) else readerData.chapterTitle ?: context.resources.getString(discordScreen.state)

            val imageUrl = readerData.thumbnailUrl ?: discordScreen.imageUrl

            rpc!!.updateRPC(
                activity = Activity(
                    name = name,
                    details = details,
                    state = state,
                    type = 0,
                    timestamps = Activity.Timestamps(start = since),
                    assets = Activity.Assets(
                        largeImage = "mp:$imageUrl",
                        smallImage = "mp:${DiscordScreen.APP.imageUrl}",
                        smallText = context.resources.getString(DiscordScreen.APP.text),
                    ),
                ),
                since = since,
            )
        }

        internal suspend fun setReaderActivity(context: Context, readerData: ReaderData = ReaderData()) {
            if (rpc == null || readerData.thumbnailUrl == null || readerData.mangaId == null) return

            val categoryIds = Injekt.get<GetCategories>()
                .await(readerData.mangaId)
                .map { it.id.toString() }
                .run { ifEmpty { plus(UNCATEGORIZED_ID.toString()) } }

            val discordIncognitoMode = connectionsPreferences.discordRPCIncognito().get()
            val incognitoCategories = connectionsPreferences.discordRPCIncognitoCategories().get()

            val incognitoCategory = categoryIds.fastAny {
                it in incognitoCategories
            }

            val discordIncognito = discordIncognitoMode || readerData.incognitoMode || incognitoCategory

            val mangaTitle = readerData.mangaTitle.takeUnless { discordIncognito }

            val chapterTitle = readerData.chapterTitle?.let {
                when {
                    discordIncognito -> null
                    connectionsPreferences.useChapterTitles().get() -> it
                    else -> readerData.chapterNumber.let {
                        context.resources.getString(
                            R.string.display_mode_chapter,
                            formatChapterNumber(it.first.toDouble()),
                        ) + "/${it.second}"
                    }
                }
            }

            withIOContext {
                val networkService: NetworkHelper by injectLazy()
                val client = networkService.client
                val response = if (!discordIncognito) {
                    try {
                        client.newCall(GET("https://kizzy-api.cjjdxhdjd.workers.dev/image?url=${readerData.thumbnailUrl}")).execute()
                    } catch (e: Throwable) {
                        null
                    }
                } else {
                    null
                }

                val mangaThumbnail = response?.body?.string()
                    ?.takeIf { !it.contains("external/Not Found") }
                    ?.substringAfter("\"id\": \"")?.substringBefore("\"}")
                    ?.split("external/")?.getOrNull(1)?.let { "external/$it" }

                setScreen(
                    context = context,
                    discordScreen = DiscordScreen.MANGA,
                    readerData = ReaderData(
                        mangaTitle = mangaTitle,
                        chapterTitle = chapterTitle,
                        thumbnailUrl = mangaThumbnail,
                    ),
                )
            }
        }
    }
}
