package az.gesture.app.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import az.gesture.app.data.BUTTON_COLOR_PRESETS
import az.gesture.app.data.ButtonSettings
import az.gesture.app.data.ButtonTrigger
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
class FloatingButton(
    context: Context,
    private val windowManager: WindowManager,
    private val layoutParams: WindowManager.LayoutParams,
    private val onTrigger: (ButtonTrigger) -> Unit,
    private val hasDoubleTapAction: () -> Boolean,
    private val onPositionSaved: (xRatio: Float, yRatio: Float) -> Unit
) : View(context) {

    private val density = resources.displayMetrics.density
    private fun dp(value: Float): Float = value * density

    private val touchSlopPx = dp(8f)
    private val swipeThresholdPx = dp(38f)
    private val maxVisualNudgePx = dp(9f)
    private val longPressDelayMs = 300L
    private val doubleTapWindowMs = 220L
    private val idleDelayMs = 2000L

    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = Color.argb(95, 255, 255, 255)
    }

    private var settings = ButtonSettings()
    private var nudgeX = 0f
    private var nudgeY = 0f

    private val handler = Handler(Looper.getMainLooper())
    private var edgeAnimator: ValueAnimator? = null

    private var downRawX = 0f
    private var downRawY = 0f
    private var startWinX = 0
    private var startWinY = 0
    private var movedPastSlop = false
    private var longPressArmed = false
    private var isDragging = false

    private var pendingSingleTap: Runnable? = null

    private val longPressRunnable = Runnable {
        if (!movedPastSlop) {
            longPressArmed = true
            performHapticIfEnabled()
            animate().scaleX(1.08f).scaleY(1.08f).setDuration(100L).start()
        }
    }

    private val idleFadeRunnable = Runnable {
        val targetAlpha = (settings.idleOpacity / 100f).coerceIn(0.10f, 1f)
        animate().alpha(targetAlpha).setDuration(300L).start()
    }

    init {
        isClickable = true
        isFocusable = false
        applySettings(settings)
    }

    fun applySettings(newSettings: ButtonSettings) {
        settings = newSettings
        val preset = BUTTON_COLOR_PRESETS.getOrElse(newSettings.colorIndex) { BUTTON_COLOR_PRESETS[0] }
        outerPaint.color = preset.outerColor
        innerPaint.color = preset.innerColor
        invalidate()
        scheduleIdleFade()
    }

    private fun performHapticIfEnabled() {
        if (settings.haptics) {
            performHapticFeedback(
                HapticFeedbackConstants.VIRTUAL_KEY,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        }
    }

    fun scheduleIdleFade() {
        handler.removeCallbacks(idleFadeRunnable)
        handler.postDelayed(idleFadeRunnable, idleDelayMs)
    }

    fun cancelTimers() {
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacks(idleFadeRunnable)
        pendingSingleTap?.let { handler.removeCallbacks(it) }
        pendingSingleTap = null
        edgeAnimator?.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val outerR = (minOf(width, height) / 2f) - dp(2f)
        if (outerR <= 0f) return
        val innerR = outerR * 0.58f

        canvas.drawCircle(cx, cy, outerR, outerPaint)
        canvas.drawCircle(cx, cy, outerR - rimPaint.strokeWidth / 2f, rimPaint)
        canvas.drawCircle(cx + nudgeX, cy + nudgeY, innerR, innerPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        val btnW = layoutParams.width.coerceAtLeast(width)
        val btnH = layoutParams.height.coerceAtLeast(height)
        val maxX = (screenW - btnW).coerceAtLeast(0)
        val minY = dp(24f).roundToInt()
        val maxY = (screenH - btnH - dp(24f).roundToInt()).coerceAtLeast(minY)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                edgeAnimator?.cancel()
                handler.removeCallbacks(idleFadeRunnable)
                animate().alpha(1f).scaleX(0.95f).scaleY(0.95f).setDuration(90L).start()

                downRawX = event.rawX
                downRawY = event.rawY
                startWinX = layoutParams.x
                startWinY = layoutParams.y
                movedPastSlop = false
                longPressArmed = false
                isDragging = false
                nudgeX = 0f
                nudgeY = 0f

                handler.removeCallbacks(longPressRunnable)
                handler.postDelayed(longPressRunnable, longPressDelayMs)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                val dist = hypot(dx, dy)

                if (dist > touchSlopPx) {
                    movedPastSlop = true
                    if (!longPressArmed) {
                        handler.removeCallbacks(longPressRunnable)
                    }
                }

                if (longPressArmed && dist > touchSlopPx) {
                    if (!isDragging) {
                        isDragging = true
                        pendingSingleTap?.let { handler.removeCallbacks(it) }
                        pendingSingleTap = null
                    }
                    layoutParams.x = (startWinX + dx).roundToInt().coerceIn(0, maxX)
                    layoutParams.y = (startWinY + dy).roundToInt().coerceIn(minY, maxY)
                    safeUpdateLayout()
                } else if (!isDragging && movedPastSlop) {
                    val ratio = (dist / swipeThresholdPx).coerceAtMost(1f)
                    if (dist > 0f) {
                        nudgeX = (dx / dist) * maxVisualNudgePx * ratio
                        nudgeY = (dy / dist) * maxVisualNudgePx * ratio
                        invalidate()
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                animate().scaleX(1f).scaleY(1f).setDuration(100L).start()
                nudgeX = 0f
                nudgeY = 0f
                invalidate()

                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                val dist = hypot(dx, dy)

                when {
                    isDragging -> {
                        snapToNearestEdge(maxX, minY, maxY)
                    }
                    longPressArmed && !movedPastSlop -> {
                        performHapticIfEnabled()
                        onTrigger(ButtonTrigger.LONG_PRESS)
                    }
                    dist >= swipeThresholdPx -> {
                        performHapticIfEnabled()
                        val trigger = if (abs(dx) >= abs(dy)) {
                            if (dx < 0) ButtonTrigger.SWIPE_LEFT else ButtonTrigger.SWIPE_RIGHT
                        } else {
                            if (dy < 0) ButtonTrigger.SWIPE_UP else ButtonTrigger.SWIPE_DOWN
                        }
                        onTrigger(trigger)
                    }
                    else -> {
                        handleTap()
                    }
                }

                isDragging = false
                longPressArmed = false
                scheduleIdleFade()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                animate().scaleX(1f).scaleY(1f).setDuration(100L).start()
                nudgeX = 0f
                nudgeY = 0f
                invalidate()
                if (isDragging) {
                    snapToNearestEdge(maxX, minY, maxY)
                }
                isDragging = false
                longPressArmed = false
                scheduleIdleFade()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTap() {
        if (!hasDoubleTapAction()) {
            performHapticIfEnabled()
            onTrigger(ButtonTrigger.TAP)
            return
        }
        val existing = pendingSingleTap
        if (existing != null) {
            handler.removeCallbacks(existing)
            pendingSingleTap = null
            performHapticIfEnabled()
            onTrigger(ButtonTrigger.DOUBLE_TAP)
        } else {
            val runnable = Runnable {
                pendingSingleTap = null
                performHapticIfEnabled()
                onTrigger(ButtonTrigger.TAP)
            }
            pendingSingleTap = runnable
            handler.postDelayed(runnable, doubleTapWindowMs)
        }
    }

    private fun snapToNearestEdge(maxX: Int, minY: Int, maxY: Int) {
        val currentX = layoutParams.x
        val targetX = if (currentX < maxX / 2) 0 else maxX
        val yRange = (maxY - minY).coerceAtLeast(1)
        val yRatio = ((layoutParams.y - minY).toFloat() / yRange).coerceIn(0f, 1f)
        val xRatio = if (targetX == 0) 0f else 1f
        onPositionSaved(xRatio, yRatio)

        edgeAnimator?.cancel()
        edgeAnimator = ValueAnimator.ofInt(currentX, targetX).apply {
            duration = 200L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                layoutParams.x = anim.animatedValue as Int
                safeUpdateLayout()
            }
            start()
        }
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
