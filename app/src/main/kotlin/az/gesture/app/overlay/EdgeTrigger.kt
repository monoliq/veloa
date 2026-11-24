package az.gesture.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

@SuppressLint("ViewConstructor")
class EdgeTrigger(
    context: Context,
    private val isLeftSide: Boolean,
    private val hapticsEnabled: () -> Boolean,
    private val isDirectDraw: () -> Boolean,
    private val onEdgeOpenPadOnly: () -> Unit,
    private val onEdgeSwipeStart: (rawX: Float, rawY: Float, downTime: Long, eventTime: Long) -> Unit,
    private val onEdgeSwipeMove: (rawX: Float, rawY: Float, downTime: Long, eventTime: Long) -> Unit,
    private val onEdgeSwipeEnd: (rawX: Float, rawY: Float, downTime: Long, eventTime: Long, cancelled: Boolean) -> Unit
) : View(context) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val swipeThresholdPx = dp(10f)
    private val exclusionRects = listOf(Rect())
    private var downRawX = 0f
    private var downRawY = 0f
    private var gestureDownTime = 0L
    private var forwarding = false
    private var openedOnly = false

    private val tabPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val tabPath = Path()
    private val tabRect = RectF()

    fun applyStyle(colorInt: Int, transparency: Float) {
        val alpha = (transparency.coerceIn(0.1f, 1.0f) * 255).toInt()
        tabPaint.color = Color.argb(
            alpha,
            Color.red(colorInt),
            Color.green(colorInt),
            Color.blue(colorInt)
        )
        invalidate()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateSystemGestureExclusion(right - left, bottom - top)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateSystemGestureExclusion(w, h)
    }

    private fun updateSystemGestureExclusion(w: Int, h: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && w > 0 && h > 0) {
            val maxExcludedH = (196f * density).toInt().coerceAtLeast(1)
            val topOffset = if (h > maxExcludedH) (h - maxExcludedH) / 2 else 0
            val bottomPos = if (h > maxExcludedH) topOffset + maxExcludedH else h
            exclusionRects[0].set(0, topOffset, w, bottomPos)
            systemGestureExclusionRects = exclusionRects
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val r = (width.toFloat() * 0.6f).coerceAtMost(dp(12f))
        tabRect.set(0f, 0f, width.toFloat(), height.toFloat())
        tabPath.reset()
        val radii = if (isLeftSide) {
            floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
        } else {
            floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
        }
        tabPath.addRoundRect(tabRect, radii, Path.Direction.CW)
        canvas.drawPath(tabPath, tabPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                gestureDownTime = event.downTime
                forwarding = false
                openedOnly = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (openedOnly) return true
                if (!forwarding) {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    val inward = if (isLeftSide) dx else -dx
                    if (inward >= swipeThresholdPx && abs(dx) > abs(dy) * 0.55f) {
                        if (hapticsEnabled()) {
                            @Suppress("DEPRECATION")
                            performHapticFeedback(
                                HapticFeedbackConstants.VIRTUAL_KEY,
                                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                            )
                        }
                        if (isDirectDraw()) {
                            forwarding = true
                            gestureDownTime = event.eventTime
                            onEdgeSwipeStart(event.rawX, event.rawY, gestureDownTime, event.eventTime)
                        } else {
                            openedOnly = true
                            onEdgeOpenPadOnly()
                        }
                    }
                } else {
                    onEdgeSwipeMove(event.rawX, event.rawY, gestureDownTime, event.eventTime)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (forwarding) {
                    onEdgeSwipeEnd(event.rawX, event.rawY, gestureDownTime, event.eventTime, false)
                    forwarding = false
                }
                openedOnly = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (forwarding) {
                    onEdgeSwipeEnd(event.rawX, event.rawY, gestureDownTime, event.eventTime, true)
                    forwarding = false
                }
                openedOnly = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
