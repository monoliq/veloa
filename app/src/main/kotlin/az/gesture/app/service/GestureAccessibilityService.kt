package az.gesture.app.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import az.gesture.app.MainActivity
import az.gesture.app.data.ActionType
import az.gesture.app.data.ButtonTrigger
import az.gesture.app.data.EdgeSide
import az.gesture.app.data.LocaleHelper
import az.gesture.app.data.Repo
import az.gesture.app.data.SideTriggerMode
import az.gesture.app.overlay.EdgeTrigger
import az.gesture.app.overlay.FloatingButton
import az.gesture.app.overlay.GesturePadOverlay
import az.gesture.app.overlay.KeyboardVisibilityObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

class GestureAccessibilityService : AccessibilityService() {

    private lateinit var repo: Repo
    private lateinit var windowManager: WindowManager
    private lateinit var actionExecutor: ActionExecutor

    private val mainHandler = Handler(Looper.getMainLooper())
    private var prefListenerRegistered = false
    private var isCleanedUp = false

    private var floatingButton: FloatingButton? = null
    private var buttonParams: WindowManager.LayoutParams? = null
    private val edgeTriggers = mutableListOf<EdgeTrigger>()
    private var gesturePadOverlay: GesturePadOverlay? = null

    private var isLandscape = false
    private var accessibilityImeVisible = false
    private var observerImeVisible = false
    private var isKeyboardShown = false
    private var suppressSelfSidePrefRefresh = false
    private var keyboardObserver: KeyboardVisibilityObserver? = null
    private val imeBoundsRect = Rect()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != "button_x_ratio" &&
            key != "button_y_ratio" &&
            key != "sensitivity_percent" &&
            key != "direction_priority" &&
            !(suppressSelfSidePrefRefresh && (key == "side_location" || key == "edge_side"))
        ) {
            mainHandler.post { refreshOverlays() }
        }
    }

    private val imeCheckRunnable = Runnable {
        try {
            if (!::repo.isInitialized) return@Runnable
            val settings = repo.getSettings()
            accessibilityImeVisible = if (settings.hideOnKeyboard) {
                windows?.any { win ->
                    if (win.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD) return@any false
                    win.getBoundsInScreen(imeBoundsRect)
                    imeBoundsRect.height() > 0 && imeBoundsRect.width() > 0
                } == true
            } else {
                false
            }
            recomputeKeyboardVisibility(settings.hideOnKeyboard)
        } catch (_: Throwable) {
        }
    }

    private fun recomputeKeyboardVisibility(hideOnKeyboard: Boolean) {
        val nextShown = hideOnKeyboard && (observerImeVisible || accessibilityImeVisible)
        if (nextShown != isKeyboardShown) {
            isKeyboardShown = nextShown
            updateHandlersVisibility()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            isCleanedUp = false
            instance = this
            _isRunning.value = true

            repo = Repo.get(this)
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (::actionExecutor.isInitialized) {
                actionExecutor.release()
            }
            actionExecutor = ActionExecutor(
                context = this,
                accessibilityService = this,
                onOpenGesturePad = { showGesturePad(fromEdge = false) }
            )

            isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            if (!prefListenerRegistered) {
                repo.prefs.registerOnSharedPreferenceChangeListener(prefListener)
                prefListenerRegistered = true
            }

            VeloaKeepAliveService.releaseFallbackOverlays()
            startKeepAliveForeground()
            refreshOverlays()

            // Retry overlay attachment shortly if WindowManager was still initializing on OEM boot/reconnect
            mainHandler.postDelayed({
                if (instance === this && !isCleanedUp) {
                    val s = repo.getSettings()
                    val needsButton = s.floatingEnabled && floatingButton == null
                    val needsEdge = s.sideEnabled && edgeTriggers.isEmpty()
                    if (needsButton || needsEdge) {
                        refreshOverlays()
                    }
                }
            }, 450L)
        } catch (_: Throwable) {
        }
    }

    fun ensureForegroundNotification() {
        mainHandler.post { startKeepAliveForeground() }
    }

    private fun startKeepAliveForeground() {
        try {
            val notification = VeloaKeepAliveService.buildKeepAliveNotification(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    VeloaKeepAliveService.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(VeloaKeepAliveService.NOTIFICATION_ID, notification)
            }
            VeloaKeepAliveService.start(this)
        } catch (_: Throwable) {
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        try {
            if (!isCleanedUp) {
                startKeepAliveForeground()
                mainHandler.postDelayed({
                    if (instance === this && !isCleanedUp) {
                        refreshOverlays()
                    }
                }, 250L)
            }
        } catch (_: Throwable) {
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        try {
            isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
            hideGesturePad()
            refreshOverlays()
        } catch (_: Throwable) {
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (!::repo.isInitialized) return
            mainHandler.removeCallbacks(imeCheckRunnable)
            mainHandler.postDelayed(imeCheckRunnable, 160L)
        } catch (_: Throwable) {
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return false
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        if (isCleanedUp) return
        isCleanedUp = true
        mainHandler.removeCallbacksAndMessages(null)
        try {
            if (prefListenerRegistered && ::repo.isInitialized) {
                repo.prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
                prefListenerRegistered = false
            }
        } catch (_: Throwable) {
        }
        try {
            removeAllOverlays()
        } catch (_: Throwable) {
        }
        try {
            if (::actionExecutor.isInitialized) {
                actionExecutor.release()
            }
        } catch (_: Throwable) {
        }
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Throwable) {
        }
        try {
            if (!VeloaKeepAliveService.shouldStayAlive(this)) {
                VeloaKeepAliveService.stop(this)
            } else {
                VeloaKeepAliveService.start(this)
                VeloaKeepAliveService.activateFallbackIfNeeded()
                VeloaWatchdogJobService.scheduleQuickRecovery(this)
            }
        } catch (_: Throwable) {
        }
        if (instance === this) {
            instance = null
        }
        _isRunning.value = false
    }

    private fun updateHandlersVisibility() {
        val visibility = if (isKeyboardShown) View.GONE else View.VISIBLE
        floatingButton?.visibility = visibility
        edgeTriggers.forEach { it.visibility = visibility }
        if (gesturePadOverlay != null) {
            setHandlersHiddenForPad(true)
        }
    }

    private fun setHandlersHiddenForPad(hidden: Boolean) {
        edgeTriggers.forEach { it.setHiddenForPad(hidden) }
        floatingButton?.let { btn ->
            btn.animate().cancel()
            if (hidden) {
                btn.alpha = 0f
            } else {
                btn.alpha = 1f
                btn.scheduleIdleFade()
            }
        }
    }

    fun refreshOverlays() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { refreshOverlays() }
            return
        }
        try {
            if (isCleanedUp || !::repo.isInitialized || !::windowManager.isInitialized) return
            repo.reloadLibrary()
            val settings = repo.getSettings()
            val hideLandscape = settings.hideInLandscape && isLandscape

            if (!settings.floatingEnabled || hideLandscape) {
                removeFloatingButton()
            } else {
                ensureOrUpdateFloatingButton(settings.sizeDp, settings.buttonXRatio, settings.buttonYRatio)
                floatingButton?.applySettings(settings)
            }

            if (!settings.sideEnabled || hideLandscape) {
                removeEdgeTriggers()
            } else {
                rebuildEdgeTriggers(
                    side = settings.edgeSide,
                    widthDp = settings.sideWidthDp,
                    heightDp = settings.sideHeightDp,
                    locationRatio = settings.sideLocation,
                    colorInt = settings.sideColor,
                    transparency = settings.sideTransparency
                )
            }

            syncKeyboardObserver(settings.hideOnKeyboard && (settings.floatingEnabled || settings.sideEnabled) && !hideLandscape)
            recomputeKeyboardVisibility(settings.hideOnKeyboard)
            updateHandlersVisibility()
        } catch (_: Throwable) {
        }
    }

    private fun syncKeyboardObserver(shouldObserve: Boolean) {
        if (shouldObserve) {
            if (keyboardObserver == null) {
                keyboardObserver = KeyboardVisibilityObserver(
                    context = this,
                    windowManager = windowManager,
                    overlayType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                ) { visible ->
                    observerImeVisible = visible
                    if (!visible) {
                        accessibilityImeVisible = false
                        mainHandler.removeCallbacks(imeCheckRunnable)
                        mainHandler.postDelayed(imeCheckRunnable, 80L)
                    }
                    val hide = if (::repo.isInitialized) repo.getSettings().hideOnKeyboard else false
                    recomputeKeyboardVisibility(hide)
                }.also { it.attach() }
            }
        } else {
            keyboardObserver?.detach()
            keyboardObserver = null
            observerImeVisible = false
            accessibilityImeVisible = false
            isKeyboardShown = false
        }
    }

    private fun ensureOrUpdateFloatingButton(sizeDp: Int, xRatio: Float, yRatio: Float) {
        val density = resources.displayMetrics.density
        val sizePx = (sizeDp * density).roundToInt()
        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        val marginY = (24f * density).roundToInt()
        val maxX = (screenW - sizePx).coerceAtLeast(0)
        val maxY = (screenH - sizePx - marginY).coerceAtLeast(marginY)

        val existing = floatingButton
        val existingParams = buttonParams
        if (existing != null && existingParams != null && existing.isAttachedToWindow) {
            existingParams.width = sizePx
            existingParams.height = sizePx
            existingParams.x = existingParams.x.coerceIn(0, maxX)
            existingParams.y = existingParams.y.coerceIn(marginY, maxY)
            try {
                windowManager.updateViewLayout(existing, existingParams)
            } catch (_: Throwable) {
            }
            return
        } else if (existing != null) {
            removeFloatingButton()
        }

        val initialX = if (xRatio < 0.5f) 0 else maxX
        val initialY = (marginY + ((maxY - marginY) * yRatio)).roundToInt().coerceIn(marginY, maxY)

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }

        val btn = FloatingButton(
            context = this,
            windowManager = windowManager,
            layoutParams = params,
            onTrigger = { trigger ->
                try {
                    val action = repo.getTriggerAction(trigger)
                    actionExecutor.execute(action)
                } catch (_: Throwable) {
                }
            },
            hasDoubleTapAction = {
                val s = repo.getSettings()
                s.enableDoubleTap && repo.getTriggerAction(ButtonTrigger.DOUBLE_TAP).type != ActionType.NONE
            },
            onPositionSaved = { xr, yr ->
                repo.saveButtonPosition(xr, yr)
            }
        )

        try {
            windowManager.addView(btn, params)
            floatingButton = btn
            buttonParams = params
        } catch (_: Throwable) {
        }
    }

    private fun removeFloatingButton() {
        floatingButton?.let { btn ->
            btn.cancelTimers()
            try {
                windowManager.removeView(btn)
            } catch (_: Throwable) {
            }
        }
        floatingButton = null
        buttonParams = null
    }

    private fun rebuildEdgeTriggers(
        side: EdgeSide,
        widthDp: Int,
        heightDp: Int,
        locationRatio: Float,
        colorInt: Int,
        transparency: Float
    ) {
        removeEdgeTriggers()
        val density = resources.displayMetrics.density
        val widthPx = (widthDp * density).roundToInt()
        val heightPx = (heightDp * density).roundToInt()
        val screenH = resources.displayMetrics.heightPixels
        val minY = (32f * density).roundToInt()
        val maxY = (screenH - heightPx - minY).coerceAtLeast(minY)
        val posY = (minY + ((maxY - minY) * locationRatio.coerceIn(0f, 1f))).roundToInt()

        if (side == EdgeSide.LEFT || side == EdgeSide.BOTH) {
            addEdgeStrip(isLeft = true, widthPx = widthPx, heightPx = heightPx, yPx = posY, colorInt = colorInt, transparency = transparency)
        }
        if (side == EdgeSide.RIGHT || side == EdgeSide.BOTH) {
            addEdgeStrip(isLeft = false, widthPx = widthPx, heightPx = heightPx, yPx = posY, colorInt = colorInt, transparency = transparency)
        }
    }

    private fun addEdgeStrip(
        isLeft: Boolean,
        widthPx: Int,
        heightPx: Int,
        yPx: Int,
        colorInt: Int,
        transparency: Float
    ) {
        val screenW = resources.displayMetrics.widthPixels
        val maxX = (screenW - widthPx).coerceAtLeast(0)
        val params = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (isLeft) 0 else maxX
            y = yPx
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        lateinit var strip: EdgeTrigger
        strip = EdgeTrigger(
            context = this,
            windowManager = windowManager,
            layoutParams = params,
            isLeftSide = isLeft,
            hapticsEnabled = { repo.getSettings().haptics },
            isDirectDraw = { repo.getSettings().sideTriggerMode == SideTriggerMode.DIRECT_DRAW },
            onEdgeOpenPadOnly = { showGesturePad(fromEdge = false) },
            onEdgeSwipeStart = { rawX, rawY, downTime, eventTime ->
                showGesturePad(fromEdge = true)
                gesturePadOverlay?.forwardTouchEvent(MotionEvent.ACTION_DOWN, rawX, rawY, downTime, eventTime)
            },
            onEdgeSwipeMove = { rawX, rawY, downTime, eventTime ->
                gesturePadOverlay?.forwardTouchEvent(MotionEvent.ACTION_MOVE, rawX, rawY, downTime, eventTime)
            },
            onEdgeSwipeEnd = { rawX, rawY, downTime, eventTime, cancelled ->
                val action = if (cancelled) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP
                gesturePadOverlay?.forwardTouchEvent(action, rawX, rawY, downTime, eventTime)
            },
            onHandleRepositioned = { newIsLeft, newLocationRatio ->
                handleSideRepositioned(strip, originalWasLeft = isLeft, newIsLeft = newIsLeft, newLocationRatio = newLocationRatio)
            }
        ).apply {
            applyStyle(colorInt, transparency)
        }
        try {
            windowManager.addView(strip, params)
            edgeTriggers.add(strip)
        } catch (_: Throwable) {
        }
    }

    private fun handleSideRepositioned(
        movedStrip: EdgeTrigger,
        originalWasLeft: Boolean,
        newIsLeft: Boolean,
        newLocationRatio: Float
    ) {
        val settings = repo.getSettings()
        suppressSelfSidePrefRefresh = true
        if (settings.edgeSide == EdgeSide.BOTH && newIsLeft == originalWasLeft) {
            repo.saveSideHandlePosition(newLocationRatio, null)
            val density = resources.displayMetrics.density
            val heightPx = (settings.sideHeightDp * density).roundToInt()
            val screenH = resources.displayMetrics.heightPixels
            val minY = (32f * density).roundToInt()
            val maxY = (screenH - heightPx - minY).coerceAtLeast(minY)
            val syncedY = (minY + ((maxY - minY) * newLocationRatio)).roundToInt()
            edgeTriggers.filter { it !== movedStrip }.forEach { it.updateYPosition(syncedY) }
        } else {
            val newSide = if (newIsLeft) EdgeSide.LEFT else EdgeSide.RIGHT
            repo.saveSideHandlePosition(newLocationRatio, newSide)
            val toRemove = edgeTriggers.filter { it !== movedStrip }
            toRemove.forEach { other ->
                other.cancelTimers()
                try {
                    windowManager.removeView(other)
                } catch (_: Throwable) {
                }
                edgeTriggers.remove(other)
            }
        }
        mainHandler.post { suppressSelfSidePrefRefresh = false }
    }

    private fun removeEdgeTriggers() {
        edgeTriggers.forEach { view ->
            view.cancelTimers()
            try {
                windowManager.removeView(view)
            } catch (_: Throwable) {
            }
        }
        edgeTriggers.clear()
    }

    fun showGesturePad(fromEdge: Boolean = false) {
        try {
            if (isCleanedUp || gesturePadOverlay != null || !::repo.isInitialized) return
            repo.reloadLibrary()
            setHandlersHiddenForPad(true)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            val pad = GesturePadOverlay(
                context = LocaleHelper.wrapContext(this),
                repo = repo,
                openedFromEdge = fromEdge,
                onDismiss = { hideGesturePad() },
                onShortcutMatched = { shortcut ->
                    hideGesturePad()
                    actionExecutor.executeChain(shortcut.actions, shortcut.delayMs, initialDelayMs = 130L)
                },
                onOpenApp = { openVeloaApp() }
            )
            windowManager.addView(pad, params)
            gesturePadOverlay = pad
        } catch (_: Throwable) {
            setHandlersHiddenForPad(false)
        }
    }

    fun hideGesturePad() {
        gesturePadOverlay?.let { pad ->
            try {
                windowManager.removeView(pad)
            } catch (_: Throwable) {
            }
        }
        gesturePadOverlay = null
        setHandlersHiddenForPad(false)
    }

    private fun openVeloaApp() {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
        } catch (_: Throwable) {
        }
    }

    private fun removeAllOverlays() {
        syncKeyboardObserver(false)
        hideGesturePad()
        removeFloatingButton()
        removeEdgeTriggers()
    }

    companion object {
        @Volatile
        var instance: GestureAccessibilityService? = null
            private set

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun isEnabledInSystemSettings(context: Context): Boolean {
            return try {
                val enabled = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false
                val cn = ComponentName(context, GestureAccessibilityService::class.java)
                val full = cn.flattenToString()
                val short = cn.flattenToShortString()
                enabled.split(':').any { it.equals(full, ignoreCase = true) || it.equals(short, ignoreCase = true) }
            } catch (_: Throwable) {
                false
            }
        }
    }
}
