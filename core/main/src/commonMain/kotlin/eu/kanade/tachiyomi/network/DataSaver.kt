package eu.kanade.tachiyomi.network

interface DataSaver {

    fun getUrl(originalUrl: String): String

    companion object {
        val NoOp = object : DataSaver {
            override fun getUrl(originalUrl: String) = originalUrl
        }

        const val NONE = 0
        const val WSRV_NL = 1
    }
}

fun getDataSaver(sourceId: Long, preferences: NetworkPreferences): DataSaver {
    val dataSaver = preferences.dataSaver().get()
    return when (dataSaver) {
        DataSaver.WSRV_NL -> WsrvNlDataSaver(preferences)
        else -> DataSaver.NoOp
    }
}

private class WsrvNlDataSaver(preferences: NetworkPreferences) : DataSaver {

    private val apiUrl = preferences.dataSaverWsrvNlUrl().get().removePrefix("https://").removePrefix("http://").trimEnd('/')
    private val quality = preferences.dataSaverQuality().get()

    override fun getUrl(originalUrl: String): String {
        val format = if (originalUrl.contains(".webp", true) || originalUrl.contains(".gif", true)) {
            "&n=-1"
        } else {
            "&output=webp"
        }
        return "https://$apiUrl/?url=$originalUrl$format&quality=$quality&default=1"
    }
}
