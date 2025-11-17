package yokai.presentation.core

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * Composable replacement for Jay's [eu.kanade.tachiyomi.ui.base.ExpandedAppBarLayout]
 *
 * Based on (M3 v1.5.0's) [androidx.compose.material3.AppBarWithSearch] implementation with a mix of
 * [androidx.compose.material3.LargeTopAppBar] implementation
 *
 * if [useLargeToolbar] is enabled, [JayTopAppBar] should be used instead.
 */
@Composable
fun JayExpandedTopAppBar(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    titleTextStyle: TextStyle = MaterialTheme.typography.headlineMedium,
    smallTitleTextStyle: TextStyle = MaterialTheme.typography.titleLarge,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(all = 0.dp),
    windowInsets: WindowInsets = SearchBarDefaults.windowInsets,
    scrollBehavior: JayAppBarScrollBehavior? = null,
    textFieldState: TextFieldState? = null,
    searchResult: @Composable (ColumnScope.() -> Unit)? = null,
) {
    val titleTextFontSizePx: Float
    LocalDensity.current.run {
        titleTextFontSizePx = titleTextStyle.fontSize.toPx()
    }

    val appBarContainerColor = {
        if ((scrollBehavior?.overlappedFraction() ?: 0f) > 0.01f) colors.scrolledContainerColor else colors.containerColor
    }

    val titleAlpha = {
        scrollBehavior?.bottomCollapsedFraction()?.let {
            TitleAlphaEasing.transform(it)
        } ?: 0f
    }

    val bottomTitleAlpha = {
        1f - (scrollBehavior?.bottomCollapsedFraction(titleTextFontSizePx) ?: 0f)
    }

    Column(
        modifier =
            Modifier
                .semantics { isTraversalGroup = true }
                .pointerInput(Unit) {}
    ) {
        Column(
            modifier = Modifier.drawBehind { drawRect(color = appBarContainerColor()) }
        ) {
            Surface(
                color = Color.Transparent,
                modifier =
                    modifier
                        .then(scrollBehavior?.let { with(it) { Modifier.smallAppBarScrollBehavior() } } ?: Modifier)
                        .onSizeChanged { scrollBehavior?.topHeightPx = it.height.toFloat() }
                        .fillMaxWidth()
                        .windowInsetsPadding(windowInsets)
            ) {
                Row(
                    modifier = Modifier.padding(contentPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    navigationIcon?.let {
                        Box(Modifier.padding(start = 4.dp)) {
                            it()
                        }
                    }
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .alpha(titleAlpha())
                    ) {
                        ProvideContentColorTextStyle(
                            contentColor = colors.titleContentColor,
                            textStyle = smallTitleTextStyle,
                            content = title
                        )
                    }
                    actions?.let {
                        // Wrap the given action icons in a Row.
                        val actionsRow =
                            @Composable {
                                Row(
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                    content = it,
                                )
                            }
                        Box(Modifier.padding(end = 4.dp)) {
                            CompositionLocalProvider(
                                LocalContentColor provides colors.actionIconContentColor,
                                content = actionsRow,
                            )
                        }
                    }
                }
            }
            Surface(
                color = Color.Transparent,
                modifier =
                    modifier
                        .then(scrollBehavior?.let { with(it) { Modifier.largeAppBarScrollBehavior() } } ?: Modifier)
                        .onSizeChanged { scrollBehavior?.bottomHeightPx = it.height.toFloat() }
                        .fillMaxWidth()
                        .semantics { isTraversalGroup = true },
            ) {
                Box(
                    modifier =
                        Modifier
                            .padding(start = 16.dp, top = 64.dp)
                            .weight(1f)
                            .alpha(bottomTitleAlpha())
                ) {
                    ProvideContentColorTextStyle(
                        contentColor = colors.titleContentColor,
                        textStyle = titleTextStyle,
                        content = title
                    )
                }
            }
        }

        if (textFieldState != null) {
            var expanded by rememberSaveable { mutableStateOf(false) }

            Surface(
                color = Color.Transparent,
                modifier =
                    modifier
                        .then(scrollBehavior?.let { with(it) { Modifier.searchAppBarScrollBehavior() } } ?: Modifier)
                        .onSizeChanged { scrollBehavior?.searchHeightPx = it.height.toFloat() }
                        .fillMaxWidth()
                        .semantics { isTraversalGroup = true },
            ) {
                SearchBar(
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = textFieldState.text.toString(),
                            onQueryChange = { textFieldState.edit { replace(0, length, it) } },
                            onSearch = {
                                // TODO
                                expanded = false
                            },
                            expanded = if (searchResult != null) expanded else false,
                            onExpandedChange = { expanded = it },
                            placeholder = { Text("Search") }  // TODO
                        )
                    },
                    expanded = if (searchResult != null) expanded else false,
                    onExpandedChange = { expanded = it },
                    content = searchResult ?: {},
                )
            }
        }
    }
}

/**
 * Composable replacement for Jay's [eu.kanade.tachiyomi.ui.base.ExpandedAppBarLayout]
 *
 * Based on (M3 v1.5.0's) [androidx.compose.material3.AppBarWithSearch] implementation with a mix of
 * [androidx.compose.material3.LargeTopAppBar] implementation
 */
@Composable
fun JayTopAppBar(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    titleTextStyle: TextStyle = MaterialTheme.typography.titleLarge,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(all = 0.dp),
    windowInsets: WindowInsets = SearchBarDefaults.windowInsets,
    scrollBehavior: JayAppBarScrollBehavior? = null,
    textFieldState: TextFieldState? = null,
    searchResult: @Composable (ColumnScope.() -> Unit)? = null,
) {

    val appBarContainerColor = {
        if ((scrollBehavior?.overlappedFraction() ?: 0f) > 0.01f) colors.scrolledContainerColor else colors.containerColor
    }

    Box(
        modifier =
            modifier
                .then(if (textFieldState == null) Modifier.drawBehind { drawRect(color = appBarContainerColor()) } else Modifier)
                .semantics { isTraversalGroup = true }
                .pointerInput(Unit) {}
    ) {
        if (textFieldState != null) {
            var expanded by rememberSaveable { mutableStateOf(false) }

            Surface(
                color = Color.Transparent,
                modifier =
                    modifier
                        .then(scrollBehavior?.let { with(it) { Modifier.appBarScrollBehavior() } } ?: Modifier)
                        .onSizeChanged { scrollBehavior?.scrollOffsetLimit = -it.height.toFloat() }
                        .fillMaxWidth()
                        .windowInsetsPadding(windowInsets)
            ) {
                SearchBar(
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = textFieldState.text.toString(),
                            onQueryChange = { textFieldState.edit { replace(0, length, it) } },
                            onSearch = {
                                // TODO
                                expanded = false
                            },
                            expanded = if (searchResult != null) expanded else false,
                            onExpandedChange = { expanded = it },
                            placeholder = { Text("Search") }  // TODO
                        )
                    },
                    expanded = if (searchResult != null) expanded else false,
                    onExpandedChange = { expanded = it },
                    content = searchResult ?: {},
                )
            }
        } else {
            Surface(
                color = Color.Transparent,
                modifier =
                    modifier
                        .then(scrollBehavior?.let { with(it) { Modifier.appBarScrollBehavior() } } ?: Modifier)
                        .onSizeChanged { scrollBehavior?.scrollOffsetLimit = -it.height.toFloat() }
                        .fillMaxWidth()
                        .windowInsetsPadding(windowInsets)
            ) {
                Row(
                    modifier = Modifier.padding(contentPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    navigationIcon?.let {
                        Box(Modifier.padding(start = 4.dp)) {
                            it()
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        ProvideContentColorTextStyle(
                            contentColor = colors.titleContentColor,
                            textStyle = titleTextStyle,
                            content = title
                        )
                    }
                    actions?.let {
                        // Wrap the given action icons in a Row.
                        val actionsRow =
                            @Composable {
                                Row(
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                    content = it,
                                )
                            }
                        Box(Modifier.padding(end = 4.dp)) {
                            CompositionLocalProvider(
                                LocalContentColor provides colors.actionIconContentColor,
                                content = actionsRow,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProvideContentColorTextStyle(
    contentColor: Color,
    textStyle: TextStyle,
    content: @Composable () -> Unit
) {
    val mergedStyle = LocalTextStyle.current.merge(textStyle)
    CompositionLocalProvider(
        LocalContentColor provides contentColor,
        LocalTextStyle provides mergedStyle,
        content = content
    )
}

internal val TitleAlphaEasing = CubicBezierEasing(.8f, 0f, .8f, .15f)
