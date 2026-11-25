package az.gesture.app.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
class EdgeTrigger(
    context: Context,
    private val windowManager: WindowManager,
    private val layoutParams: WindowManager.LayoutParams,
    private var isLeftSide: Boolean,
    private val hapticsEnabled: () -> Boolean,
    private val isDirectDraw: () -> Boolean,
    private val onEdgeOpenPadOnly: () -> Unit,
    private val onEdgeSwipeStart: (rawX: Float, rawY: Float, downTime: Long, eventTime: Long) -> Unit,
    private val onEdgeSwipeMove: (rawX: Float, rawY: Float, downTime: Long, eventTime: Long) -> Unit,
    private val onEdgeSwipeEnd: (rawX: Float, rawY: Float, downTime: Long, eventTime: Long, cancelled: Boolean) -> Unit,
    private val onHandleRepositioned: (newIsLeft: Boolean, newLocationRatio: Float) -> Unit
) : View(context) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val holdCancelPx = dp(18f)
    private val swipeThresholdPx = dp(10f)
    private val longPressDelayMs = 170L
    private val exclusionRects = listOf(Rect())

    private val handler = Handler(Looper.getMainLooper())
    private var edgeAnimator: ValueAnimator? = null

    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var startWinX = 0
    private var startWinY = 0
    private var gestureDownTime = 0L
    private var forwarding = false
    private var openedOnly = false
    private var movedBeforeLongPress = false
    private var isDraggingHandle = false
    private var isDraggedMidScreen = false
    private var hiddenForPad = false

    private val tabPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val dragOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = Color.argb(180, 255, 255, 255)
    }
    private val tabPath = Path()
    private val tabRect = RectF()

    private val longPressRunnable = Runnable {
        if (!movedBeforeLongPress && !forwarding && !openedOnly && !hiddenForPad) {
            isDraggingHandle = true
            isDraggedMidScreen = false
            downRawX = lastRawX
            downRawY = lastRawY
            startWinX = layoutParams.x
            startWinY = layoutParams.y
            if (hapticsEnabled()) {
                @Suppress("DEPRECATION")
                performHapticFeedback(
                    HapticFeedbackConstants.LONG_PRESS,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
            }
            animate().scaleX(1.25f).scaleY(1.04f).setDuration(90L).start()
            invalidate()
        }
    }

    fun applyStyle(colorInt: Int, transparency: Float) {
        val alphaInt = (transparency.coerceIn(0.1f, 1.0f) * 255).toInt()
        tabPaint.color = Color.argb(
            alphaInt,
            Color.red(colorInt),
            Color.green(colorInt),
            Color.blue(colorInt)
        )
        invalidate()
    }

    fun setHiddenForPad(hidden: Boolean) {
        if (hiddenForPad == hidden) return
        hiddenForPad = hidden
        animate().cancel()
        if (hidden) {
            handler.removeCallbacks(longPressRunnable)
            alpha = 0f
        } else {
            animate().alpha(1f).setDuration(120L).start()
        }
    }

    fun updateYPosition(yPx: Int) {
        layoutParams.y = yPx
        safeUpdateLayout()
    }

    fun cancelTimers() {
        handler.removeCallbacks(longPressRunnable)
        edgeAnimator?.cancel()
        animate().cancel()
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
        val radii = when {
            isDraggedMidScreen -> floatArrayOf(r, r, r, r, r, r, r, r)
            isLeftSide -> floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
            else -> floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
        }
        tabPath.addRoundRect(tabRect, radii, Path.Direction.CW)
        canvas.drawPath(tabPath, tabPaint)
        if (isDraggingHandle) {
            canvas.drawPath(tabPath, dragOutlinePaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        val w = layoutParams.width.coerceAtLeast(width)
        val h = layoutParams.height.coerceAtLeast(height)
        val maxX = (screenW - w).coerceAtLeast(0)
        val minY = dp(32f).roundToInt()
        val maxY = (screenH - h - minY).coerceAtLeast(minY)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                edgeAnimator?.cancel()
                downRawX = event.rawX
                downRawY = event.rawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                startWinX = layoutParams.x
                startWinY = layoutParams.y
                gestureDownTime = event.downTime
                forwarding = false
                openedOnly = false
                movedBeforeLongPress = false
                isDraggingHandle = false
                isDraggedMidScreen = false
                handler.removeCallbacks(longPressRunnable)
                handler.postDelayed(longPressRunnable, longPressDelayMs)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                lastRawX = event.rawX
                lastRawY = event.rawY
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY

                if (isDraggingHandle) {
                    layoutParams.x = (startWinX + dx).roundToInt().coerceIn(0, maxX)
                    layoutParams.y = (startWinY + dy).roundToInt().coerceIn(minY, maxY)
                    isLeftSide = (layoutParams.x + w / 2) < (screenW / 2)
                    isDraggedMidScreen = layoutParams.x > dp(6f) && layoutParams.x < maxX - dp(6f)
                    invalidate()
                    safeUpdateLayout()
                    return true
                }

                if (openedOnly) return true
                if (!forwarding) {
                    val inward = if (isLeftSide) dx else -dx
                    if (inward >= swipeThresholdPx && abs(dx) > abs(dy) * 0.55f) {
                        movedBeforeLongPress = true
                        handler.removeCallbacks(longPressRunnable)
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
                    } else if (hypot(dx, dy) > holdCancelPx) {
                        movedBeforeLongPress = true
                        handler.removeCallbacks(longPressRunnable)
                    }
                } else {
                    onEdgeSwipeMove(event.rawX, event.rawY, gestureDownTime, event.eventTime)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                if (isDraggingHandle) {
                    finishDragAndSnap(screenW, maxX, minY, maxY)
                    return true
                }
                if (forwarding) {
                    onEdgeSwipeEnd(event.rawX, event.rawY, gestureDownTime, event.eventTime, false)
                    forwarding = false
                }
                openedOnly = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                if (isDraggingHandle) {
                    finishDragAndSnap(screenW, maxX, minY, maxY)
                    return true
                }
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

    private fun finishDragAndSnap(screenW: Int, maxX: Int, minY: Int, maxY: Int) {
        isDraggingHandle = false
        isDraggedMidScreen = false
        animate().scaleX(1f).scaleY(1f).setDuration(100L).start()

        val w = layoutParams.width.coerceAtLeast(width)
        val snapToLeft = (layoutParams.x + w / 2) < (screenW / 2)
        val targetX = if (snapToLeft) 0 else maxX
        val yRange = (maxY - minY).coerceAtLeast(1)
        val yRatio = ((layoutParams.y - minY).toFloat() / yRange).coerceIn(0f, 1f)
        isLeftSide = snapToLeft
        invalidate()

        val currentX = layoutParams.x
        edgeAnimator?.cancel()
        edgeAnimator = ValueAnimator.ofInt(currentX, targetX).apply {
            duration = 180L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                layoutParams.x = anim.animatedValue as Int
                safeUpdateLayout()
            }
            start()
        }
        onHandleRepositioned(snapToLeft, yRatio)
    }

    private fun safeUpdateLayout() {
        if (isAttachedToWindow) {
            try {
                windowManager.updateViewLayout(this, layoutParams)
            } catch (_: Exception) {
            }
        }
    }
}
