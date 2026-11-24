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
import android.widget.TextView
import az.gesture.app.R
import az.gesture.app.data.Repo
import az.gesture.app.data.Shortcut

@SuppressLint("ViewConstructor")
class GesturePadOverlay(
    context: Context,
    private val repo: Repo,
    private var openedFromEdge: Boolean,
    private val onDismiss: () -> Unit,
    private val onShortcutMatched: (Shortcut) -> Unit
) : FrameLayout(context) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float): Int = (v * density).toInt()
    private val handler = Handler(Looper.getMainLooper())
    private val screenLoc = IntArray(2)

    private val matchPill: TextView
    private val drawHintView: TextView
    private val gestureOverlay: GestureOverlayView

    init {
        setBackgroundColor(Color.argb(175, 6, 12, 16))
        isFocusable = true
        isFocusableInTouchMode = true

        val settings = repo.getSettings()
        val strokeColor = settings.gestureColor

        drawHintView = TextView(context).apply {
            text = context.getString(R.string.overlay_draw_now)
            setTextColor(Color.argb(175, 226, 232, 240))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            visibility = if (openedFromEdge) View.GONE else View.VISIBLE
            setPadding(dp(20f), dp(10f), dp(20f), dp(10f))
            background = GradientDrawable().apply {
                setColor(Color.argb(110, 19, 32, 40))
                cornerRadius = dp(20f).toFloat()
                setStroke(dp(1f), Color.argb(90, 45, 212, 191))
            }
        }

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
            addOnGestureListener(object : GestureOverlayView.OnGestureListener {
                override fun onGestureStarted(overlay: GestureOverlayView?, event: MotionEvent?) {
                    drawHintView.visibility = View.GONE
                }
                override fun onGesture(overlay: GestureOverlayView?, event: MotionEvent?) = Unit
                override fun onGestureEnded(overlay: GestureOverlayView?, event: MotionEvent?) = Unit
                override fun onGestureCancelled(overlay: GestureOverlayView?, event: MotionEvent?) = Unit
            })
            addOnGesturePerformedListener { overlay, gesture ->
                val result = repo.recognize(gesture)
                val matched = result.shortcut
                if (matched != null) {
                    if (repo.getSettings().haptics) {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                    matchPill.text = "✓ ${matched.name}"
                    matchPill.visibility = View.VISIBLE
                    handler.postDelayed({ onShortcutMatched(matched) }, 110L)
                } else {
                    val strokeLength = gesture.strokes.sumOf { it.length.toDouble() }
                    if (openedFromEdge) {
                        if (strokeLength < dp(56f).toDouble()) {
                            openedFromEdge = false
                            overlay.clear(false)
                            drawHintView.visibility = View.VISIBLE
                        } else {
                            onDismiss()
                        }
                    }
                }
            }
        }
        addView(gestureOverlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(
            drawHintView,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )

        matchPill = TextView(context).apply {
            visibility = View.GONE
            setTextColor(Color.parseColor("#042F2E"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(18f), dp(9f), dp(18f), dp(9f))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2DD4BF"))
                cornerRadius = dp(20f).toFloat()
            }
        }
        addView(
            matchPill,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = dp(48f)
            }
        )

        val closeButton = TextView(context).apply {
            text = "✕"
            setTextColor(Color.parseColor("#E2E8F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(160, 19, 32, 40))
                setStroke(dp(1f), Color.argb(100, 45, 212, 191))
            }
            contentDescription = context.getString(R.string.overlay_close)
            setOnClickListener { onDismiss() }
        }
        addView(
            closeButton,
            LayoutParams(dp(40f), dp(40f), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(44f)
                marginEnd = dp(20f)
            }
        )
    }

    fun forwardTouchEvent(
        action: Int,
        rawX: Float,
        rawY: Float,
        downTime: Long,
        eventTime: Long
    ) {
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
            onDismiss()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }
}
