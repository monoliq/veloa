package az.gesture.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.gesture.GestureOverlayView
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import az.gesture.app.R
import az.gesture.app.data.Repo
import az.gesture.app.data.Shortcut

@SuppressLint("ViewConstructor")
class GesturePadOverlay(
    context: Context,
    private val repo: Repo,
    private var openedFromEdge: Boolean,
    private val onDismiss: () -> Unit,
    private val onShortcutMatched: (Shortcut) -> Unit,
    private val onOpenApp: () -> Unit
) : FrameLayout(context) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float): Int = (v * density).toInt()
    private val handler = Handler(Looper.getMainLooper())
    private val screenLoc = IntArray(2)

    private var dismissed = false
    private val matchPill: TextView
    private val gestureOverlay: GestureOverlayView

    init {
        setBackgroundColor(Color.argb(158, 8, 12, 18))
        alpha = 0f
        animate().alpha(1f).setDuration(140L).start()
        isFocusable = true
        isFocusableInTouchMode = true

        val settings = repo.getSettings()
        val strokeColor = settings.gestureColor

        gestureOverlay = GestureOverlayView(context).apply {
            gestureStrokeType = if (settings.multiStroke) {
                GestureOverlayView.GESTURE_STROKE_TYPE_MULTIPLE
            } else {
                GestureOverlayView.GESTURE_STROKE_TYPE_SINGLE
            }
            fadeOffset = 420L
            gestureStrokeWidth = dp(6f).toFloat()
            orientation = GestureOverlayView.ORIENTATION_VERTICAL
            gestureStrokeAngleThreshold = 95f
            gestureStrokeSquarenessTreshold = 0f
            gestureStrokeLengthThreshold = dp(16f).toFloat()
            gestureColor = strokeColor
            uncertainGestureColor = strokeColor
            isGestureVisible = true
            isEventsInterceptionEnabled = true
            addOnGesturePerformedListener { overlay, gesture ->
                if (dismissed) return@addOnGesturePerformedListener
                val result = repo.recognize(gesture)
                val matched = result.shortcut
                if (matched != null) {
                    if (repo.getSettings().haptics) {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                    matchPill.text = "✓ ${matched.name}"
                    matchPill.visibility = View.VISIBLE
                    handler.postDelayed({
                        if (!dismissed) {
                            dismissed = true
                            onShortcutMatched(matched)
                        }
                    }, 110L)
                } else {
                    val strokeLength = gesture.strokes.sumOf { it.length.toDouble() }
                    if (openedFromEdge && strokeLength < dp(56f).toDouble()) {
                        openedFromEdge = false
                        overlay.clear(false)
                    } else {
                        dismissOnce()
                    }
                }
            }
        }
        addView(gestureOverlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val pillTextColor = if (ColorUtils.calculateLuminance(strokeColor) > 0.45) {
            Color.parseColor("#06181A")
        } else {
            Color.parseColor("#F8FAFC")
        }

        matchPill = TextView(context).apply {
            visibility = View.GONE
            setTextColor(pillTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(20f), dp(10f), dp(20f), dp(10f))
            background = GradientDrawable().apply {
                setColor(strokeColor)
                cornerRadius = dp(24f).toFloat()
            }
            elevation = dp(4f).toFloat()
        }
        val matchPillLp = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply {
            topMargin = dp(48f)
        }
        addView(matchPill, matchPillLp)

        val btnSize = dp(48f)
        val btnPadding = dp(12f)
        val iconTint = Color.parseColor("#E2E8F0")

        fun createFrostedCircleBackground(): GradientDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(185, 22, 27, 36))
            setStroke(dp(1f), Color.argb(52, 255, 255, 255))
        }

        val openAppButton = ImageView(context).apply {
            setImageResource(R.drawable.ic_menu_lines)
            setColorFilter(iconTint)
            setPadding(btnPadding, btnPadding, btnPadding, btnPadding)
            background = createFrostedCircleBackground()
            elevation = dp(4f).toFloat()
            contentDescription = context.getString(R.string.app_name)
            isClickable = true
            isFocusable = false
            setOnClickListener {
                if (!dismissed) {
                    dismissOnce()
                    onOpenApp()
                }
            }
        }

        val backButton = ImageView(context).apply {
            setImageResource(R.drawable.ic_action_back)
            setColorFilter(iconTint)
            setPadding(btnPadding, btnPadding, btnPadding, btnPadding)
            background = createFrostedCircleBackground()
            elevation = dp(4f).toFloat()
            contentDescription = context.getString(R.string.overlay_close)
            isClickable = true
            isFocusable = false
            setOnClickListener { dismissOnce() }
        }

        val actionStack = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(
                openAppButton,
                LinearLayout.LayoutParams(btnSize, btnSize).apply {
                    bottomMargin = dp(12f)
                }
            )
            addView(
                backButton,
                LinearLayout.LayoutParams(btnSize, btnSize)
            )
        }

        val stackLp = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.END
        ).apply {
            marginEnd = dp(20f)
            bottomMargin = dp(48f)
        }
        addView(actionStack, stackLp)

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            matchPillLp.topMargin = sysBars.top + dp(20f)
            matchPill.layoutParams = matchPillLp
            stackLp.bottomMargin = sysBars.bottom + dp(28f)
            actionStack.layoutParams = stackLp
            insets
        }
    }

    private fun dismissOnce() {
        if (dismissed) return
        dismissed = true
        handler.removeCallbacksAndMessages(null)
        onDismiss()
    }

    fun forwardTouchEvent(
        action: Int,
        rawX: Float,
        rawY: Float,
        downTime: Long,
        eventTime: Long
    ) {
        if (dismissed) return
        val localX: Float
        val localY: Float
        if (gestureOverlay.width > 0) {
            gestureOverlay.getLocationOnScreen(screenLoc)
            localX = rawX - screenLoc[0]
            localY = rawY - screenLoc[1]
        } else {
            localX = rawX
            localY = rawY
        }
        val ev = MotionEvent.obtain(downTime, eventTime, action, localX, localY, 0)
        gestureOverlay.dispatchTouchEvent(ev)
        ev.recycle()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            dismissOnce()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }
}
