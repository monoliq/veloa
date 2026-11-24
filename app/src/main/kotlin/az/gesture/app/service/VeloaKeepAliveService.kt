package az.gesture.app.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import az.gesture.app.MainActivity
import az.gesture.app.R
import az.gesture.app.data.ActionType
import az.gesture.app.data.ButtonTrigger
import az.gesture.app.data.EdgeSide
import az.gesture.app.data.LocaleHelper
import az.gesture.app.data.Repo
import az.gesture.app.data.SideTriggerMode
import az.gesture.app.overlay.EdgeTrigger
import az.gesture.app.overlay.FloatingButton
import az.gesture.app.overlay.GesturePadOverlay
import kotlin.math.roundToInt

/**
 * Dedicated START_STICKY foreground service that keeps Veloa alive after the user closes the app
 * or clears Recents. Also hosts TYPE_APPLICATION_OVERLAY fallback handles automatically if an
 * OEM cleaner disconnects GestureAccessibilityService while SYSTEM_ALERT_WINDOW is granted.
 */
class VeloaKeepAliveService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var repo: Repo
    private lateinit var windowManager: WindowManager
    private lateinit var fallbackExecutor: ActionExecutor

    private var floatingButton: FloatingButton? = null
    private var buttonParams: WindowManager.LayoutParams? = null
    private val edgeTriggers = mutableListOf<EdgeTrigger>()
    private var gesturePadOverlay: GesturePadOverlay? = null
    private var isLandscape = false
    private var prefListenerRegistered = false
    private var screenReceiverRegistered = false

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != "button_x_ratio" &&
            key != "button_y_ratio" &&
            key != "sensitivity_percent" &&
            key != "direction_priority"
        ) {
            mainHandler.post {
                promoteToForeground()
                syncFallbackOverlays()
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            mainHandler.post {
                if (action == Intent.ACTION_SCREEN_OFF) {
                    pauseWatchdogLoop()
                } else {
                    promoteToForeground()
                    startWatchdogLoop()
                    if (GestureAccessibilityService.instance != null) {
                        removeFallbackOverlays()
                        GestureAccessibilityService.instance?.refreshOverlays()
                    } else {
                        syncFallbackOverlays()
                    }
                }
            }
        }
    }

    private val watchdogPetRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            val interactive = pm?.isInteractive ?: true
            if (!interactive) {
                pauseWatchdogLoop()
                return
            }
            if (!shouldStayAlive(this@VeloaKeepAliveService)) {
                pauseWatchdogLoop()
                return
            }
            petWatchdog()
            mainHandler.postDelayed(this, WATCHDOG_PET_INTERVAL_MS)
        }
    }

    private fun petWatchdog() {
        VeloaWatchdogJobService.armDeadManSwitch(
            applicationContext,
            minLatencyMs = WATCHDOG_JOB_MIN_LATENCY_MS,
            maxDelayMs = WATCHDOG_JOB_MAX_DELAY_MS
        )
        scheduleWatchdogRestart(applicationContext, WATCHDOG_ALARM_DELAY_MS)
    }

    private fun startWatchdogLoop() {
        mainHandler.removeCallbacks(watchdogPetRunnable)
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val interactive = pm?.isInteractive ?: true
        if (interactive && shouldStayAlive(this)) {
            petWatchdog()
            mainHandler.postDelayed(watchdogPetRunnable, WATCHDOG_PET_INTERVAL_MS)
        }
    }

    private fun pauseWatchdogLoop() {
        mainHandler.removeCallbacks(watchdogPetRunnable)
        VeloaWatchdogJobService.cancelDeadManSwitch(applicationContext)
        cancelWatchdogRestart(applicationContext)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        isRunning = true
        repo = Repo.get(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        fallbackExecutor = ActionExecutor(
            context = this,
            accessibilityService = null,
            onOpenGesturePad = { showFallbackGesturePad(fromEdge = false) }
        )
        isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (!prefListenerRegistered) {
            repo.prefs.registerOnSharedPreferenceChangeListener(prefListener)
            prefListenerRegistered = true
        }
        registerScreenReceiver()
        promoteToForeground()
        VeloaWatchdogJobService.schedulePeriodic(this)
        startWatchdogLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        isRunning = true
        promoteToForeground()
        VeloaWatchdogJobService.schedulePeriodic(this)

        if (!shouldStayAlive(this) && GestureAccessibilityService.instance == null) {
            pauseWatchdogLoop()
            VeloaWatchdogJobService.cancelAll(this)
            stopSelf()
            return START_NOT_STICKY
        }

        startWatchdogLoop()
        if (GestureAccessibilityService.instance != null) {
            removeFallbackOverlays()
            GestureAccessibilityService.instance?.refreshOverlays()
        } else {
            syncFallbackOverlays()
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        hideFallbackGesturePad()
        syncFallbackOverlays()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        try {
            if (shouldStayAlive(this)) {
                promoteToForeground()
                scheduleWatchdogRestart(applicationContext, 1200L)
                VeloaWatchdogJobService.scheduleQuickRecovery(applicationContext)
                mainHandler.postDelayed({
                    if (GestureAccessibilityService.instance != null) {
                        removeFallbackOverlays()
                        GestureAccessibilityService.instance?.refreshOverlays()
                    } else {
                        syncFallbackOverlays()
                    }
                }, 250L)
                start(applicationContext)
            }
        } catch (_: Throwable) {
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        if (prefListenerRegistered && ::repo.isInitialized) {
            try {
                repo.prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
            } catch (_: Throwable) {
            }
            prefListenerRegistered = false
        }
        unregisterScreenReceiver()
        removeFallbackOverlays()
        if (::fallbackExecutor.isInitialized) {
            fallbackExecutor.release()
        }
        if (instance === this) {
            instance = null
        }
        isRunning = false
        try {
            if (shouldStayAlive(this)) {
                scheduleWatchdogRestart(applicationContext, 1200L)
                VeloaWatchdogJobService.scheduleQuickRecovery(applicationContext)
            }
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            ContextCompat.registerReceiver(
                this,
                screenReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            screenReceiverRegistered = true
        } catch (_: Throwable) {
        }
    }

    private fun unregisterScreenReceiver() {
        if (!screenReceiverRegistered) return
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Throwable) {
        }
        screenReceiverRegistered = false
    }

    fun syncFallbackOverlays() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { syncFallbackOverlays() }
            return
        }
        try {
            if (GestureAccessibilityService.instance != null) {
                removeFallbackOverlays()
                return
            }
            if (!Settings.canDrawOverlays(this)) {
                removeFallbackOverlays()
                return
            }

            repo.reloadLibrary()
            val settings = repo.getSettings()
            val hideLandscape = settings.hideInLandscape && isLandscape

            if (!settings.floatingEnabled || hideLandscape) {
                removeFallbackFloatingButton()
            } else {
                ensureOrUpdateFallbackButton(settings.sizeDp, settings.buttonXRatio, settings.buttonYRatio)
                floatingButton?.applySettings(settings)
            }

            if (!settings.sideEnabled || hideLandscape) {
                removeFallbackEdgeTriggers()
            } else {
                rebuildFallbackEdgeTriggers(
                    side = settings.edgeSide,
                    widthDp = settings.sideWidthDp,
                    heightDp = settings.sideHeightDp,
                    locationRatio = settings.sideLocation,
                    colorInt = settings.sideColor,
                    transparency = settings.sideTransparency
                )
            }
        } catch (_: Throwable) {
        }
    }

    fun removeFallbackOverlays() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { removeFallbackOverlays() }
            return
        }
        hideFallbackGesturePad()
        removeFallbackFloatingButton()
        removeFallbackEdgeTriggers()
    }

    private fun ensureOrUpdateFallbackButton(sizeDp: Int, xRatio: Float, yRatio: Float) {
        val density = resources.displayMetrics.density
        val sizePx = (sizeDp * density).roundToInt()
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
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
            removeFallbackFloatingButton()
        }

        val initialX = if (xRatio < 0.5f) 0 else maxX
        val initialY = (marginY + ((maxY - marginY) * yRatio)).roundToInt().coerceIn(marginY, maxY)

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
                    fallbackExecutor.execute(repo.getTriggerAction(trigger))
                } catch (_: Throwable) {
                }
            },
            hasDoubleTapAction = {
                val s = repo.getSettings()
                s.enableDoubleTap && repo.getTriggerAction(ButtonTrigger.DOUBLE_TAP).type != ActionType.NONE
            },
            onPositionSaved = { xr, yr -> repo.saveButtonPosition(xr, yr) }
        )
        try {
            windowManager.addView(btn, params)
            floatingButton = btn
            buttonParams = params
        } catch (_: Throwable) {
        }
    }

    private fun removeFallbackFloatingButton() {
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

    private fun rebuildFallbackEdgeTriggers(
        side: EdgeSide,
        widthDp: Int,
        heightDp: Int,
        locationRatio: Float,
        colorInt: Int,
        transparency: Float
    ) {
        removeFallbackEdgeTriggers()
        val density = resources.displayMetrics.density
        val widthPx = (widthDp * density).roundToInt()
        val heightPx = (heightDp * density).roundToInt()
        val screenH = resources.displayMetrics.heightPixels
        val minY = (32f * density).roundToInt()
        val maxY = (screenH - heightPx - minY).coerceAtLeast(minY)
        val posY = (minY + ((maxY - minY) * locationRatio.coerceIn(0f, 1f))).roundToInt()

        if (side == EdgeSide.LEFT || side == EdgeSide.BOTH) {
            addFallbackEdgeStrip(true, widthPx, heightPx, posY, colorInt, transparency)
        }
        if (side == EdgeSide.RIGHT || side == EdgeSide.BOTH) {
            addFallbackEdgeStrip(false, widthPx, heightPx, posY, colorInt, transparency)
        }
    }

    private fun addFallbackEdgeStrip(
        isLeft: Boolean,
        widthPx: Int,
        heightPx: Int,
        yPx: Int,
        colorInt: Int,
        transparency: Float
    ) {
        val params = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = (if (isLeft) Gravity.START else Gravity.END) or Gravity.TOP
            y = yPx
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        val strip = EdgeTrigger(
            context = this,
            isLeftSide = isLeft,
            hapticsEnabled = { repo.getSettings().haptics },
            isDirectDraw = { repo.getSettings().sideTriggerMode == SideTriggerMode.DIRECT_DRAW },
            onEdgeOpenPadOnly = { showFallbackGesturePad(fromEdge = false) },
            onEdgeSwipeStart = { rawX, rawY, downTime, eventTime ->
                showFallbackGesturePad(fromEdge = true)
                gesturePadOverlay?.forwardTouchEvent(MotionEvent.ACTION_DOWN, rawX, rawY, downTime, eventTime)
            },
            onEdgeSwipeMove = { rawX, rawY, downTime, eventTime ->
                gesturePadOverlay?.forwardTouchEvent(MotionEvent.ACTION_MOVE, rawX, rawY, downTime, eventTime)
            },
            onEdgeSwipeEnd = { rawX, rawY, downTime, eventTime, cancelled ->
                val action = if (cancelled) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP
                gesturePadOverlay?.forwardTouchEvent(action, rawX, rawY, downTime, eventTime)
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

    private fun removeFallbackEdgeTriggers() {
        edgeTriggers.forEach { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Throwable) {
            }
        }
        edgeTriggers.clear()
    }

    fun showFallbackGesturePad(fromEdge: Boolean = false) {
        try {
            if (gesturePadOverlay != null || !Settings.canDrawOverlays(this)) return
            repo.reloadLibrary()
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
                onDismiss = { hideFallbackGesturePad() },
                onShortcutMatched = { shortcut ->
                    hideFallbackGesturePad()
                    fallbackExecutor.executeChain(shortcut.actions, shortcut.delayMs, initialDelayMs = 130L)
                }
            )
            windowManager.addView(pad, params)
            gesturePadOverlay = pad
        } catch (_: Throwable) {
        }
    }

    private fun hideFallbackGesturePad() {
        gesturePadOverlay?.let { pad ->
            try {
                windowManager.removeView(pad)
            } catch (_: Throwable) {
            }
        }
        gesturePadOverlay = null
    }

    private fun promoteToForeground() {
        try {
            val notification = buildKeepAliveNotification(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Throwable) {
        }
    }

    companion object {
        const val CHANNEL_ID = "glypha_service_channel"
        const val NOTIFICATION_ID = 1042
        private const val WATCHDOG_REQUEST_CODE = 2042
        private const val WATCHDOG_PET_INTERVAL_MS = 4_000L
        private const val WATCHDOG_JOB_MIN_LATENCY_MS = 6_000L
        private const val WATCHDOG_JOB_MAX_DELAY_MS = 7_500L
        private const val WATCHDOG_ALARM_DELAY_MS = 6_500L

        @Volatile
        var instance: VeloaKeepAliveService? = null
            private set

        @Volatile
        var isRunning: Boolean = false
            private set

        fun shouldStayAlive(context: Context): Boolean {
            return GestureAccessibilityService.isEnabledInSystemSettings(context) ||
                Settings.canDrawOverlays(context)
        }

        fun releaseFallbackOverlays() {
            instance?.removeFallbackOverlays()
        }

        fun activateFallbackIfNeeded() {
            instance?.syncFallbackOverlays()
        }

        fun buildKeepAliveNotification(context: Context): Notification {
            val localized = LocaleHelper.wrapContext(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (nm != null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    localized.getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                    lockscreenVisibility = Notification.VISIBILITY_SECRET
                }
                nm.createNotificationChannel(channel)
            }

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_action_gesture_pad)
                .setContentTitle(localized.getString(R.string.notif_service_title))
                .setContentText(localized.getString(R.string.notif_service_text))
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(pendingIntent)
                .build()
        }

        fun start(context: Context) {
            try {
                val intent = Intent(context.applicationContext, VeloaKeepAliveService::class.java)
                ContextCompat.startForegroundService(context.applicationContext, intent)
            } catch (_: Throwable) {
            }
        }

        fun stop(context: Context) {
            try {
                cancelWatchdogRestart(context.applicationContext)
                VeloaWatchdogJobService.cancelAll(context.applicationContext)
                context.applicationContext.stopService(
                    Intent(context.applicationContext, VeloaKeepAliveService::class.java)
                )
            } catch (_: Throwable) {
            }
        }

        fun cancelWatchdogRestart(context: Context) {
            try {
                val appCtx = context.applicationContext
                val alarmManager = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(appCtx, KeepAliveRestartReceiver::class.java)
                val pi = PendingIntent.getBroadcast(
                    appCtx,
                    WATCHDOG_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                ) ?: return
                alarmManager.cancel(pi)
                pi.cancel()
            } catch (_: Throwable) {
            }
        }

        fun scheduleWatchdogRestart(context: Context, delayMs: Long = 1200L) {
            try {
                val appCtx = context.applicationContext
                val alarmManager = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(appCtx, KeepAliveRestartReceiver::class.java)
                val pi = PendingIntent.getBroadcast(
                    appCtx,
                    WATCHDOG_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + delayMs,
                    pi
                )
            } catch (_: Throwable) {
            }
        }
    }
}
