package yokai.presentation

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import dev.icerock.moko.resources.compose.stringResource
import yokai.i18n.MR
import yokai.presentation.component.ToolTipButton
import yokai.presentation.core.ExpandedAppBar
import yokai.presentation.core.TopAppBar
import yokai.presentation.core.TopAppBarScrollBehavior
import yokai.presentation.core.enterAlwaysScrollBehavior
import yokai.presentation.core.rememberTopAppBarState

@Composable
fun YokaiScaffold(
    onNavigationIconClicked: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "",
    scrollBehavior: TopAppBarScrollBehavior? = null,
    fab: @Composable () -> Unit = {},
    navigationIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    navigationIconLabel: String = stringResource(MR.strings.back),
    actions: @Composable RowScope.() -> Unit = {},
    appBarType: AppBarType = AppBarType.LARGE,
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehaviorOrDefault = scrollBehavior ?: enterAlwaysScrollBehavior(state = rememberTopAppBarState())
    val view = LocalView.current
    val useDarkIcons = MaterialTheme.colorScheme.surface.luminance() > .5
    val (color, scrolledColor) = getTopAppBarColor(title)

    SideEffect {
        val activity  = view.context as Activity
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.window.statusBarColor = Color.Transparent.toArgb()
            WindowInsetsControllerCompat(activity.window, view).isAppearanceLightStatusBars = useDarkIcons
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehaviorOrDefault.nestedScrollConnection),
        floatingActionButton = fab,
        topBar = {
            when (appBarType) {
                AppBarType.SMALL -> TopAppBar(
                    title = {
                        Text(
                            modifier = Modifier.basicMarquee(),
                            text = title,
                        )
                    },
                    // modifier = Modifier.statusBarsPadding(),
                    colors = topAppBarColors(
                        containerColor = color,
                        scrolledContainerColor = scrolledColor,
                    ),
                    navigationIcon = {
                        ToolTipButton(
                            toolTipLabel = navigationIconLabel,
                            icon = navigationIcon,
                            buttonClicked = onNavigationIconClicked,
                        )
                    },
                    scrollBehavior = scrollBehaviorOrDefault,
                    actions = actions,
                )
                AppBarType.LARGE -> ExpandedAppBar(
                    title = {
                        Text(
                            modifier = Modifier.basicMarquee(),
                            text = title,
                        )
                    },
                    // modifier = Modifier.statusBarsPadding(),
                    colors = topAppBarColors(
                        containerColor = color,
                        scrolledContainerColor = scrolledColor,
                    ),
                    navigationIcon = {
                        ToolTipButton(
                            toolTipLabel = navigationIconLabel,
                            icon = navigationIcon,
                            buttonClicked = onNavigationIconClicked,
                        )
                    },
                    scrollBehavior = scrollBehaviorOrDefault,
                    actions = actions,
                )
                AppBarType.NONE -> {}
            }
        },
        snackbarHost = snackbarHost,
        content = content,
    )
}

@Composable
fun getTopAppBarColor(title: String): Pair<Color, Color> {
    return when (title.isEmpty()) {
        true -> Color.Transparent to Color.Transparent
        false -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.primaryContainer
    }
}

enum class AppBarType {
    // FIXME: Delete "NONE" later
    NONE,
    SMALL,
    LARGE,
}
