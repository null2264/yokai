package yokai.presentation

import androidx.compose.runtime.Immutable

sealed interface StatsScreenState {
    @Immutable
    data object Loading : StatsScreenState

    // @Immutable
    // data class Success(
    //     val overview: StatsData.Overview,
    //     val titles: StatsData.Titles,
    //     val chapters: StatsData.Chapters,
    //     val trackers: StatsData.Trackers,
    // ) : StatsScreenState
}
