package az.gesture.app.data

import androidx.annotation.StringRes
import az.gesture.app.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Shortcut(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val actions: List<GestureAction>,
    val delayMs: Int = 350
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("delayMs", delayMs)
        val arr = JSONArray()
        actions.forEach { arr.put(it.toJson()) }
        put("actions", arr)
    }

    companion object {
        fun fromJson(obj: JSONObject): Shortcut {
            val id = obj.optString("id").ifBlank { UUID.randomUUID().toString() }
            val name = obj.optString("name", "")
            val delayMs = obj.optInt("delayMs", 350).coerceIn(100, 3000)
            val arr = obj.optJSONArray("actions") ?: JSONArray()
            val list = buildList {
                for (i in 0 until arr.length()) {
                    add(GestureAction.fromJson(arr.optJSONObject(i)))
                }
            }
            return Shortcut(id = id, name = name, actions = list, delayMs = delayMs)
        }
    }
}

enum class HandlerType {
    SIDE,
    FLOATING,
    BOTH;

    companion object {
        fun safeValueOf(name: String?): HandlerType =
            entries.firstOrNull { it.name == name } ?: SIDE
    }
}

enum class SideTriggerMode {
    OPEN_THEN_DRAW,
    DIRECT_DRAW;

    companion object {
        fun safeValueOf(name: String?): SideTriggerMode =
            entries.firstOrNull { it.name == name } ?: OPEN_THEN_DRAW
    }
}

enum class AppThemeMode {
    LIGHT,
    DARK,
    AMOLED;

    companion object {
        fun safeValueOf(name: String?): AppThemeMode = when (name) {
            AMOLED.name -> AMOLED
            LIGHT.name -> LIGHT
            else -> DARK
        }
    }
}

enum class AppColorTheme(
    @param:StringRes val labelRes: Int,
    val previewColor: Int
) {
    OCEAN(R.string.color_theme_ocean, 0xFF38BDF8.toInt()),
    EMERALD(R.string.color_theme_emerald, 0xFF2DD4BF.toInt()),
    VIOLET(R.string.color_theme_violet, 0xFFA78BFA.toInt()),
    CORAL(R.string.color_theme_coral, 0xFFFB7185.toInt()),
    AMBER(R.string.color_theme_amber, 0xFFFBBF24.toInt()),
    MONOCHROME(R.string.color_theme_monochrome, 0xFFE2E8F0.toInt());

    companion object {
        fun safeValueOf(name: String?): AppColorTheme =
            entries.firstOrNull { it.name == name } ?: OCEAN
    }
}

enum class AppLanguage(val tag: String, @param:StringRes val labelRes: Int) {
    AZ("az", R.string.lang_az),
    DE("de", R.string.lang_de),
    EN("en", R.string.lang_en),
    ES("es", R.string.lang_es),
    FR("fr", R.string.lang_fr),
    IT("it", R.string.lang_it),
    PL("pl", R.string.lang_pl),
    PT("pt", R.string.lang_pt),
    RU("ru", R.string.lang_ru),
    TR("tr", R.string.lang_tr),
    UK("uk", R.string.lang_uk);

    companion object {
        fun safeValueOf(name: String?): AppLanguage =
            entries.firstOrNull { it.name == name } ?: AZ
    }
}

enum class ButtonTrigger(
    val key: String,
    @param:StringRes val labelRes: Int,
    val defaultAction: GestureAction
) {
    TAP("trigger_tap", R.string.trigger_tap, GestureAction(ActionType.OPEN_GESTURE_PAD)),
    DOUBLE_TAP("trigger_double_tap", R.string.trigger_double_tap, GestureAction(ActionType.LOCK_SCREEN)),
    LONG_PRESS("trigger_long_press", R.string.trigger_long_press, GestureAction(ActionType.NONE)),
    SWIPE_LEFT("trigger_swipe_left", R.string.trigger_swipe_left, GestureAction(ActionType.BACK)),
    SWIPE_UP("trigger_swipe_up", R.string.trigger_swipe_up, GestureAction(ActionType.HOME)),
    SWIPE_RIGHT("trigger_swipe_right", R.string.trigger_swipe_right, GestureAction(ActionType.RECENTS)),
    SWIPE_DOWN("trigger_swipe_down", R.string.trigger_swipe_down, GestureAction(ActionType.NOTIFICATIONS))
}

enum class EdgeSide {
    LEFT, RIGHT, BOTH;

    companion object {
        fun safeValueOf(name: String?): EdgeSide =
            entries.firstOrNull { it.name == name } ?: RIGHT
    }
}

data class ButtonColorPreset(
    val outerColor: Int,
    val innerColor: Int,
    val accentColor: Int
)

val BUTTON_COLOR_PRESETS = listOf(
    ButtonColorPreset(0xFF2DD4BF.toInt(), 0xFF042F2E.toInt(), 0xFF2DD4BF.toInt()),
    ButtonColorPreset(0xFF38BDF8.toInt(), 0xFF0C4A6E.toInt(), 0xFF38BDF8.toInt()),
    ButtonColorPreset(0xFF8A8A8A.toInt(), 0xFF3A3A3A.toInt(), 0xFFA8C7FA.toInt()),
    ButtonColorPreset(0xFFA78BFA.toInt(), 0xFF3B0764.toInt(), 0xFFA78BFA.toInt()),
    ButtonColorPreset(0xFFFBBF24.toInt(), 0xFF451A03.toInt(), 0xFFFBBF24.toInt())
)

val GESTURE_COLOR_OPTIONS = listOf(
    0xFF2DD4BF.toInt(),
    0xFF38BDF8.toInt(),
    0xFFA8C7FA.toInt(),
    0xFF818CF8.toInt(),
    0xFFC084FC.toInt(),
    0xFFF472B6.toInt(),
    0xFFFB7185.toInt(),
    0xFFFB923C.toInt(),
    0xFFFBBF24.toInt(),
    0xFFA3E635.toInt(),
    0xFF34D399.toInt(),
    0xFF06B6D4.toInt(),
    0xFFF43F5E.toInt(),
    0xFFFFFFFF.toInt()
)

val SIDE_HANDLE_COLOR_OPTIONS = listOf(
    0xFF2DD4BF.toInt(),
    0xFF2A9D8F.toInt(),
    0xFF38BDF8.toInt(),
    0xFF818CF8.toInt(),
    0xFFC084FC.toInt(),
    0xFFF472B6.toInt(),
    0xFFFB923C.toInt(),
    0xFFFBBF24.toInt(),
    0xFF34D399.toInt(),
    0xFF94A3B8.toInt()
)

data class ButtonSettings(
    val handlerType: HandlerType = HandlerType.SIDE,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val colorTheme: AppColorTheme = AppColorTheme.OCEAN,
    val language: AppLanguage = AppLanguage.AZ,
    val hideOnKeyboard: Boolean = true,
    val sensitivityPercent: Int = 80,
    val gestureColor: Int = 0xFF2DD4BF.toInt(),
    val directionPriority: Boolean = true,
    val multiStroke: Boolean = true,
    val sizeDp: Int = 48,
    val idleOpacity: Int = 80,
    val enableDoubleTap: Boolean = true,
    val colorIndex: Int = 0,
    val buttonXRatio: Float = 1f,
    val buttonYRatio: Float = 0.55f,
    val sideWidthDp: Int = 16,
    val sideHeightDp: Int = 110,
    val sideTransparency: Float = 0.55f,
    val sideLocation: Float = 0.65f,
    val edgeSide: EdgeSide = EdgeSide.RIGHT,
    val sideColor: Int = 0xFF2DD4BF.toInt(),
    val sideTriggerMode: SideTriggerMode = SideTriggerMode.OPEN_THEN_DRAW,
    val haptics: Boolean = true,
    val hideInLandscape: Boolean = false
) {
    val floatingEnabled: Boolean
        get() = handlerType == HandlerType.FLOATING || handlerType == HandlerType.BOTH

    val sideEnabled: Boolean
        get() = handlerType == HandlerType.SIDE || handlerType == HandlerType.BOTH

    val recognitionThreshold: Double
        get() = GestureRecognizer.minSimilarityForSensitivity(sensitivityPercent)
}
