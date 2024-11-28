package eu.kanade.tachiyomi.ui.more.stats.details

import android.graphics.drawable.Drawable
import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.database.models.MangaChapterHistory
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.database.models.seriesType
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.icon
import eu.kanade.tachiyomi.source.nameBasedOnEnabledLanguages
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import eu.kanade.tachiyomi.ui.more.stats.StatsHelper
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.mapSeriesType
import eu.kanade.tachiyomi.util.mapStatus
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.roundToTwoDecimal
import eu.kanade.tachiyomi.util.system.withUIContext
import kotlinx.coroutines.runBlocking
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import yokai.domain.manga.interactor.GetLibraryManga
import yokai.i18n.MR
import yokai.util.lang.getString
import java.util.*
import java.util.concurrent.*
import kotlin.math.roundToInt
import yokai.domain.track.interactor.GetTrack

class StatsDetailsPresenter(
    private val db: DatabaseHelper = Injekt.get(),
    private val prefs: PreferencesHelper = Injekt.get(),
    val trackManager: TrackManager = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
) : BaseCoroutinePresenter<StatsDetailsController>() {
    private val getLibraryManga: GetLibraryManga by injectLazy()
    private val getTrack: GetTrack by injectLazy()

    private val context
        get() = view?.view?.context ?: prefs.context
    var libraryMangas = getLibrary()
        set(value) {
            field = value
            mangasDistinct = field.distinct()
        }
    private var mangasDistinct = libraryMangas.distinct()
    val sources = getEnabledSources()
    val extensionManager by injectLazy<ExtensionManager>()
    val enabledLanguages = prefs.enabledLanguages().get()

    var selectedStat: Stats? = null
    var selectedSeriesType = mutableSetOf<String>()
    var selectedSource = mutableSetOf<Source>()
    var selectedStatus = mutableSetOf<String>()
    var selectedLanguage = mutableSetOf<String>()
    var selectedCategory = mutableSetOf<Category>()
    var selectedStatsSort: StatsSort? = null

    var startDate: Calendar = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        set(Calendar.HOUR_OF_DAY, 0)
        clear(Calendar.MINUTE)
        clear(Calendar.SECOND)
        clear(Calendar.MILLISECOND)
    }
    var endDate: Calendar = Calendar.getInstance().apply {
        timeInMillis = startDate.timeInMillis - 1
        add(Calendar.WEEK_OF_YEAR, 1)
    }
    private var daysRange = getDaysRange()
    var history = getMangaHistoryGroupedByDay()
    var historyByDayAndManga = emptyMap<Calendar, Map<Manga, List<History>>>()

    var currentStats: ArrayList<StatsData>? = null
    val seriesTypeStats by lazy {
        arrayOf(
            context.getString(MR.strings.manga),
            context.getString(MR.strings.manhwa),
            context.getString(MR.strings.manhua),
            context.getString(MR.strings.comic),
            context.getString(MR.strings.webtoon),
        )
    }
    val statusStats by lazy {
        arrayOf(
            context.getString(MR.strings.ongoing),
            context.getString(MR.strings.completed),
            context.getString(MR.strings.licensed),
            context.getString(MR.strings.publishing_finished),
            context.getString(MR.strings.cancelled),
            context.getString(MR.strings.on_hiatus),
        )
    }
    private val defaultCategory by lazy {
        if (libraryMangas.any { it.category == 0 }) arrayOf(Category.createDefault(context)) else emptyArray()
    }
    val categoriesStats by lazy { defaultCategory + getCategories().toTypedArray() }
    val languagesStats by lazy {
        prefs.enabledLanguages().get()
            .associateWith { lang -> LocaleHelper.getSourceDisplayName(lang, context) }
            .toSortedMap()
    }
    private val pieColorList = StatsHelper.PIE_CHART_COLOR_LIST

    /**
     * Get the data of the selected stat
     */
    fun getStatisticData(keepAdapter: Boolean = false) {
        if (selectedStat == null || selectedStatsSort == null) {
            return
        }

        presenterScope.launchIO {
            when (selectedStat) {
                Stats.SERIES_TYPE -> setupSeriesType()
                Stats.STATUS -> setupStatus()
                Stats.SCORE -> setupScores()
                Stats.LANGUAGE -> setupLanguages()
                Stats.LENGTH -> setupLength()
                Stats.TRACKER -> setupTrackers()
                Stats.SOURCE -> setupSources()
                Stats.CATEGORY -> setupCategories()
                Stats.TAG -> setupTags()
                Stats.START_YEAR -> setupStartYear()
                Stats.READ_DURATION -> setupReadDuration()
                else -> {}
            }
            withUIContext { view?.updateStats(keepAdapter = keepAdapter) }
        }
    }

    private fun setupSeriesType() {
        currentStats = ArrayList()
        val libraryFormat = mangasDistinct.filterByChip().groupBy { it.seriesType() }

        libraryFormat.forEach { (seriesType, mangaList) ->
            currentStats?.add(
                StatsData(
                    color = pieColorList[currentStats?.size!!],
                    count = mangaList.count(),
                    meanScore = mangaList.getMeanScoreRounded(),
                    chaptersRead = mangaList.sumOf { it.read },
                    totalChapters = mangaList.sumOf { it.totalChapters },
                    label = context.mapSeriesType(seriesType),
                    readDuration = mangaList.getReadDuration(),
                ),
            )
        }
        sortCurrentStats()
    }

    private fun setupStatus() {
        currentStats = ArrayList()
        val libraryFormat = mangasDistinct.filterByChip().groupBy { it.status }

        libraryFormat.forEach { (status, mangaList) ->
            currentStats?.add(
                StatsData(
                    color = StatsHelper.STATUS_COLOR_MAP[status],
                    count = mangaList.count(),
                    meanScore = mangaList.getMeanScoreRounded(),
                    chaptersRead = mangaList.sumOf { it.read },
                    totalChapters = mangaList.sumOf { it.totalChapters },
                    label = context.mapStatus(status),
                    readDuration = mangaList.getReadDuration(),
                ),
            )
        }
        sortCurrentStats()
    }

    private fun setupScores() {
        currentStats = ArrayList()
        val libraryFormat = mangasDistinct.filterByChip().groupBy { it.getMeanScoreToInt() }
        val scoreMap = StatsHelper.SCORE_COLOR_MAP.plus(null to pieColorList[1])

        scoreMap.forEach { (score, color) ->
            val mangaList = libraryFormat[score]
            currentStats?.add(
                StatsData(
                    color = color,
                    count = mangaList?.count() ?: 0,
                    meanScore = score?.toDouble() ?: 0.0,
                    chaptersRead = mangaList?.sumOf { it.read } ?: 0,
                    totalChapters = mangaList?.sumOf { it.totalChapters } ?: 0,
                    label = score?.toString() ?: context.getString(MR.strings.not_rated),
                    readDuration = mangaList?.getReadDuration() ?: 0L,
                    id = score?.toLong(),
                ),
            )
        }
    }

    private fun setupLanguages() {
        currentStats = ArrayList()
        val libraryFormat = mangasDistinct.filterByChip().groupBy { it.getLanguage() }

        libraryFormat.forEach { (language, mangaList) ->
            currentStats?.add(
                StatsData(
                    color = pieColorList[currentStats?.size!! % 12],
                    count = mangaList.count(),
                    meanScore = mangaList.getMeanScoreRounded(),
                    chaptersRead = mangaList.sumOf { it.read },
                    totalChapters = mangaList.sumOf { it.totalChapters },
                    label = language,
                    readDuration = mangaList.getReadDuration(),
                ),
            )
        }
        sortCurrentStats()
    }

    private fun setupLength() {
        currentStats = ArrayList()
        var mangaFiltered = mangasDistinct.filterByChip()
        StatsHelper.STATS_LENGTH.forEach { range ->
            val (min, max) = range.first to range.last.takeIf { it != Int.MAX_VALUE }
            val (match, unmatch) = mangaFiltered.partition { it.totalChapters in range }
            mangaFiltered = unmatch
            currentStats?.add(
                StatsData(
                    color = StatsHelper.SCORE_COLOR_LIST[currentStats?.size!!],
                    count = match.count(),
                    meanScore = match.getMeanScoreRounded(),
                    chaptersRead = match.sumOf { it.read },
                    totalChapters = match.sumOf { it.totalChapters },
                    label = if (min == max) {
                        min.toString()
                    } else {
                        listOf(min.toString(), max?.toString()).joinToString("-")
                            .replace("-null", "+")
                    },
                    readDuration = match.getReadDuration(),
                    id = range.last.toLong(),
                ),
            )
        }
    }

    private fun setupTrackers() {
        currentStats = ArrayList()
        val libraryFormat = mangasDistinct.filterByChip()
            .map { it to getTracks(it).ifEmpty { listOf(null) } }
            .flatMap { it.second.map { track -> it.first to track } }
        val loggedServices = trackManager.services.filter { it.isLogged }

        val serviceWithTrackedManga = libraryFormat.groupBy { it.second?.sync_id }

        serviceWithTrackedManga.forEach { (serviceId, mangaAndTrack) ->
            val service = loggedServices.find { it.id == serviceId }
            val label = context.getString(service?.nameRes() ?: MR.strings.not_tracked)
            currentStats?.add(
                StatsData(
                    color = service?.getTrackerColor() ?: pieColorList.first(),
                    count = mangaAndTrack.count(),
                    meanScore = mangaAndTrack.map { it.second }.getMeanScoreByTracker()?.roundToTwoDecimal(),
                    chaptersRead = mangaAndTrack.sumOf { it.first.read },
                    totalChapters = mangaAndTrack.sumOf { it.first.totalChapters },
                    label = label,
                    iconRes = service?.getLogo(),
                    iconBGColor = service?.getLogoColor(),
                    readDuration = mangaAndTrack.map { it.first }.getReadDuration(),
                    id = service?.id?.toLong(),
                ),
            )
        }
        sortCurrentStats()
    }

    private fun setupSources() {
        currentStats = ArrayList()
        val libraryFormat = mangasDistinct.filterByChip().groupBy { it.source }

        libraryFormat.forEach { (sourceId, mangaList) ->
            val source = sourceManager.getOrStub(sourceId)
            currentStats?.add(
                StatsData(
                    color = pieColorList[1],
                    count = mangaList.count(),
                    meanScore = mangaList.getMeanScoreRounded(),
                    chaptersRead = mangaList.sumOf { it.read },
                    totalChapters = mangaList.sumOf { it.totalChapters },
                    label = source.nameBasedOnEnabledLanguages(enabledLanguages, extensionManager),
                    icon = source.icon(),
                    readDuration = mangaList.getReadDuration(),
                    id = sourceId,
                ),
            )
        }
        sortCurrentStats()
    }

    private fun setupCategories() {
        currentStats = ArrayList()
        val libraryFormat = libraryMangas.filterByChip().groupBy { it.category }
        val categories = getCategories()

        libraryFormat.forEach { (category, mangaList) ->
            val label = categories.find { it.id == category }?.name ?: context.getString(MR.strings.default_value)
            currentStats?.add(
                StatsData(
                    color = pieColorList[currentStats?.size!! % pieColorList.size],
                    count = mangaList.count(),
                    meanScore = mangaList.getMeanScoreRounded(),
                    chaptersRead = mangaList.sumOf { it.read },
                    totalChapters = mangaList.sumOf { it.totalChapters },
                    label = label,
                    readDuration = mangaList.getReadDuration(),
                    id = category.toLong(),
                ),
            )
        }
        sortCurrentStats()
    }

    private fun setupTags() {
        currentStats = ArrayList()
        val mangaFiltered = mangasDistinct.filterByChip()
        val tags = mangaFiltered.flatMap { it.getTags() }.distinctBy { it.uppercase() }
        val libraryFormat = tags.map { tag ->
            tag to mangaFiltered.filter {
                it.getTags().any { mangaTag -> mangaTag.equals(tag, true) }
            }
        }

        libraryFormat.forEach { (tag, mangaList) ->
            currentStats?.add(
                StatsData(
                    color = pieColorList[1],
                    count = mangaList.count(),
                    meanScore = mangaList.getMeanScoreRounded(),
                    chaptersRead = mangaList.sumOf { it.read },
                    totalChapters = mangaList.sumOf { it.totalChapters },
                    label = tag,
                    readDuration = mangaList.getReadDuration(),
                ),
            )
        }
        sortCurrentStats()
        currentStats = currentStats?.take(100)?.let { ArrayList(it) }
    }

    private fun setupStartYear() {
        currentStats = ArrayList()
        val libraryFormat = mangasDistinct.filterByChip().groupBy { it.getStartYear() }

        libraryFormat.forEach { (year, mangaList) ->
            currentStats?.add(
                StatsData(
                    color = if (year == null) pieColorList[0] else pieColorList[1],
                    count = mangaList.count(),
                    meanScore = mangaList.getMeanScoreRounded(),
                    chaptersRead = mangaList.sumOf { it.read },
                    totalChapters = mangaList.sumOf { it.totalChapters },
                    label = year?.toString() ?: context.getString(MR.strings.not_started),
                    readDuration = mangaList.getReadDuration(),
                    id = year?.toLong(),
                ),
            )
        }
    }

    fun setupReadDuration(day: Calendar? = null) {
        currentStats = ArrayList()

        historyByDayAndManga = history.mapValues { h ->
            h.value.groupBy { it.manga }.mapValues { m -> m.value.map { it.history } }
        }
        val libraryFormat = if (day == null) {
            historyByDayAndManga.values.flatMap { it.entries }.groupBy { it.key }
                .mapValues { it.value.flatMap { h -> h.value } }
        } else {
            historyByDayAndManga[day]
        }

        libraryFormat?.forEach { (manga, history) ->
            currentStats?.add(
                StatsData(
                    color = pieColorList[1],
                    count = 1,
                    label = manga.title,
                    subLabel = sources.find { it.id == manga.source }?.toString(),
                    readDuration = history.sumOf { it.time_read },
                    id = manga.id,
                ),
            )
        }
        currentStats?.sortByDescending { it.readDuration }
    }

    /**
     * Filter the stat data according to the chips selected
     */
    private fun List<LibraryManga>.filterByChip(): List<LibraryManga> {
        return this.filterByCategory(selectedStat == Stats.CATEGORY)
            .filterBySeriesType(selectedStat == Stats.SERIES_TYPE)
            .filterByStatus(selectedStat == Stats.STATUS)
            .filterByLanguage(selectedStat == Stats.LANGUAGE || (selectedStat != Stats.SOURCE && selectedSource.isNotEmpty()))
            .filterBySource(selectedStat in listOf(Stats.SOURCE, Stats.LANGUAGE) || selectedLanguage.isNotEmpty())
    }

    private fun List<LibraryManga>.filterBySeriesType(noFilter: Boolean = false): List<LibraryManga> {
        return if (noFilter || selectedSeriesType.isEmpty()) {
            this
        } else {
            filter { manga ->
                context.mapSeriesType(manga.seriesType()) in selectedSeriesType
            }
        }
    }

    private fun List<LibraryManga>.filterByStatus(noFilter: Boolean = false): List<LibraryManga> {
        return if (noFilter || selectedStatus.isEmpty()) {
            this
        } else {
            filter { manga ->
                context.mapStatus(manga.status) in selectedStatus
            }
        }
    }

    private fun List<LibraryManga>.filterByLanguage(noFilter: Boolean = false): List<LibraryManga> {
        return if (noFilter || selectedLanguage.isEmpty()) {
            this
        } else {
            filter { manga ->
                manga.getLanguage() in selectedLanguage
            }
        }
    }

    private fun List<LibraryManga>.filterBySource(noFilter: Boolean = false): List<LibraryManga> {
        return if (noFilter || selectedSource.isEmpty()) {
            this
        } else {
            filter { manga ->
                manga.source in selectedSource.map { it.id }
            }
        }
    }

    private fun List<LibraryManga>.filterByCategory(noFilter: Boolean = false): List<LibraryManga> {
        return if (noFilter || selectedCategory.isEmpty()) {
            this
        } else {
            libraryMangas.filter { manga ->
                manga.category in selectedCategory.map { it.id }
            }.distinct()
        }
    }

    fun sortCurrentStats() {
        when (selectedStatsSort) {
            StatsSort.COUNT_DESC -> currentStats?.sortWith(
                compareByDescending<StatsData> { it.count }.thenByDescending { it.chaptersRead }
                    .thenByDescending { it.meanScore },
            )
            StatsSort.MEAN_SCORE_DESC -> currentStats?.sortWith(
                compareByDescending<StatsData> { it.meanScore }.thenByDescending { it.count }
                    .thenByDescending { it.chaptersRead },
            )
            StatsSort.PROGRESS_DESC -> currentStats?.sortWith(
                compareByDescending<StatsData> { it.chaptersRead }.thenByDescending { it.count }
                    .thenByDescending { it.meanScore },
            )
            else -> {}
        }
    }

    private fun Manga.getTags(): List<String> {
        return getGenres() ?: emptyList()
    }

    /**
     * Get language name of a manga
     */
    private fun LibraryManga.getLanguage(): String {
        val code = if (isLocal()) {
            LocalSource.getMangaLang(this)
        } else {
            sourceManager.get(source)?.lang
        } ?: return context.getString(MR.strings.unknown)
        return LocaleHelper.getLocalizedDisplayName(code)
    }

    /**
     * Get mean score rounded to two decimal of a list of manga
     */
    private fun List<LibraryManga>.getMeanScoreRounded(): Double? {
        val mangaTracks = this.map { it to getTracks(it) }
        val scoresList = mangaTracks.filter { it.second.isNotEmpty() }
            .mapNotNull { it.second.getMeanScoreByTracker() }
        return if (scoresList.isEmpty()) null else scoresList.average().roundToTwoDecimal()
    }

    /**
     * Get mean score rounded to int of a single manga
     */
    private fun LibraryManga.getMeanScoreToInt(): Int? {
        val mangaTracks = getTracks(this)
        val scoresList = mangaTracks.filter { it.score > 0 }
            .mapNotNull { it.get10PointScore() }
        return if (scoresList.isEmpty()) null else scoresList.average().roundToInt().coerceIn(1..10)
    }

    /**
     * Get mean score of a tracker
     */
    private fun List<Track?>.getMeanScoreByTracker(): Double? {
        val scoresList = this.filter { (it?.score ?: 0f) > 0 }
            .mapNotNull { it?.get10PointScore() }
        return if (scoresList.isEmpty()) null else scoresList.average()
    }

    /**
     * Convert the score to a 10 point score
     */
    private fun Track.get10PointScore(): Float? {
        val service = trackManager.getService(this.sync_id)
        return service?.get10PointScore(this.score)
    }

    private fun LibraryManga.getStartYear(): Int? {
        if (db.getChapters(id).executeAsBlocking().any { it.read }) {
            val chapters = db.getHistoryByMangaId(id!!).executeAsBlocking().filter { it.last_read > 0 }
            val date = chapters.minOfOrNull { it.last_read } ?: return null
            val cal = Calendar.getInstance().apply { timeInMillis = date }
            return if (date <= 0L) null else cal.get(Calendar.YEAR)
        }
        return null
    }

    fun getStatsArray(): Array<String> {
        return Stats.entries.map { context.getString(it.resourceId) }.toTypedArray()
    }

    private fun getEnabledSources(): List<Source> {
        return mangasDistinct.mapNotNull { sourceManager.get(it.source) }
            .distinct().sortedBy { it.name }
    }

    fun getSortDataArray(): Array<String> {
        return StatsSort.entries.sorted().map { context.getString(it.resourceId) }.toTypedArray()
    }

    fun getTracks(manga: Manga): MutableList<Track> {
        return runBlocking { getTrack.awaitAllByMangaId(manga.id) }.toMutableList()
    }

    fun getLibrary(): MutableList<LibraryManga> {
        return runBlocking { getLibraryManga.await() }.toMutableList()
    }

    private fun getCategories(): MutableList<Category> {
        return db.getCategories().executeAsBlocking()
    }

    private fun List<LibraryManga>.getReadDuration(): Long {
        return sumOf { manga -> db.getHistoryByMangaId(manga.id!!).executeAsBlocking().sumOf { it.time_read } }
    }

    /**
     * Get the manga and history grouped by day during the selected period
     */
    fun getMangaHistoryGroupedByDay(): Map<Calendar, List<MangaChapterHistory>> {
        val history = db.getHistoryPerPeriod(startDate.timeInMillis, endDate.timeInMillis).executeAsBlocking()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = startDate.timeInMillis
        }

        return (0 until daysRange).associate { _ ->
            Calendar.getInstance().apply { timeInMillis = calendar.timeInMillis } to history.filter {
                val calH = Calendar.getInstance().apply { timeInMillis = it.history.last_read }
                calH.get(Calendar.DAY_OF_YEAR) == calendar.get(Calendar.DAY_OF_YEAR) &&
                    calH.get(Calendar.YEAR) == calendar.get(Calendar.YEAR)
            }.also { calendar.add(Calendar.DAY_OF_WEEK, 1) }
        }
    }

    fun getCalendarShortDay(calendar: Calendar): String {
        return if (history.size > 14) {
            ""
        } else {
            calendar.getDisplayName(
                Calendar.DAY_OF_WEEK,
                Calendar.SHORT,
                Locale.getDefault(),
            )
        } ?: context.getString(MR.strings.unknown)
    }

    fun changeReadDurationPeriod(toAdd: Int) {
        startDate.add(Calendar.DAY_OF_YEAR, toAdd * daysRange.toInt())
        endDate.add(Calendar.DAY_OF_YEAR, toAdd * daysRange.toInt())
    }

    private fun getDaysRange(): Long {
        return TimeUnit.MILLISECONDS.toDays(endDate.timeInMillis - startDate.timeInMillis) + 1
    }

    /**
     * Update the start date and end date according to time selected
     */
    fun updateReadDurationPeriod(startMillis: Long, endMillis: Long) {
        startDate = Calendar.getInstance().apply {
            timeInMillis = startMillis
            set(Calendar.HOUR_OF_DAY, 0)
            clear(Calendar.MINUTE)
            clear(Calendar.SECOND)
            clear(Calendar.MILLISECOND)
        }
        endDate = Calendar.getInstance().apply {
            timeInMillis = endMillis
            set(Calendar.HOUR_OF_DAY, 0)
            clear(Calendar.MINUTE)
            clear(Calendar.SECOND)
            clear(Calendar.MILLISECOND)
            add(Calendar.DAY_OF_YEAR, 1)
            timeInMillis -= 1
        }
        daysRange = getDaysRange()
    }

    fun updateMangaHistory() {
        history = getMangaHistoryGroupedByDay()
    }

    fun convertCalendarToLongString(calendar: Calendar): String {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val showYear = calendar.get(Calendar.YEAR) != currentYear
        val flagYear = if (showYear) DateUtils.FORMAT_ABBREV_MONTH else 0
        val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or flagYear
        return DateUtils.formatDateTime(context, calendar.timeInMillis, flags)
    }

    fun convertCalendarToString(calendar: Calendar, showYear: Boolean): String {
        val flagYear = if (showYear) DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_YEAR else 0
        val flags = DateUtils.FORMAT_SHOW_DATE or flagYear
        return DateUtils.formatDateTime(context, calendar.timeInMillis, flags)
    }

    fun getPeriodString(): String {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val showYear = listOf(startDate, endDate).any { it.get(Calendar.YEAR) != currentYear }
        val startDateString = convertCalendarToString(startDate, showYear)
        val endDateString = convertCalendarToString(endDate, showYear)
        return "$startDateString - $endDateString"
    }

    enum class Stats(val resourceId: StringResource) {
        SERIES_TYPE(MR.strings.series_type),
        STATUS(MR.strings.status),
        READ_DURATION(MR.strings.read_duration),
        SCORE(MR.strings.score),
        LENGTH(MR.strings.length),
        LANGUAGE(MR.strings.language),
        SOURCE(MR.strings.source),
        TRACKER(MR.strings.tracker),
        CATEGORY(MR.strings.category),
        TAG(MR.strings.tag),
        START_YEAR(MR.strings.start_year),
    }

    enum class StatsSort(val resourceId: StringResource) {
        COUNT_DESC(MR.strings.most_entries),
        PROGRESS_DESC(MR.strings.chapters_read),
        MEAN_SCORE_DESC(MR.strings.mean_tracking_score),
    }

    class StatsData(
        var color: Int? = null,
        val count: Int = 0,
        val meanScore: Double? = null,
        val chaptersRead: Int = 0,
        val totalChapters: Int = 0,
        var label: String? = null,
        @DrawableRes
        var iconRes: Int? = null,
        var iconBGColor: Int? = null,
        var icon: Drawable? = null,
        var subLabel: String? = null,
        var readDuration: Long = 0,
        var id: Long? = null,
    )
}
