package az.gesture.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class KeyboardVisibilityObserver(
    private val context: Context,
    private val windowManager: WindowManager,
    private val overlayType: Int,
    private val onKeyboardChanged: (Boolean) -> Unit
) {
    private var probeView: View? = null
    private var lastVisible: Boolean = false
    private var maxObservedHeight: Int = 0
    private var insetsImeVisible: Boolean = false
    private var layoutShrunk: Boolean = false

    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var layoutChangeListener: View.OnLayoutChangeListener? = null

    fun attach() {
        if (probeView != null) return

        val view = View(context).apply {
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
        }

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

        val params = WindowManager.LayoutParams(
            1,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            flags,
            PixelFormat.TRANSPARENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fitInsetsTypes = WindowInsets.Type.statusBars() or
                    WindowInsets.Type.navigationBars() or
                    WindowInsets.Type.ime()
                isFitInsetsIgnoringVisibility = false
            }
        }

        val thresholdPx = (100f * context.resources.displayMetrics.density).toInt().coerceAtLeast(100)

        fun evaluateLayoutHeight(currentH: Int) {
            if (currentH <= 0) return
            if (currentH > maxObservedHeight) {
                maxObservedHeight = currentH
            }
            layoutShrunk = maxObservedHeight > 0 && (maxObservedHeight - currentH) > thresholdPx
            dispatchIfChanged()
        }

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            insetsImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            dispatchIfChanged()
            insets
        }

        val onLayoutChange = View.OnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            evaluateLayoutHeight(bottom - top)
        }
        view.addOnLayoutChangeListener(onLayoutChange)
        layoutChangeListener = onLayoutChange

        val onGlobalLayout = ViewTreeObserver.OnGlobalLayoutListener {
            evaluateLayoutHeight(view.height)
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(onGlobalLayout)
        globalLayoutListener = onGlobalLayout

        try {
            windowManager.addView(view, params)
            probeView = view
        } catch (_: Throwable) {
            cleanupListeners(view)
        }
    }

    fun detach() {
        val view = probeView ?: return
        probeView = null
        cleanupListeners(view)
        try {
            windowManager.removeViewImmediate(view)
        } catch (_: Throwable) {
            try {
                windowManager.removeView(view)
            } catch (_: Throwable) {
            }
        }
        lastVisible = false
        maxObservedHeight = 0
        insetsImeVisible = false
        layoutShrunk = false
    }

    private fun cleanupListeners(view: View) {
        try {
            ViewCompat.setOnApplyWindowInsetsListener(view, null)
            layoutChangeListener?.let { view.removeOnLayoutChangeListener(it) }
            layoutChangeListener = null
            globalLayoutListener?.let {
                if (view.viewTreeObserver.isAlive) {
                    view.viewTreeObserver.removeOnGlobalLayoutListener(it)
                }
            }
            globalLayoutListener = null
        } catch (_: Throwable) {
        }
    }

    private fun dispatchIfChanged() {
        val visible = insetsImeVisible || layoutShrunk
        if (visible != lastVisible) {
            lastVisible = visible
            onKeyboardChanged(visible)
        }
    }
}
