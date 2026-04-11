package eu.kanade.tachiyomi.ui.setting.controllers

import android.app.Activity
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.R
import yokai.i18n.MR
import yokai.util.lang.getString
import dev.icerock.moko.resources.compose.stringResource
import eu.kanade.tachiyomi.data.preference.changesIn
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackPreferences
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.data.track.bangumi.BangumiApi
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeListApi
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriApi
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.setting.SettingsLegacyController
import eu.kanade.tachiyomi.ui.setting.add
import eu.kanade.tachiyomi.ui.setting.defaultValue
import eu.kanade.tachiyomi.ui.setting.iconRes
import eu.kanade.tachiyomi.ui.setting.infoPreference
import eu.kanade.tachiyomi.ui.setting.onClick
import eu.kanade.tachiyomi.ui.setting.preference
import eu.kanade.tachiyomi.ui.setting.preferenceCategory
import eu.kanade.tachiyomi.ui.setting.switchPreference
import eu.kanade.tachiyomi.ui.setting.titleMRes as titleRes
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.widget.preference.TrackLoginDialog
import eu.kanade.tachiyomi.widget.preference.TrackLogoutDialog
import eu.kanade.tachiyomi.widget.preference.TrackerPreference
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsTrackingController :
    SettingsLegacyController(),
    TrackLoginDialog.Listener,
    TrackLogoutDialog.Listener {

    private val trackManager: TrackManager by injectLazy()
    val trackPreferences: TrackPreferences by injectLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = MR.strings.tracking

        switchPreference {
            key = Keys.autoUpdateTrack
            titleRes = MR.strings.update_tracking_after_reading
            defaultValue = true
        }
        switchPreference {
            key = Keys.trackMarkedAsRead
            titleRes = MR.strings.update_tracking_marked_read
            defaultValue = false
        }
        switchPreference {
            key = Keys.autoSyncProgressFromTrackers
            titleRes = MR.strings.pref_auto_sync_progress_from_trackers
            defaultValue = true
        }
        preferenceCategory {
            titleRes = MR.strings.services

            trackPreference(trackManager.myAnimeList) {
                activity?.openInBrowser(MyAnimeListApi.authUrl(), trackManager.myAnimeList.getLogoColor(), true)
            }
            trackPreference(trackManager.aniList) {
                activity?.openInBrowser(AnilistApi.authUrl(), trackManager.aniList.getLogoColor(), true)
            }
            preference {
                key = "update_anilist_scoring"
                isPersistent = false
                isIconSpaceReserved = true
                title = context.getString(MR.strings.update_tracking_scoring_type, context.getString(MR.strings.anilist))

                preferences.getStringPref(trackManager.aniList.getUsername())
                    .changesIn(viewScope) {
                        isVisible = it.isNotEmpty()
                    }

                onClick {
                    viewScope.launchIO {
                        val (result, error) = trackManager.aniList.updatingScoring()
                        if (result) {
                            view?.snack(MR.strings.scoring_type_updated)
                        } else {
                            view?.snack(
                                context.getString(
                                    MR.strings.could_not_update_scoring_,
                                    error?.localizedMessage.orEmpty(),
                                ),
                            )
                        }
                    }
                }
            }
            trackPreference(trackManager.kitsu) {
                val dialog = TrackLoginDialog(trackManager.kitsu, MR.strings.email)
                dialog.targetController = this@SettingsTrackingController
                dialog.showDialog(router)
            }
            trackPreference(trackManager.mangaUpdates) {
                val dialog = TrackLoginDialog(trackManager.mangaUpdates, MR.strings.username)
                dialog.targetController = this@SettingsTrackingController
                dialog.showDialog(router)
            }
            trackPreference(trackManager.shikimori) {
                activity?.openInBrowser(ShikimoriApi.authUrl(), trackManager.shikimori.getLogoColor(), true)
            }
            trackPreference(trackManager.bangumi) {
                activity?.openInBrowser(BangumiApi.authUrl(), trackManager.bangumi.getLogoColor(), true)
            }
            infoPreference(MR.strings.tracking_info)
        }
        preferenceCategory {
            titleRes = MR.strings.enhanced_services
            val sourceManager = Injekt.get<SourceManager>()
            val enhancedTrackers = trackManager.services
                .filter { service ->
                    if (service !is EnhancedTrackService) return@filter false
                    sourceManager.getCatalogueSources().any { service.accept(it) }
                }
            enhancedTrackers.forEach { trackPreference(it) }
            infoPreference(MR.strings.enhanced_tracking_info)
        }
    }

    private inline fun PreferenceGroup.trackPreference(
        service: TrackService,
        crossinline login: () -> Unit = { },
    ): TrackerPreference {
        return add(
            TrackerPreference(context).apply {
                key = trackPreferences.trackUsername(service).key()
                title = context.getString(service.nameRes())
                iconRes = service.getLogo()
                iconColor = service.getLogoColor()
                onClick {
                    if (service.isLogged) {
                        if (service is EnhancedTrackService) {
                            service.logout()
                            updatePreference(service)
                        } else {
                            val dialog = TrackLogoutDialog(service)
                            dialog.targetController = this@SettingsTrackingController
                            dialog.showDialog(router)
                        }
                    } else {
                        if (service is EnhancedTrackService) {
                            service.loginNoop()
                            updatePreference(service)
                        } else {
                            login()
                        }
                    }
                }
            },
        )
    }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        updatePreference(trackManager.myAnimeList)
        updatePreference(trackManager.aniList)
        updatePreference(trackManager.shikimori)
        updatePreference(trackManager.bangumi)
    }

    private fun updatePreference(service: TrackService) {
        val pref = findPreference(trackPreferences.trackUsername(service).key()) as? TrackerPreference
        pref?.notifyChanged()
    }

    override fun trackLoginDialogClosed(service: TrackService) {
        updatePreference(service)
    }

    override fun trackLogoutDialogClosed(service: TrackService) {
        updatePreference(service)
    }
}
