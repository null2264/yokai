package eu.kanade.tachiyomi.util.system

import android.app.LocaleManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import co.touchlab.kermit.Logger
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.App
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.util.ExtensionLoader
import eu.kanade.tachiyomi.ui.main.MainActivity
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.i18n.MR

private const val TABLET_UI_MIN_SCREEN_WIDTH_DP = 720

/**
 * Helper method to create a notification.
 *
 * @param channelId the channel id.
 * @param func the function that will execute inside the builder.
 * @return a notification to be displayed or updated.
 */
inline fun Context.notification(channelId: String, func: NotificationCompat.Builder.() -> Unit): Notification {
    val builder = NotificationCompat.Builder(this, channelId)
    builder.func()
    return builder.build()
}

/**
 * Returns the color for the given attribute.
 *
 * @param resource the attribute.
 */
@ColorInt
fun Context.getResourceColor(@AttrRes resource: Int): Int {
    val typedArray = obtainStyledAttributes(intArrayOf(resource))
    val attrValue = typedArray.getColor(0, 0)
    typedArray.recycle()
    return attrValue
}

fun Context.getResourceDrawable(@AttrRes resource: Int): Drawable? {
    val typedArray = obtainStyledAttributes(intArrayOf(resource))
    val attrValue = typedArray.getDrawable(0)
    typedArray.recycle()
    return attrValue
}

/**
 * Returns the color from ContextCompat
 *
 * @param resource the color.
 */
fun Context.contextCompatColor(@ColorRes resource: Int): Int {
    return ContextCompat.getColor(this, resource)
}

/**
 * Returns the color from ContextCompat
 *
 * @param resource the color.
 */
fun Context.contextCompatDrawable(@DrawableRes resource: Int): Drawable? {
    return ContextCompat.getDrawable(this, resource)
}

/** Converts to px and takes into account LTR/RTL layout */
fun Float.dpToPxEnd(resources: Resources): Float {
    return this * resources.displayMetrics.density * if (resources.isLTR) 1 else -1
}

val Resources.isLTR
    get() = configuration.layoutDirection == View.LAYOUT_DIRECTION_LTR

fun Context.isTablet() = resources.configuration.smallestScreenWidthDp >= 600

val displayMaxHeightInPx: Int
    get() = Resources.getSystem().displayMetrics.let { max(it.heightPixels, it.widthPixels) }

/** Gets the duration multiplier for general animations on the device
 * @see Settings.Global.ANIMATOR_DURATION_SCALE
 */
val Context.animatorDurationScale: Float
    get() = Settings.Global.getFloat(this.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

/**
 * Helper method to create a notification builder.
 *
 * @param channelId the channel id.
 * @param block the function that will execute inside the builder.
 * @return a notification to be displayed or updated.
 */
fun Context.notificationBuilder(
    channelId: String,
    block: (NotificationCompat.Builder.() -> Unit)? = null,
): NotificationCompat.Builder {
    val builder = NotificationCompat.Builder(this, channelId)
        .setColor(ContextCompat.getColor(this, R.color.secondaryTachiyomi))
    if (block != null) {
        builder.block()
    }
    return builder
}

fun Context.prepareSideNavContext(): Context {
    val configuration = resources.configuration
    val expected = when (Injekt.get<PreferencesHelper>().sideNavMode().get()) {
        SideNavMode.ALWAYS.prefValue -> true
        SideNavMode.NEVER.prefValue -> false
        else -> null
    }
    if (expected != null) {
        val overrideConf = Configuration()
        overrideConf.setTo(configuration)
        overrideConf.screenWidthDp = if (expected) {
            overrideConf.screenWidthDp.coerceAtLeast(TABLET_UI_MIN_SCREEN_WIDTH_DP)
        } else {
            overrideConf.screenWidthDp.coerceAtMost(TABLET_UI_MIN_SCREEN_WIDTH_DP - 1)
        }
        return createConfigurationContext(overrideConf)
    }
    return this
}

fun Context.withOriginalWidth(): Context {
    val width = (this as? MainActivity)?.ogWidth ?: resources.configuration.screenWidthDp
    val configuration = resources.configuration
    val overrideConf = Configuration()
    overrideConf.setTo(configuration)
    overrideConf.screenWidthDp = width
    resources.configuration.updateFrom(overrideConf)
    return this
}

fun Context.extensionIntentForText(text: String): Intent? {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(text))
    val info = packageManager.queryIntentActivitiesCompat(intent, PackageManager.MATCH_ALL)
        .firstOrNull {
            try {
                val pkgName = it.activityInfo.packageName
                ExtensionLoader.isPackageNameAnExtension(packageManager, pkgName)
            } catch (_: Exception) {
                false
            }
        } ?: return null
    intent.setClassName(info.activityInfo.packageName, info.activityInfo.name)
    return intent
}

fun Context.isLandscape(): Boolean {
    return resources.configuration?.orientation == Configuration.ORIENTATION_LANDSCAPE
}

/**
 * Gets document size of provided [Uri]
 *
 * @return document size of [uri] or null if size can't be obtained
 */
fun Context.getUriSize(uri: Uri): Long? {
    return UniFile.fromUri(this, uri)!!.length().takeIf { it >= 0 }
}

/**
 * Returns true if [packageName] is installed.
 */
fun Context.isPackageInstalled(packageName: String): Boolean {
    return try {
        packageManager.getApplicationInfoCompat(packageName, 0)
        true
    } catch (_: Exception) {
        false
    }
}

/**
 * Property to get the notification manager from the context.
 */
val Context.notificationManager: NotificationManager
    get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

/**
 * Property to get the connectivity manager from the context.
 */
val Context.connectivityManager: ConnectivityManager
    get() = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

val Context.wifiManager: WifiManager
    get() = getSystemService()!!

/**
 * Property to get the power manager from the context.
 */
val Context.powerManager: PowerManager
    get() = getSystemService()!!

val Context.workManager: WorkManager
    get() = WorkManager.getInstance(this)

/**
 * Returns true if device is connected to Wifi.
 */
fun Context.isConnectedToWifi(): Boolean {
    if (!wifiManager.isWifiEnabled) return false

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } else {
        @Suppress("DEPRECATION")
        wifiManager.connectionInfo.bssid != null
    }
}

fun Context.openInBrowser(url: String, @ColorInt toolbarColor: Int? = null, forceBrowser: Boolean = false) {
    this.openInBrowser(url.toUri(), toolbarColor, forceBrowser)
}

fun Context.openInBrowser(uri: Uri, @ColorInt toolbarColor: Int? = null, forceBrowser: Boolean = false) {
    try {
        val intent = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(
                CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(toolbarColor ?: getResourceColor(R.attr.colorPrimaryVariant))
                    .build(),
            )
            .build()
        if (forceBrowser) {
            val packages = getCustomTabsPackages().maxByOrNull { it.preferredOrder }
            val processName = packages?.activityInfo?.processName
            if (processName == null) {
                intent.intent.`package` = processName
            }
        }
        intent.launchUrl(this, uri)
    } catch (e: Exception) {
        toast(e.message)
    }
}

/**
 * Opens a URL in a custom tab.
 */
fun Context.openInBrowser(url: String, forceBrowser: Boolean, fullBrowser: Boolean = true): Boolean {
    try {
        val parsedUrl = url.toUri()
        if (forceBrowser && fullBrowser) {
            val intent = Intent(Intent.ACTION_VIEW, parsedUrl).apply {
                defaultBrowserPackageName()?.let { setPackage(it) }
            }
            startActivity(intent)
        } else {
            val intent = CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(
                    CustomTabColorSchemeParams.Builder()
                        .setToolbarColor(getResourceColor(R.attr.colorPrimaryVariant))
                        .build(),
                )
                .build()
            if (forceBrowser) {
                val packages = getCustomTabsPackages().maxByOrNull { it.preferredOrder }
                val processName = packages?.activityInfo?.processName ?: return false
                intent.intent.`package` = processName
            }
            intent.launchUrl(this, parsedUrl)
        }
        return true
    } catch (e: Exception) {
        toast(e.message)
        return false
    }
}

fun Context.defaultBrowserPackageName(): String? {
    val browserIntent = Intent(Intent.ACTION_VIEW, "http://".toUri())
    return packageManager.resolveActivityCompat(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo?.packageName
        ?.takeUnless { it in DeviceUtil.invalidDefaultBrowsers }
}

/**
 * Returns a list of packages that support Custom Tabs.
 */
fun Context.getCustomTabsPackages(): ArrayList<ResolveInfo> {
    // Get default VIEW intent handler.
    val activityIntent = Intent(Intent.ACTION_VIEW, "https://www.example.com".toUri())
    // Get all apps that can handle VIEW intents.
    val resolvedActivityList = packageManager.queryIntentActivitiesCompat(activityIntent, 0)
    val packagesSupportingCustomTabs = ArrayList<ResolveInfo>()
    for (info in resolvedActivityList) {
        val serviceIntent = Intent()
        serviceIntent.action = ACTION_CUSTOM_TABS_CONNECTION
        serviceIntent.setPackage(info.activityInfo.packageName)
        // Check if this package also resolves the Custom Tabs service.
        if (packageManager.resolveServiceCompat(serviceIntent, 0) != null) {
            packagesSupportingCustomTabs.add(info)
        }
    }
    return packagesSupportingCustomTabs
}

fun Context.isInNightMode(): Boolean {
    val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return currentNightMode == Configuration.UI_MODE_NIGHT_YES
}

fun Context.appDelegateNightMode(): Int {
    return if (isInNightMode()) {
        AppCompatDelegate.MODE_NIGHT_YES
    } else {
        AppCompatDelegate.MODE_NIGHT_NO
    }
}

fun Context.isOnline(): Boolean {
    val networkCapabilities = connectivityManager.activeNetwork ?: return false
    val actNw = connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return false
    val maxTransport = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 -> NetworkCapabilities.TRANSPORT_LOWPAN
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> NetworkCapabilities.TRANSPORT_WIFI_AWARE
        else -> NetworkCapabilities.TRANSPORT_VPN
    }
    return (NetworkCapabilities.TRANSPORT_CELLULAR..maxTransport).any(actNw::hasTransport)
}

fun Context.createFileInCacheDir(name: String): File {
    val file = File(externalCacheDir, name)
    if (file.exists()) {
        file.delete()
    }
    file.createNewFile()
    return file
}

fun Context.getApplicationIcon(pkgName: String): Drawable? {
    return try {
        packageManager.getApplicationIcon(pkgName)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}

/** Context used for notifications as Appcompat app lang does not support notifications */
val Context.localeContext: Context
    get() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return this
        val pref = Injekt.get<PreferencesHelper>()
        val prefsLang = if (pref.appLanguage().isSet()) {
            Locale.forLanguageTag(pref.appLanguage().get())
        } else {
            null
        }
        val configuration = Configuration(resources.configuration)
        configuration.setLocale(
            prefsLang
                ?: AppCompatDelegate.getApplicationLocales()[0]
                ?: Locale.getDefault(),
        )
        return createConfigurationContext(configuration)
    }

fun setLocaleByAppCompat() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        AppCompatDelegate.getApplicationLocales().get(0)?.let { Locale.setDefault(it) }
    }
}

suspend fun CoroutineWorker.tryToSetForeground() {
    try {
        setForeground(getForegroundInfo())
        delay(1000)
    } catch (e: IllegalStateException) {
        Logger.e(e) { "Not allowed to set foreground job" }
    }
}

fun WorkManager.jobIsRunning(tag: String): Boolean = getWorkInfosForUniqueWork(tag).get()
    .let { list -> list.count { it.state == WorkInfo.State.RUNNING } == 1 }

val Context.systemLangContext: Context
    get() {
        val configuration = Configuration(resources.configuration)

        val systemLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getSystemService<LocaleManager>()?.systemLocales?.get(0)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Resources.getSystem().configuration.locales.get(0)
            } else {
                return this
            } ?: Locale.getDefault()
        }
        configuration.setLocale(systemLocale)
        return createConfigurationContext(configuration)
    }

val Context.application: App
    get() = applicationContext as App

suspend fun <T> withNonCancellableContext(block: suspend CoroutineScope.() -> T) =
    withContext(NonCancellable, block)

fun Context.tryTakePersistableUriPermission(uri: Uri, flags: Int) = try {
    contentResolver.takePersistableUriPermission(uri, flags)
} catch (e: SecurityException) {
    Logger.e(e) { "Persists URI permission is not supported in this device" }
    toast(MR.strings.file_picker_uri_permission_unsupported)
}
