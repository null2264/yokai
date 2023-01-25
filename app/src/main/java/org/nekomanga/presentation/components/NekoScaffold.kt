package org.nekomanga.presentation.components

import ToolTipButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.kanade.tachiyomi.R
import org.nekomanga.presentation.screens.ThemeColorState
import org.nekomanga.presentation.screens.defaultThemeColorState

@Composable
fun NekoScaffold(
    title: String,
    onNavigationIconClicked: () -> Unit,
    modifier: Modifier = Modifier,
    themeColorState: ThemeColorState = defaultThemeColorState(),
    incognitoMode: Boolean = false,
    isRoot: Boolean = false,
    scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(),
    navigationIcon: ImageVector = Icons.Filled.ArrowBack,
    navigationIconLabel: String = stringResource(id = R.string.back),
    subtitle: String = "",
    snackBarHost: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit = {},
) {
    val systemUiController = rememberSystemUiController()
    val useDarkIcons = MaterialTheme.colorScheme.surface.luminance() > .5
    val color = getTopAppBarColor(title)
    SideEffect {
        systemUiController.setStatusBarColor(color, darkIcons = useDarkIcons)
    }
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = snackBarHost,
        topBar =
        {
            CompositionLocalProvider(LocalRippleTheme provides (themeColorState.rippleTheme)) {
                if (subtitle.isEmpty() && title.isNotEmpty()) {
                    TitleOnlyTopAppBar(color, title, navigationIconLabel, navigationIcon, onNavigationIconClicked, actions, incognitoMode, isRoot, scrollBehavior)
                } else if (title.isEmpty()) {
                    NoTitleTopAppBar(color, navigationIconLabel, navigationIcon, onNavigationIconClicked, actions, scrollBehavior)
                } else {
                    TitleAndSubtitleTopAppBar(color, title, subtitle, navigationIconLabel, navigationIcon, onNavigationIconClicked, actions, scrollBehavior)
                }
            }
        },
    ) { paddingValues ->
        CompositionLocalProvider(LocalRippleTheme provides PrimaryColorRippleTheme) {
            content(paddingValues)
        }
    }
}

@Composable
private fun TitleAndSubtitleTopAppBar(
    color: Color,
    title: String,
    subtitle: String,
    navigationIconLabel: String,
    navigationIcon: ImageVector,
    onNavigationIconClicked: () -> Unit,
    actions: @Composable (RowScope.() -> Unit),
    scrollBehavior: TopAppBarScrollBehavior,
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        modifier = Modifier.statusBarsPadding(),
        navigationIcon = {
            ToolTipButton(
                toolTipLabel = navigationIconLabel,
                icon = navigationIcon,
                buttonClicked = onNavigationIconClicked,
            )
        },
        actions = actions,
        colors = TopAppBarDefaults.smallTopAppBarColors(
            containerColor = color,
            scrolledContainerColor = color,
        ),
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun NoTitleTopAppBar(
    color: Color,
    navigationIconLabel: String,
    navigationIcon: ImageVector,
    onNavigationIconClicked: () -> Unit,
    actions: @Composable() (RowScope.() -> Unit),
    scrollBehavior: TopAppBarScrollBehavior,
) {
    TopAppBar(
        title = {},
        modifier = Modifier.statusBarsPadding(),
        navigationIcon = {
            ToolTipButton(
                toolTipLabel = navigationIconLabel,
                icon = navigationIcon,
                buttonClicked = onNavigationIconClicked,
            )
        },
        actions = actions,
        colors = TopAppBarDefaults.smallTopAppBarColors(
            containerColor = color,
            scrolledContainerColor = color,
        ),
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun TitleOnlyTopAppBar(
    color: Color,
    title: String,
    navigationIconLabel: String,
    navigationIcon: ImageVector,
    onNavigationIconClicked: () -> Unit,
    actions: @Composable (RowScope.() -> Unit),
    incognitoMode: Boolean,
    isRoot: Boolean,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.smallTopAppBarColors(
            containerColor = color,
            scrolledContainerColor = color,
        ),
        modifier = Modifier.statusBarsPadding(),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            if (incognitoMode) {
                Image(
                    CommunityMaterial.Icon2.cmd_incognito_circle,
                    colorFilter = ColorFilter.tint(LocalContentColor.current),
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(32.dp),
                )
            } else if (!isRoot) {
                ToolTipButton(
                    toolTipLabel = navigationIconLabel,
                    icon = navigationIcon,
                    buttonClicked = onNavigationIconClicked,
                )
            }
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
    )
}

@Composable
fun getTopAppBarColor(title: String): Color {
    return when (title.isEmpty()) {
        true -> Color.Transparent
        false -> MaterialTheme.colorScheme.surface.copy(alpha = .7f)
    }
}