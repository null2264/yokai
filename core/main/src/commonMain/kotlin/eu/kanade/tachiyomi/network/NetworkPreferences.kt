package eu.kanade.tachiyomi.network

import eu.kanade.tachiyomi.core.preference.PreferenceStore

class NetworkPreferences(
    private val preferenceStore: PreferenceStore,
    private val verboseLogging: Boolean,
) {

    fun verboseLogging() = preferenceStore.getBoolean("verbose_logging", verboseLogging)

    fun dohProvider() = preferenceStore.getInt("doh_provider", -1)

    fun defaultUserAgent() = preferenceStore.getString("default_user_agent", DEFAULT_USER_AGENT)

    fun dataSaver() = preferenceStore.getInt("pref_data_saver", DataSaver.NONE)
    fun dataSaverWsrvNlUrl() = preferenceStore.getString("pref_data_saver_wsrv_nl_url", "https://wsrv.nl/")
    fun dataSaverQuality() = preferenceStore.getInt("pref_data_saver_quality", 80)

    companion object {
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"
    }
}
