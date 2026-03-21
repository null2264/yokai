package eu.kanade.tachiyomi.ui.setting

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.CrossfadeTransition
import eu.kanade.tachiyomi.ui.base.controller.BaseComposeController
import yokai.presentation.settings.ComposableSettings

abstract class SettingsComposeController: BaseComposeController(), SettingsControllerInterface {
    override fun getTitle(): String? = __getTitle()
    override fun getSearchTitle(): String? = __getTitle()

    fun setTitle() = __setTitle()

    abstract fun getComposableSettings(): ComposableSettings

    @Composable
    override fun ScreenContent() {
        Navigator(
            screen = getComposableSettings(),
            content = {
                CrossfadeTransition(navigator = it)
            },
        )
    }
}
