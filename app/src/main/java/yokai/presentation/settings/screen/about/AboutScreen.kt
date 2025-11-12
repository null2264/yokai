package yokai.presentation.settings.screen.about

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import cafe.adriel.voyager.navigator.LocalNavigator
import co.touchlab.kermit.Logger
import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.compose.stringResource
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.core.storage.preference.asDateFormat
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.updater.AppUpdateChecker
import eu.kanade.tachiyomi.data.updater.AppUpdateNotifier
import eu.kanade.tachiyomi.data.updater.AppUpdateResult
import eu.kanade.tachiyomi.data.updater.RELEASE_URL
import eu.kanade.tachiyomi.util.CrashLogUtil
import eu.kanade.tachiyomi.util.compose.LocalDialogHostState
import eu.kanade.tachiyomi.util.compose.currentOrThrow
import eu.kanade.tachiyomi.util.lang.toTimestampString
import eu.kanade.tachiyomi.util.showNotificationPermissionPrompt
import eu.kanade.tachiyomi.util.system.isOnline
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.localeContext
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.withUIContext
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.DialogHostState
import yokai.i18n.MR
import yokai.presentation.component.preference.widget.TextPreferenceWidget
import yokai.presentation.core.components.LinkIcon
import yokai.presentation.core.enterAlwaysCollapsedAppBarScrollBehavior
import yokai.presentation.core.icons.CustomIcons
import yokai.presentation.core.icons.Discord
import yokai.presentation.core.icons.GitHub
import yokai.presentation.settings.SettingsScaffold
import yokai.util.Screen
import yokai.util.lang.getString

class AboutScreen : Screen() {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val dialogHostState = LocalDialogHostState.currentOrThrow
        val uriHandler = LocalUriHandler.current

        val preferences = remember { Injekt.get<PreferencesHelper>() }

        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val listState = rememberLazyListState()

        val requestNotificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                scope.launch { dialogHostState.awaitNotificationPermissionDeniedDialog() }
            }
        }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            // FIXME: Move this to MainActivity once the app is fully migrated to Compose
            scope.launchIO {
                context.checkVersion(
                    dialogState = dialogHostState,
                    isUserPrompt = false,
                    notificationPrompt = {
                        context.showNotificationPermissionPrompt(
                            requestNotificationPermission,
                            false,
                            preferences,
                        )
                    }
                )
            }
        }

        val dateFormat by lazy { preferences.dateFormatRaw().get().asDateFormat() }

        SettingsScaffold(
            title = stringResource(MR.strings.about),
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
            appBarScrollBehavior = enterAlwaysCollapsedAppBarScrollBehavior(
                canScroll = { listState.canScrollForward || listState.canScrollBackward },
            ),
            content = { contentPadding ->
                LazyColumn(
                    contentPadding = contentPadding,
                    state = listState,
                ) {
                    item {
                        TextPreferenceWidget(
                            title = stringResource(MR.strings.whats_new_this_release),
                            onPreferenceClick = {
                                uriHandler.openUri(if (BuildConfig.DEBUG) SOURCE_URL else RELEASE_URL)
                            },
                        )
                    }

                    if (BuildConfig.INCLUDE_UPDATER) {
                        item {
                            TextPreferenceWidget(
                                title = stringResource(MR.strings.check_for_updates),
                                onPreferenceClick = {
                                    if (context.isOnline()) {
                                        scope.launch {
                                            context.checkVersion(dialogHostState, true)
                                        }
                                    } else {
                                        context.toast(MR.strings.no_network_connection)
                                    }
                                },
                            )
                        }
                    }

                    item {
                        TextPreferenceWidget(
                            title = stringResource(MR.strings.version),
                            subtitle = getVersionName(),
                            onPreferenceClick = {
                                val deviceInfo = CrashLogUtil(context.localeContext).getDebugInfo()
                                val clipboard = context.getSystemService<ClipboardManager>()!!
                                val appInfo = context.getString(MR.strings.app_info)
                                clipboard.setPrimaryClip(ClipData.newPlainText(appInfo, deviceInfo))
                                scope.launch {
                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                        snackbarHostState.showSnackbar(
                                            message = context.getString(MR.strings._copied_to_clipboard, appInfo),
                                        )
                                    }
                                }
                            },
                        )
                    }

                    item {
                        TextPreferenceWidget(
                            title = stringResource(MR.strings.build_time),
                            subtitle = getFormattedBuildTime(dateFormat),
                        )
                    }

                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            HorizontalDivider()

                            TextPreferenceWidget(
                                title = stringResource(MR.strings.help_translate),
                                onPreferenceClick = { uriHandler.openUri("https://hosted.weblate.org/engage/yokai/") },
                            )
                        }
                    }

                    item {
                        TextPreferenceWidget(
                            title = stringResource(MR.strings.helpful_translation_links),
                            onPreferenceClick = { uriHandler.openUri("https://mihon.app/docs/contribute#helpful-links") },
                        )
                    }

                    item {
                        TextPreferenceWidget(
                            title = stringResource(MR.strings.open_source_licenses),
                            onPreferenceClick = { navigator.push(AboutLicenseScreen()) },
                        )
                    }

                    item {
                        FlowRow(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            LinkIcon(
                                label = "Website",
                                icon = Icons.Outlined.Public,
                                url = "https://mihon.app",
                            )
                            LinkIcon(
                                label = "Discord",
                                icon = CustomIcons.Discord,
                                url = "https://discord.gg/mihon",
                            )
                            LinkIcon(
                                label = "GitHub",
                                icon = CustomIcons.GitHub,
                                url = "https://github.com/null2264/yokai",
                            )
                        }
                    }
                }
            },
        )
    }

    private fun getVersionName(): String = when {
        BuildConfig.DEBUG -> "Debug ${BuildConfig.COMMIT_SHA}"
        BuildConfig.NIGHTLY -> "Nightly ${BuildConfig.COMMIT_COUNT} (${BuildConfig.COMMIT_SHA})"
        else -> "Release ${BuildConfig.VERSION_NAME}"
    }

    private fun Context.toastIfNotUserPrompt(message: StringResource, isUserPrompt: Boolean) {
        toastIfNotUserPrompt(getString(message), isUserPrompt)
    }

    private fun Context.toastIfNotUserPrompt(message: String?, isUserPrompt: Boolean) {
        if (!isUserPrompt) return
        toast(message)
    }

    private suspend fun Context.checkVersion(dialogState: DialogHostState, isUserPrompt: Boolean, notificationPrompt: () -> Unit = {}) {
        val updateChecker = AppUpdateChecker()

        withUIContext { toastIfNotUserPrompt(MR.strings.searching_for_updates, isUserPrompt) }

        val result = try {
            updateChecker.checkForUpdate(this, isUserPrompt)
        } catch (error: Exception) {
            withUIContext {
                toastIfNotUserPrompt(error.message, isUserPrompt)
                Logger.e(error) { "Couldn't check new update" }
            }
        }
        when (result) {
            is AppUpdateResult.NewUpdate -> {
                val data = NewUpdateData(
                    result.release.info,
                    result.release.downloadLink,
                    result.release.preRelease == true
                )

                // Create confirmation window
                withUIContext {
                    if (!isUserPrompt) { notificationPrompt() }
                    AppUpdateNotifier.releasePageUrl = result.release.releaseLink
                    dialogState.awaitNewUpdateDialog(data)
                }
            }
            is AppUpdateResult.NoNewUpdate -> {
                withUIContext { toastIfNotUserPrompt(MR.strings.no_new_updates_available, isUserPrompt) }
            }
        }
    }
}

fun getFormattedBuildTime(dateFormat: DateFormat): String {
    try {
        val inputDf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.getDefault())
        inputDf.timeZone = TimeZone.getTimeZone("UTC")
        val buildTime =
            inputDf.parse(BuildConfig.BUILD_TIME) ?: return BuildConfig.BUILD_TIME

        return buildTime.toTimestampString(dateFormat)
    } catch (e: ParseException) {
        return BuildConfig.BUILD_TIME
    }
}

private const val SOURCE_URL = "https://github.com/null2264/yokai/commits/master"
