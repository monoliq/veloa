package az.gesture.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Observes global soft-keyboard visibility reliably across both Accessibility and Overlay-only
 * modes without intercepting any screen touches.
 */
class KeyboardVisibilityObserver(
    private val context: Context,
    private val windowManager: WindowManager,
    private val overlayType: Int,
    private val onKeyboardChanged: (Boolean) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var probeView: View? = null
    private var lastVisible: Boolean? = null

    fun attach() {
        if (probeView != null) return
        val screenH = context.resources.displayMetrics.heightPixels
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

        @Suppress("DEPRECATION")
        val params = WindowManager.LayoutParams(
            1,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        val view = View(context).apply {
            alpha = 0f
            ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
                val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime()) ||
                    insets.getInsets(WindowInsetsCompat.Type.ime()).bottom > 0
                notifyState(imeVisible)
                insets
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                addOnLayoutChangeListener { v, _, top, _, bottom, _, _, _, _ ->
                    val currentH = bottom - top
                    if (screenH > 0 && currentH > 0) {
                        val shrunk = currentH < (screenH * 0.84f)
                        notifyState(shrunk)
                    } else if (v.height > 0 && screenH > 0) {
                        notifyState(v.height < (screenH * 0.84f))
                    }
                }
            }
        }

        try {
            windowManager.addView(view, params)
            probeView = view
        } catch (_: Throwable) {
        }
    }

    fun detach() {
        mainHandler.removeCallbacksAndMessages(null)
        probeView?.let { v ->
            ViewCompat.setOnApplyWindowInsetsListener(v, null)
            try {
                windowManager.removeView(v)
            } catch (_: Throwable) {
            }
        }
        probeView = null
        lastVisible = null
    }

    private fun notifyState(visible: Boolean) {
        if (lastVisible == visible) return
        lastVisible = visible
        mainHandler.post { onKeyboardChanged(visible) }
    }
}
