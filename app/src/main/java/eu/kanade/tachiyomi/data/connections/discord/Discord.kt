package eu.kanade.tachiyomi.data.connections.discord

import android.graphics.Color
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.ConnectionsService
import tachiyomi.i18n.MR

class Discord(id: Long) : ConnectionsService(id) {

    override fun nameRes() = MR.strings.connections_discord

    override fun getLogo() = R.drawable.ic_discord_24dp

    override fun getLogoColor() = Color.rgb(88, 101, 242)

    override fun logout() {
        super.logout()
        connectionsPreferences.connectionsToken(this).delete()
    }

    override suspend fun login(username: String, password: String) {
        // Not Needed
    }
}
