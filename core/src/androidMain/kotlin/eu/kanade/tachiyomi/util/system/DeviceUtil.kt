package eu.kanade.tachiyomi.util.system

import android.annotation.SuppressLint
import android.app.Activity
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.Window
import androidx.core.content.getSystemService
import androidx.core.view.WindowInsetsCompat
import co.touchlab.kermit.Logger


object DeviceUtil {

    val isMiui by lazy {
        getSystemProperty("ro.miui.ui.version.name")?.isNotEmpty() ?: false
    }

    /**
     * Extracts the MIUI major version code from a string like "V12.5.3.0.QFGMIXM".
     *
     * @return MIUI major version code (e.g., 13) or null if can't be parsed.
     */
    val miuiMajorVersion by lazy {
        if (!isMiui) return@lazy null

        Build.VERSION.INCREMENTAL
            .substringBefore('.')
            .trimStart('V')
            .toIntOrNull()
    }

    @SuppressLint("PrivateApi")
    fun isMiuiOptimizationDisabled(): Boolean {
        val sysProp = getSystemProperty("persist.sys.miui_optimization")
        if (sysProp == "0" || sysProp == "false") {
            return true
        }

        return try {
            Class.forName("android.miui.AppOpsUtils")
                .getDeclaredMethod("isXOptMode")
                .invoke(null) as Boolean
        } catch (e: Exception) {
            false
        }
    }

    val isSamsung by lazy {
        Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    }

    val oneUiVersion by lazy {
        try {
            val semPlatformIntField = Build.VERSION::class.java.getDeclaredField("SEM_PLATFORM_INT")
            val version = semPlatformIntField.getInt(null) - 90000
            if (version < 0) {
                1.0
            } else {
                ((version / 10000).toString() + "." + version % 10000 / 100).toDouble()
            }
        } catch (e: Exception) {
            null
        }
    }

    val invalidDefaultBrowsers = listOf(
        "android",
        "com.hihonor.android.internal.app",
        "com.huawei.android.internal.app",
        "com.zui.resolver",
    )

    @SuppressLint("PrivateApi")
    private fun getSystemProperty(key: String?): String? {
        return try {
            Class.forName("android.os.SystemProperties")
                .getDeclaredMethod("get", String::class.java)
                .invoke(null, key) as String
        } catch (e: Exception) {
            Logger.w(e) { "Unable to use SystemProperties.get" }
            null
        }
    }

    val isVivo by lazy {
        val prop = getSystemProperty("ro.vivo.os.name")
        !prop.isNullOrBlank() &&
            prop.contains("funtouch", true)
    }

    fun setLegacyCutoutMode(window: Window, mode: LegacyCutoutMode) {
        when (mode) {
            LegacyCutoutMode.SHORT_EDGES -> {
                // Vivo doesn't support this, user had to set it from Settings
                /*
                if (isVivo) {
                }
                 */
            }
            LegacyCutoutMode.NEVER -> {
                // Vivo doesn't support this, user had to set it from Settings
                /*
                if (isVivo) {
                }
                 */
            }
        }
    }

    fun hasCutout(context: Activity?): CutoutSupport {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (context?.getSystemService<DisplayManager>()
                    ?.getDisplay(Display.DEFAULT_DISPLAY)?.cutout != null)
                return CutoutSupport.EXTENDED
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val displayCutout = context?.window?.decorView?.rootWindowInsets?.displayCutout
            if (displayCutout?.safeInsetTop != null || displayCutout?.safeInsetBottom != null)
                return CutoutSupport.MODERN
        } else if (isVivo) {
            // https://swsdl.vivo.com.cn/appstore/developer/uploadfile/20180328/20180328152252602.pdf
            try {
                @SuppressLint("PrivateApi")
                val ftFeature = context?.classLoader
                    ?.loadClass("android.util.FtFeature")
                val isFeatureSupportMethod = ftFeature?.getMethod(
                    "isFeatureSupport",
                    Int::class.javaPrimitiveType,
                )
                val isNotchOnScreen = 0x00000020
                val isSupported = isFeatureSupportMethod?.invoke(ftFeature, isNotchOnScreen) as Boolean
                if (isSupported) return CutoutSupport.LEGACY
            } catch (_: Exception) {
            }
        } else if (isMiui) {
            try {
                @SuppressLint("PrivateApi")
                val sysProp = context?.classLoader?.loadClass("android.os.SystemProperties")
                val method = sysProp?.getMethod("getInt", String::class.java, Int::class.javaPrimitiveType)
                val rt = method?.invoke(sysProp, "ro.miui.notch", 0) as Int
                if (rt == 1) return CutoutSupport.LEGACY
            } catch (_: Exception) {
            }
        }
        return CutoutSupport.NONE
    }

    fun getCutoutHeight(context: Activity?, cutoutSupport: CutoutSupport): Number {
        return when (cutoutSupport) {
            CutoutSupport.MODERN -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
                    throw IllegalStateException("Modern cutout only available on Android P or higher")
                context?.window?.decorView?.rootWindowInsets?.displayCutout?.safeInsetTop ?: 0
            }
            CutoutSupport.EXTENDED -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
                    throw IllegalStateException("Extended cutout only available on Android Q or higher")
                context?.window?.decorView?.rootWindowInsets?.displayCutout?.boundingRectTop?.height()?.toFloat() ?: 0f
            }
            CutoutSupport.LEGACY -> {
                if (isVivo) {
                    /*
                    // REF: https://github.com/SivanLiu/VivoFramework/blob/8d31381e/Vivo_y93/src/main/java/android/util/FtDeviceInfo.java#L28-L30
                    try {
                        @SuppressLint("PrivateApi")
                        val ftDeviceInfo = context?.classLoader
                            ?.loadClass("android.util.FtDeviceInfo")
                        val getEarHeightMethod = ftDeviceInfo?.getMethod(
                            "getEarHeight",
                            Context::class.java
                        )
                        val notchHeight = getEarHeightMethod?.invoke(ftDeviceInfo, context) as Int
                    } catch (_: Exception) {
                        // fallback
                    }
                    */

                    val insetCompat = context?.window?.decorView?.rootWindowInsets?.let {
                        WindowInsetsCompat.toWindowInsetsCompat(it)
                    }
                    val statusBarHeight = insetCompat?.getInsets(WindowInsetsCompat.Type.statusBars())?.top
                        ?: 24.dpToPx  // 24dp is "standard" height for Android since Marshmallow
                    var notchHeight = 32.dpToPx
                    if (notchHeight < statusBarHeight) {
                        notchHeight = statusBarHeight
                    }
                    notchHeight
                } else if (isMiui) {
                    val resourceId = context?.resources?.getIdentifier("notch_height",
                        "dimen", "android") ?: 0
                    if (resourceId > 0) {
                        context?.resources?.getDimensionPixelSize(resourceId) ?: 0
                    } else {
                        0
                    }
                } else {
                    0
                }
            }
            else -> 0
        }
    }

    enum class CutoutSupport {
        NONE,
        LEGACY,  // Pre-Android P, the start of this hell
        MODERN,  // Android P
        EXTENDED,  // Android Q
    }

    enum class LegacyCutoutMode {
        SHORT_EDGES,
        NEVER,
    }
}
