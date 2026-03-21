package eu.kanade.tachiyomi.ui.setting.controllers

import eu.kanade.tachiyomi.ui.setting.SettingsComposeController
import yokai.presentation.settings.ComposableSettings
import yokai.presentation.settings.screen.SettingsAdvancedScreen

class SettingsAdvancedController : SettingsComposeController() {
    override fun getComposableSettings(): ComposableSettings = SettingsAdvancedScreen
}
