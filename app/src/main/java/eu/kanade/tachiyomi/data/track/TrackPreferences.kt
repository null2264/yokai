package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.core.preference.Preference
import eu.kanade.tachiyomi.core.preference.PreferenceStore
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.data.track.anilist.Anilist

class TrackPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun trackUsername(sync: TrackService) = preferenceStore.getString(trackUsername(sync.id), "")

    fun trackPassword(sync: TrackService) = preferenceStore.getString(trackPassword(sync.id), "")

    fun trackAuthExpired(sync: TrackService) = preferenceStore.getBoolean(
        Preference.privateKey("pref_tracker_auth_expired_${sync.id}"),
        false,
    )

    fun setCredentials(sync: TrackService, username: String, password: String) {
        trackUsername(sync).set(username)
        trackPassword(sync).set(password)
        trackAuthExpired(sync).set(false)
    }

    fun trackToken(sync: TrackService) = preferenceStore.getString(trackToken(sync.id), "")

    fun anilistScoreType() = preferenceStore.getString("anilist_score_type", Anilist.POINT_10)

    fun autoUpdateTrack() = preferenceStore.getBoolean("pref_auto_update_manga_sync_key", true)

    fun autoSyncProgressFromTrackers() = preferenceStore.getBoolean(PreferenceKeys.autoSyncProgressFromTrackers, true)

    companion object {
        fun trackUsername(syncId: Long) = Preference.privateKey("pref_mangasync_username_$syncId")

        private fun trackPassword(syncId: Long) =
            Preference.privateKey("pref_mangasync_password_$syncId")

        private fun trackToken(syncId: Long) = Preference.privateKey("track_token_$syncId")
    }
}
