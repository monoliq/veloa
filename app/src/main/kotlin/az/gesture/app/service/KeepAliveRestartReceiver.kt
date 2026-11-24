package az.gesture.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Revives VeloaKeepAliveService if the OS or an OEM memory cleaner terminates the process
 * while Veloa's Accessibility Service is still enabled in Android settings.
 */
class KeepAliveRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        try {
            if (VeloaKeepAliveService.shouldStayAlive(context)) {
                VeloaKeepAliveService.start(context)
                if (GestureAccessibilityService.instance != null) {
                    GestureAccessibilityService.instance?.refreshOverlays()
                } else {
                    VeloaKeepAliveService.activateFallbackIfNeeded()
                }
            } else {
                VeloaKeepAliveService.cancelWatchdogRestart(context)
                VeloaWatchdogJobService.cancelAll(context)
            }
        } catch (_: Throwable) {
        }
    }
}
