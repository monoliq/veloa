package az.gesture.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import az.gesture.app.R
import az.gesture.app.data.ActionType
import az.gesture.app.data.GestureAction
import az.gesture.app.data.LocaleHelper

class ActionExecutor(
    private val context: Context,
    private val accessibilityService: AccessibilityService? = context as? AccessibilityService,
    private val onOpenGesturePad: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var torchCameraId: String? = null
    @Volatile
    private var isTorchOn: Boolean = false
    private var torchCallbackRegistered = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) {
                isTorchOn = enabled
            }
        }
    }

    init {
        initTorch()
    }

    private fun initTorch() {
        val cm = cameraManager ?: return
        try {
            val id = cm.cameraIdList.firstOrNull { camId ->
                val chars = cm.getCameraCharacteristics(camId)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK
            } ?: cm.cameraIdList.firstOrNull { camId ->
                cm.getCameraCharacteristics(camId)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            torchCameraId = id
            if (id != null) {
                cm.registerTorchCallback(torchCallback, handler)
                torchCallbackRegistered = true
            }
        } catch (_: Exception) {
            torchCameraId = null
        }
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        if (torchCallbackRegistered) {
            try {
                cameraManager?.unregisterTorchCallback(torchCallback)
            } catch (_: Exception) {
            }
            torchCallbackRegistered = false
        }
    }

    fun executeChain(actions: List<GestureAction>, delayMs: Int, initialDelayMs: Long = 150L) {
        if (actions.isEmpty()) return
        var offset = initialDelayMs.coerceAtLeast(0L)
        val stepDelay = delayMs.coerceIn(100, 3000).toLong()
        actions.forEach { action ->
            if (action.type != ActionType.NONE) {
                val runAt = offset
                handler.postDelayed({ execute(action) }, runAt)
                offset += if (action.type == ActionType.LAST_APP) maxOf(stepDelay, 500L) else stepDelay
            }
        }
    }

    private fun activeAccessibility(): AccessibilityService? {
        return accessibilityService ?: GestureAccessibilityService.instance
    }

    fun execute(action: GestureAction) {
        when (action.type) {
            ActionType.NONE -> Unit
            ActionType.OPEN_GESTURE_PAD -> onOpenGesturePad()
            ActionType.BACK -> activeAccessibility()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            ActionType.HOME -> activeAccessibility()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            ActionType.RECENTS -> activeAccessibility()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            ActionType.NOTIFICATIONS -> activeAccessibility()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            ActionType.QUICK_SETTINGS -> activeAccessibility()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            ActionType.POWER_DIALOG -> activeAccessibility()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
            ActionType.LOCK_SCREEN -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    activeAccessibility()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                } else {
                    showToast(R.string.overlay_require_api28)
                }
            }
            ActionType.SCREENSHOT -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    activeAccessibility()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
                } else {
                    showToast(R.string.overlay_require_api28)
                }
            }
            ActionType.SPLIT_SCREEN -> {
                try {
                    @Suppress("DEPRECATION")
                    activeAccessibility()?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
                } catch (_: Exception) {
                }
            }
            ActionType.LAST_APP -> {
                val svc = activeAccessibility() ?: return
                svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                handler.postDelayed(
                    { svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) },
                    300L
                )
            }
            ActionType.LAUNCH_APP -> launchApp(action.param)
            ActionType.OPEN_URL -> openUrl(action.param)
            ActionType.DIAL -> dialNumber(action.param)
            ActionType.FLASHLIGHT -> toggleFlashlight()
            ActionType.VOLUME_PANEL -> {
                audioManager?.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_SAME,
                    AudioManager.FLAG_SHOW_UI
                )
            }
            ActionType.MEDIA_PLAY_PAUSE -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            ActionType.MEDIA_NEXT -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            ActionType.MEDIA_PREV -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            ActionType.OPEN_WIFI_SETTINGS -> startSystemSettings(Settings.ACTION_WIFI_SETTINGS)
            ActionType.OPEN_BT_SETTINGS -> startSystemSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
            ActionType.ROTATION_SETTINGS -> startSystemSettings(Settings.ACTION_DISPLAY_SETTINGS)
        }
    }

    private fun launchApp(pkg: String?) {
        if (pkg.isNullOrBlank()) return
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun openUrl(rawUrl: String?) {
        if (rawUrl.isNullOrBlank()) return
        val trimmed = rawUrl.trim()
        val normalized = if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
            trimmed
        } else {
            "https://$trimmed"
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun dialNumber(rawNumber: String?) {
        if (rawNumber.isNullOrBlank()) return
        try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(rawNumber.trim())}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun toggleFlashlight() {
        val cm = cameraManager
        val id = torchCameraId
        if (cm == null || id == null) {
            showToast(R.string.overlay_flashlight_unavailable)
            return
        }
        try {
            val next = !isTorchOn
            cm.setTorchMode(id, next)
            isTorchOn = next
        } catch (_: Exception) {
            showToast(R.string.overlay_flashlight_unavailable)
        }
    }

    private fun dispatchMediaKey(keyCode: Int) {
        val am = audioManager ?: return
        val now = android.os.SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
    }

    private fun startSystemSettings(action: String) {
        try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun showToast(resId: Int) {
        val localized = LocaleHelper.wrapContext(context)
        Toast.makeText(context, localized.getString(resId), Toast.LENGTH_SHORT).show()
    }
}
