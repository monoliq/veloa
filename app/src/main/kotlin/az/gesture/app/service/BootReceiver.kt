package az.gesture.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restores Veloa's keep-alive foreground service automatically after device reboot or app update
 * whenever the user has enabled Veloa's Accessibility Service.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val relevant = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        if (!relevant) return

        try {
            if (VeloaKeepAliveService.shouldStayAlive(context)) {
                VeloaKeepAliveService.start(context)
                VeloaWatchdogJobService.schedulePeriodic(context)
            }
        } catch (_: Throwable) {
        }
    }
}
