package az.gesture.app.data

import android.content.Context
import android.content.SharedPreferences
import android.gesture.Gesture
import android.gesture.GestureLibraries
import android.gesture.GestureLibrary
import android.gesture.GestureStore
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class RecognitionResult(
    val shortcut: Shortcut?,
    val bestScore: Double
)

class Repo private constructor(context: Context) {
    private val appContext = context.applicationContext
    val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val gestureFile = File(appContext.filesDir, "gestures")
    private var library: GestureLibrary = createLibrary()

    private fun createLibrary(): GestureLibrary {
        val settings = getSettings()
        return GestureLibraries.fromFile(gestureFile).apply {
            orientationStyle = if (settings.directionPriority) {
                GestureStore.ORIENTATION_SENSITIVE
            } else {
                GestureStore.ORIENTATION_INVARIANT
            }
            sequenceType = if (settings.multiStroke) {
                GestureStore.SEQUENCE_SENSITIVE
            } else {
                GestureStore.SEQUENCE_INVARIANT
            }
            if (gestureFile.exists()) {
                load()
            }
        }
    }

    @Synchronized
    fun reloadLibrary() {
        library = createLibrary()
    }

    fun getShortcuts(): List<Shortcut> {
        val raw = prefs.getString(KEY_SHORTCUTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { add(Shortcut.fromJson(it)) }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveShortcutsList(list: List<Shortcut>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_SHORTCUTS, arr.toString()).apply()
    }

    @Synchronized
    fun saveShortcut(shortcut: Shortcut, gesture: Gesture?) {
        val current = getShortcuts().toMutableList()
        val idx = current.indexOfFirst { it.id == shortcut.id }
        if (idx >= 0) {
            current[idx] = shortcut
        } else {
            current.add(shortcut)
        }
        if (gesture != null) {
            library.removeEntry(shortcut.id)
            library.addGesture(shortcut.id, gesture)
            library.save()
        }
        saveShortcutsList(current)
    }

    @Synchronized
    fun deleteShortcut(id: String) {
        val current = getShortcuts().filterNot { it.id == id }
        library.removeEntry(id)
        library.save()
        saveShortcutsList(current)
    }

    @Synchronized
    fun getGesture(id: String): Gesture? {
        return library.getGestures(id)?.firstOrNull()
    }

    @Synchronized
    fun getGestureThumbnail(id: String, width: Int, height: Int, color: Int): Bitmap? {
        val gesture = library.getGestures(id)?.firstOrNull() ?: return null
        return try {
            gesture.toBitmap(width, height, 10, color)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun recognize(gesture: Gesture): RecognitionResult {
        val shortcuts = getShortcuts()
        if (shortcuts.isEmpty()) {
            return RecognitionResult(null, 0.0)
        }
        val templates = shortcuts.mapNotNull { shortcut ->
            val savedGesture = library.getGestures(shortcut.id)?.firstOrNull() ?: return@mapNotNull null
            shortcut to savedGesture
        }
        if (templates.isEmpty()) {
            return RecognitionResult(null, 0.0)
        }
        val settings = getSettings()
        return GestureRecognizer.recognize(
            candidate = gesture,
            templates = templates,
            sensitivityPercent = settings.sensitivityPercent,
            directionPriority = settings.directionPriority,
            multiStroke = settings.multiStroke
        )
    }

    fun getTriggerAction(trigger: ButtonTrigger): GestureAction {
        val raw = prefs.getString(trigger.key, null) ?: return trigger.defaultAction
        return try {
            GestureAction.fromJson(JSONObject(raw))
        } catch (_: Exception) {
            trigger.defaultAction
        }
    }

    fun setTriggerAction(trigger: ButtonTrigger, action: GestureAction) {
        prefs.edit().putString(trigger.key, action.toJson().toString()).apply()
    }

    fun getAllTriggerMappings(): Map<ButtonTrigger, GestureAction> =
        ButtonTrigger.entries.associateWith { getTriggerAction(it) }

    fun getSettings(): ButtonSettings = ButtonSettings(
        handlerType = HandlerType.safeValueOf(prefs.getString(KEY_HANDLER_TYPE, HandlerType.SIDE.name)),
        themeMode = AppThemeMode.safeValueOf(prefs.getString(KEY_THEME_MODE, AppThemeMode.DARK.name)),
        colorTheme = AppColorTheme.safeValueOf(prefs.getString(KEY_COLOR_THEME, AppColorTheme.OCEAN.name)),
        language = AppLanguage.safeValueOf(prefs.getString(KEY_LANGUAGE, AppLanguage.AZ.name)),
        hideOnKeyboard = prefs.getBoolean(KEY_HIDE_ON_KEYBOARD, true),
        sensitivityPercent = prefs.getInt(KEY_SENSITIVITY_PCT, 80).coerceIn(50, 95),
        gestureColor = prefs.getInt(KEY_GESTURE_COLOR, 0xFF2DD4BF.toInt()),
        directionPriority = prefs.getBoolean(KEY_DIRECTION_PRIORITY, true),
        multiStroke = prefs.getBoolean(KEY_MULTI_STROKE, true),
        sizeDp = prefs.getInt(KEY_SIZE_DP, 48).coerceIn(30, 80),
        idleOpacity = prefs.getInt(KEY_IDLE_OPACITY, 80).coerceIn(10, 100),
        enableDoubleTap = prefs.getBoolean(KEY_ENABLE_DOUBLE_TAP, true),
        colorIndex = prefs.getInt(KEY_COLOR_INDEX, 0).coerceIn(0, BUTTON_COLOR_PRESETS.lastIndex),
        buttonXRatio = prefs.getFloat(KEY_BUTTON_X_RATIO, 1f).coerceIn(0f, 1f),
        buttonYRatio = prefs.getFloat(KEY_BUTTON_Y_RATIO, 0.55f).coerceIn(0f, 1f),
        sideWidthDp = prefs.getInt(KEY_SIDE_WIDTH, 16).coerceIn(8, 32),
        sideHeightDp = prefs.getInt(KEY_SIDE_HEIGHT, 110).coerceIn(50, 320),
        sideTransparency = prefs.getFloat(KEY_SIDE_TRANSPARENCY, 0.55f).coerceIn(0.1f, 1.0f),
        sideLocation = prefs.getFloat(KEY_SIDE_LOCATION, 0.65f).coerceIn(0.0f, 1.0f),
        edgeSide = EdgeSide.safeValueOf(prefs.getString(KEY_EDGE_SIDE, EdgeSide.RIGHT.name)),
        sideColor = prefs.getInt(KEY_SIDE_COLOR, 0xFF2DD4BF.toInt()),
        sideTriggerMode = SideTriggerMode.safeValueOf(
            prefs.getString(KEY_SIDE_TRIGGER_MODE, SideTriggerMode.OPEN_THEN_DRAW.name)
        ),
        haptics = prefs.getBoolean(KEY_HAPTICS, true),
        hideInLandscape = prefs.getBoolean(KEY_HIDE_LANDSCAPE, false)
    )

    fun updateSettings(transform: (ButtonSettings) -> ButtonSettings) {
        val next = transform(getSettings())
        prefs.edit()
            .putString(KEY_HANDLER_TYPE, next.handlerType.name)
            .putString(KEY_THEME_MODE, next.themeMode.name)
            .putString(KEY_COLOR_THEME, next.colorTheme.name)
            .putString(KEY_LANGUAGE, next.language.name)
            .putBoolean(KEY_HIDE_ON_KEYBOARD, next.hideOnKeyboard)
            .putInt(KEY_SENSITIVITY_PCT, next.sensitivityPercent.coerceIn(50, 95))
            .putInt(KEY_GESTURE_COLOR, next.gestureColor)
            .putBoolean(KEY_DIRECTION_PRIORITY, next.directionPriority)
            .putBoolean(KEY_MULTI_STROKE, next.multiStroke)
            .putInt(KEY_SIZE_DP, next.sizeDp.coerceIn(30, 80))
            .putInt(KEY_IDLE_OPACITY, next.idleOpacity.coerceIn(10, 100))
            .putBoolean(KEY_ENABLE_DOUBLE_TAP, next.enableDoubleTap)
            .putInt(KEY_COLOR_INDEX, next.colorIndex.coerceIn(0, BUTTON_COLOR_PRESETS.lastIndex))
            .putFloat(KEY_BUTTON_X_RATIO, next.buttonXRatio.coerceIn(0f, 1f))
            .putFloat(KEY_BUTTON_Y_RATIO, next.buttonYRatio.coerceIn(0f, 1f))
            .putInt(KEY_SIDE_WIDTH, next.sideWidthDp.coerceIn(8, 32))
            .putInt(KEY_SIDE_HEIGHT, next.sideHeightDp.coerceIn(50, 320))
            .putFloat(KEY_SIDE_TRANSPARENCY, next.sideTransparency.coerceIn(0.1f, 1.0f))
            .putFloat(KEY_SIDE_LOCATION, next.sideLocation.coerceIn(0.0f, 1.0f))
            .putString(KEY_EDGE_SIDE, next.edgeSide.name)
            .putInt(KEY_SIDE_COLOR, next.sideColor)
            .putString(KEY_SIDE_TRIGGER_MODE, next.sideTriggerMode.name)
            .putBoolean(KEY_HAPTICS, next.haptics)
            .putBoolean(KEY_HIDE_LANDSCAPE, next.hideInLandscape)
            .apply()
    }

    fun saveButtonPosition(xRatio: Float, yRatio: Float) {
        prefs.edit()
            .putFloat(KEY_BUTTON_X_RATIO, xRatio.coerceIn(0f, 1f))
            .putFloat(KEY_BUTTON_Y_RATIO, yRatio.coerceIn(0f, 1f))
            .apply()
    }

    fun isAutoStartConfirmed(): Boolean = prefs.getBoolean(KEY_AUTO_START_CONFIRMED, false)

    fun setAutoStartConfirmed(confirmed: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_START_CONFIRMED, confirmed).apply()
    }

    fun isXiaomiPopupConfirmed(): Boolean = prefs.getBoolean(KEY_XIAOMI_POPUP_CONFIRMED, false)

    fun setXiaomiPopupConfirmed(confirmed: Boolean) {
        prefs.edit().putBoolean(KEY_XIAOMI_POPUP_CONFIRMED, confirmed).apply()
    }

    fun isXiaomiLockConfirmed(): Boolean = prefs.getBoolean(KEY_XIAOMI_LOCK_CONFIRMED, false)

    fun setXiaomiLockConfirmed(confirmed: Boolean) {
        prefs.edit().putBoolean(KEY_XIAOMI_LOCK_CONFIRMED, confirmed).apply()
    }

    fun wasNotificationRequested(): Boolean = prefs.getBoolean(KEY_NOTIFICATION_REQUESTED, false)

    fun markNotificationRequested() {
        prefs.edit().putBoolean(KEY_NOTIFICATION_REQUESTED, true).apply()
    }

    companion object {
        private const val PREFS_NAME = "gesture_prefs"
        private const val KEY_SHORTCUTS = "shortcuts_json"
        private const val KEY_HANDLER_TYPE = "handler_type"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_COLOR_THEME = "color_theme"
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_HIDE_ON_KEYBOARD = "hide_on_keyboard"
        private const val KEY_SENSITIVITY_PCT = "sensitivity_percent"
        private const val KEY_GESTURE_COLOR = "gesture_color"
        private const val KEY_DIRECTION_PRIORITY = "direction_priority"
        private const val KEY_MULTI_STROKE = "multi_stroke"
        private const val KEY_SIZE_DP = "button_size_dp"
        private const val KEY_IDLE_OPACITY = "button_idle_opacity"
        private const val KEY_ENABLE_DOUBLE_TAP = "enable_double_tap"
        private const val KEY_COLOR_INDEX = "button_color_index"
        private const val KEY_BUTTON_X_RATIO = "button_x_ratio"
        private const val KEY_BUTTON_Y_RATIO = "button_y_ratio"
        private const val KEY_SIDE_WIDTH = "side_width_dp"
        private const val KEY_SIDE_HEIGHT = "side_height_dp"
        private const val KEY_SIDE_TRANSPARENCY = "side_transparency"
        private const val KEY_SIDE_LOCATION = "side_location"
        private const val KEY_EDGE_SIDE = "edge_side"
        private const val KEY_SIDE_COLOR = "side_color"
        private const val KEY_SIDE_TRIGGER_MODE = "side_trigger_mode"
        private const val KEY_HAPTICS = "haptics_enabled"
        private const val KEY_HIDE_LANDSCAPE = "hide_in_landscape"
        private const val KEY_AUTO_START_CONFIRMED = "auto_start_confirmed"
        private const val KEY_XIAOMI_POPUP_CONFIRMED = "xiaomi_popup_confirmed"
        private const val KEY_XIAOMI_LOCK_CONFIRMED = "xiaomi_lock_confirmed"
        private const val KEY_NOTIFICATION_REQUESTED = "notification_requested"

        @Volatile
        private var instance: Repo? = null

        fun get(context: Context): Repo =
            instance ?: synchronized(this) {
                instance ?: Repo(context).also { instance = it }
            }
    }
}
