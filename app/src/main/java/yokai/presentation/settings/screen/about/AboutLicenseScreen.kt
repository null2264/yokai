package yokai.presentation.settings.screen.about

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.LocalNavigator
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.util.htmlReadyLicenseContent
import dev.icerock.moko.resources.compose.stringResource
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.compose.LocalBackPress
import eu.kanade.tachiyomi.util.compose.currentOrThrow
import yokai.i18n.MR
import yokai.presentation.AppBarType
import yokai.presentation.YokaiScaffold
import yokai.presentation.core.pinnedAppBarScrollBehavior
import yokai.util.Screen

class AboutLicenseScreen : Screen() {
    @Composable
    override fun Content() {
        val libraries by produceLibraries(R.raw.aboutlibraries)
        val navigator = LocalNavigator.currentOrThrow
        val backPress = LocalBackPress.currentOrThrow

        YokaiScaffold(
            onNavigationIconClicked = backPress,
            title = stringResource(MR.strings.open_source_licenses),
            appBarType = AppBarType.SMALL,
            scrollBehavior = pinnedAppBarScrollBehavior(),
        ) { innerPadding ->
            LibrariesContainer(
                libraries = libraries,
                modifier = Modifier.fillMaxSize(),
                contentPadding = innerPadding,
                onLibraryClick = {
                    navigator.push(
                        AboutLibraryLicenseScreen(
                            it.name,
                            it.website,
                            it.licenses.firstOrNull()?.htmlReadyLicenseContent.orEmpty(),
                        ),
                    )
                }
            )
        }
    }
}
