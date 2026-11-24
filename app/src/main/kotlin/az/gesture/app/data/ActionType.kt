package az.gesture.app.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import az.gesture.app.R

enum class ParamType {
    NONE,
    APP,
    URL,
    PHONE
}

enum class ActionType(
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
    val paramType: ParamType = ParamType.NONE
) {
    NONE(R.string.action_none, R.drawable.ic_action_none),
    OPEN_GESTURE_PAD(R.string.action_open_gesture_pad, R.drawable.ic_action_gesture_pad),
    BACK(R.string.action_back, R.drawable.ic_action_back),
    HOME(R.string.action_home, R.drawable.ic_action_home),
    RECENTS(R.string.action_recents, R.drawable.ic_action_recents),
    NOTIFICATIONS(R.string.action_notifications, R.drawable.ic_action_notifications),
    QUICK_SETTINGS(R.string.action_quick_settings, R.drawable.ic_action_quick_settings),
    POWER_DIALOG(R.string.action_power_dialog, R.drawable.ic_action_power),
    LOCK_SCREEN(R.string.action_lock_screen, R.drawable.ic_action_lock),
    SCREENSHOT(R.string.action_screenshot, R.drawable.ic_action_screenshot),
    SPLIT_SCREEN(R.string.action_split_screen, R.drawable.ic_action_split_screen),
    LAST_APP(R.string.action_last_app, R.drawable.ic_action_last_app),
    LAUNCH_APP(R.string.action_launch_app, R.drawable.ic_action_apps, ParamType.APP),
    OPEN_URL(R.string.action_open_url, R.drawable.ic_action_url, ParamType.URL),
    DIAL(R.string.action_dial, R.drawable.ic_action_dial, ParamType.PHONE),
    FLASHLIGHT(R.string.action_flashlight, R.drawable.ic_action_flashlight),
    VOLUME_PANEL(R.string.action_volume_panel, R.drawable.ic_action_volume),
    MEDIA_PLAY_PAUSE(R.string.action_media_play_pause, R.drawable.ic_action_play_pause),
    MEDIA_NEXT(R.string.action_media_next, R.drawable.ic_action_next),
    MEDIA_PREV(R.string.action_media_prev, R.drawable.ic_action_prev),
    OPEN_WIFI_SETTINGS(R.string.action_wifi_settings, R.drawable.ic_action_wifi),
    OPEN_BT_SETTINGS(R.string.action_bluetooth_settings, R.drawable.ic_action_bluetooth),
    ROTATION_SETTINGS(R.string.action_rotation_settings, R.drawable.ic_action_rotation);

    companion object {
        fun safeValueOf(name: String?): ActionType {
            if (name.isNullOrBlank()) return NONE
            return entries.firstOrNull { it.name == name } ?: NONE
        }
    }
}
