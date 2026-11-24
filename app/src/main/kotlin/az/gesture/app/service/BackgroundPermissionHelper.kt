package az.gesture.app.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import az.gesture.app.R

enum class AutoStartTargetKind(@param:StringRes val roadmapRes: Int) {
    XIAOMI_AUTOSTART(R.string.perm_roadmap_autostart_xiaomi),
    OEM_STARTUP_LIST(R.string.perm_roadmap_autostart_oem),
    SAMSUNG_BATTERY(R.string.perm_roadmap_autostart_samsung),
    APP_INFO_BATTERY(R.string.perm_roadmap_autostart_app_info)
}

enum class XiaomiLockTargetKind(@param:StringRes val roadmapRes: Int) {
    DIRECT_LOCK_LIST(R.string.perm_roadmap_xiaomi_lock_direct),
    SECURITY_APP(R.string.perm_roadmap_xiaomi_lock_security)
}

data class ResolvedSettingsTarget(
    val intent: Intent,
    val kind: AutoStartTargetKind
)

data class ResolvedXiaomiLockTarget(
    val intent: Intent,
    val kind: XiaomiLockTargetKind
)

object BackgroundPermissionHelper {

    fun isOverlayPermissionGranted(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun isNotificationPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun isBackgroundRestricted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return am.isBackgroundRestricted
    }

    fun openAccessibilitySettings(context: Context) {
        launchSafely(
            context,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        )
    }

    fun openOverlayPermissionSettings(context: Context) {
        val direct = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        if (!launchSafely(context, direct)) {
            launchSafely(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
    }

    fun openAppNotificationSettings(context: Context) {
        val direct = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        if (!launchSafely(context, direct)) {
            openAppInfo(context)
        }
    }

    @SuppressLint("BatteryLife")
    fun canLaunchDirectBatteryDialog(context: Context): Boolean {
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.fromParts("package", context.packageName, null)
        )
        return isResolvable(context, direct)
    }

    @SuppressLint("BatteryLife")
    fun openBatteryOptimizationSettings(context: Context) {
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.fromParts("package", context.packageName, null)
        )
        if (!launchSafely(context, direct)) {
            if (!launchSafely(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) {
                openAppInfo(context)
            }
        }
    }

    fun openAppInfo(context: Context) {
        launchSafely(
            context,
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
        )
    }

    /**
     * Resolves the exact OEM Auto-Start / Background Control activity installed on this device,
     * falling back cleanly to Veloa's App Info screen so the displayed roadmap always starts
     * on the exact first screen the user lands on.
     */
    fun resolveAutoStartTarget(context: Context): ResolvedSettingsTarget {
        val candidates = listOf(
            // Xiaomi / Redmi / POCO (MIUI & HyperOS)
            componentIntent(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ) to AutoStartTargetKind.XIAOMI_AUTOSTART,
            // OPPO / Realme / OnePlus (ColorOS & OxygenOS)
            componentIntent(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            ) to AutoStartTargetKind.OEM_STARTUP_LIST,
            componentIntent(
                "com.oplus.safecenter",
                "com.oplus.safecenter.permission.startup.StartupAppListActivity"
            ) to AutoStartTargetKind.OEM_STARTUP_LIST,
            componentIntent(
                "com.oneplus.security",
                "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
            ) to AutoStartTargetKind.OEM_STARTUP_LIST,
            // Vivo / iQOO
            componentIntent(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            ) to AutoStartTargetKind.OEM_STARTUP_LIST,
            componentIntent(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
            ) to AutoStartTargetKind.OEM_STARTUP_LIST,
            // Huawei / Honor
            componentIntent(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            ) to AutoStartTargetKind.OEM_STARTUP_LIST,
            componentIntent(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            ) to AutoStartTargetKind.OEM_STARTUP_LIST,
            // Samsung One UI
            componentIntent(
                "com.samsung.android.lool",
                "com.samsung.android.sm.battery.ui.BatteryActivity"
            ) to AutoStartTargetKind.SAMSUNG_BATTERY,
            componentIntent(
                "com.samsung.android.sm_cn",
                "com.samsung.android.sm.ui.ram.AutoRunActivity"
            ) to AutoStartTargetKind.OEM_STARTUP_LIST
        )

        for ((intent, kind) in candidates) {
            if (isResolvable(context, intent)) {
                return ResolvedSettingsTarget(intent, kind)
            }
        }

        val fallback = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
        return ResolvedSettingsTarget(fallback, AutoStartTargetKind.APP_INFO_BATTERY)
    }

    fun openAutoStartSettings(context: Context) {
        val target = resolveAutoStartTarget(context)
        if (!launchSafely(context, target.intent)) {
            openAppInfo(context)
        }
    }

    /**
     * Xiaomi / HyperOS extra permission ("Display pop-up windows while running in the background")
     * needed so gestures can launch apps even after Veloa is completely swiped away.
     */
    fun hasXiaomiBackgroundPopupSetting(context: Context): Boolean {
        return isResolvable(context, buildXiaomiPopupIntent(context))
    }

    fun openXiaomiBackgroundPopupSetting(context: Context) {
        if (!launchSafely(context, buildXiaomiPopupIntent(context))) {
            openAppInfo(context)
        }
    }

    /**
     * Xiaomi / HyperOS Memory Cleaner Lock Apps ("Tətbiqləri kilidlə") whitelist so pressing "X"
     * (Clear All / Boost Speed) in Recents does NOT kill Veloa.
     */
    fun resolveXiaomiMemoryLockTarget(context: Context): ResolvedXiaomiLockTarget? {
        val directLock = componentIntent(
            "com.miui.securitycenter",
            "com.miui.memoryclean.MemoryCleanLockActivity"
        )
        if (isResolvable(context, directLock)) {
            return ResolvedXiaomiLockTarget(directLock, XiaomiLockTargetKind.DIRECT_LOCK_LIST)
        }
        val securityMain = componentIntent(
            "com.miui.securitycenter",
            "com.miui.securityscan.MainActivity"
        )
        if (isResolvable(context, securityMain)) {
            return ResolvedXiaomiLockTarget(securityMain, XiaomiLockTargetKind.SECURITY_APP)
        }
        return null
    }

    fun openXiaomiMemoryLockSetting(context: Context) {
        val target = resolveXiaomiMemoryLockTarget(context)
        if (target == null || !launchSafely(context, target.intent)) {
            openAppInfo(context)
        }
    }

    private fun buildXiaomiPopupIntent(context: Context): Intent {
        return Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.PermissionsEditorActivity"
            )
            putExtra("extra_pkgname", context.packageName)
        }
    }

    private fun componentIntent(pkg: String, cls: String): Intent {
        return Intent().apply {
            component = ComponentName(pkg, cls)
        }
    }

    private fun isResolvable(context: Context, intent: Intent): Boolean {
        return try {
            val info = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            info?.activityInfo?.exported == true
        } catch (_: Throwable) {
            false
        }
    }

    private fun launchSafely(context: Context, intent: Intent): Boolean {
        return try {
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Throwable) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }
}
