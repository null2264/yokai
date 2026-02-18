package eu.kanade.tachiyomi.source

import android.graphics.drawable.Drawable
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.online.HttpSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

fun Source.includeLangInName(enabledLanguages: Set<String>, extensionManager: ExtensionManager? = null): Boolean {
    val httpSource = this as? HttpSource ?: return true
    val extManager = extensionManager ?: Injekt.get()
    val allExt = httpSource.getExtension(extManager)?.lang == "all"
    val onlyAll = httpSource.extOnlyHasAllLanguage(extManager)
    val isMultiLingual = enabledLanguages.filterNot { it == "all" }.size > 1
    return (isMultiLingual && allExt) || (lang == "all" && !onlyAll)
}

fun Source.nameBasedOnEnabledLanguages(enabledLanguages: Set<String>, extensionManager: ExtensionManager? = null): String {
    return if (includeLangInName(enabledLanguages, extensionManager)) toString() else name
}

fun Source.icon(): Drawable? = Injekt.get<ExtensionManager>().getAppIconForSource(this)

fun Source.pkgName() = Injekt.get<ExtensionManager>().getPackageName(this.id)
fun HttpSource.getExtension(extensionManager: ExtensionManager? = null): Extension.Installed? =
    (extensionManager ?: Injekt.get()).installedExtensionsFlow.value.find { it.sources.contains(this) }

fun HttpSource.extOnlyHasAllLanguage(extensionManager: ExtensionManager? = null) =
    getExtension(extensionManager)?.sources?.all { it.lang == "all" } ?: true
