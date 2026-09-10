package com.example

import android.content.Context
import android.content.SharedPreferences

/**
 * Storage for Status Bar Volume Gesture preferences.
 */
class GesturePreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("quickvolume_gesture_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_GESTURE_ENABLED = "key_gesture_enabled"
        private const val KEY_FLOATING_BALL_ENABLED = "key_floating_ball_enabled"
        private const val KEY_HAPTIC_ENABLED = "key_haptic_enabled"
        private const val KEY_SENSITIVITY = "key_sensitivity"
        private const val KEY_VISUAL_LINE = "key_visual_line"
        private const val KEY_BALL_POS_X = "key_ball_pos_x"
        private const val KEY_BALL_POS_Y = "key_ball_pos_y"
        private const val KEY_BOOT_START = "key_boot_start"
        private const val KEY_SNAP_TO_EDGE = "key_snap_to_edge"
        private const val KEY_IDLE_DIMMING = "key_idle_dimming"
        private const val KEY_BALL_SIZE_DP = "key_ball_size_dp"
    }

    var isFloatingBallEnabled: Boolean
        get() = prefs.getBoolean(KEY_FLOATING_BALL_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_FLOATING_BALL_ENABLED, value).apply()

    var isGestureEnabled: Boolean
        get() = prefs.getBoolean(KEY_GESTURE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_GESTURE_ENABLED, value).apply()

    var isHapticEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC_ENABLED, value).apply()

    var isBootStartEnabled: Boolean
        get() = prefs.getBoolean(KEY_BOOT_START, true)
        set(value) = prefs.edit().putBoolean(KEY_BOOT_START, value).apply()

    var isSnapToEdgeEnabled: Boolean
        get() = prefs.getBoolean(KEY_SNAP_TO_EDGE, true)
        set(value) = prefs.edit().putBoolean(KEY_SNAP_TO_EDGE, value).apply()

    var isIdleDimmingEnabled: Boolean
        get() = prefs.getBoolean(KEY_IDLE_DIMMING, true)
        set(value) = prefs.edit().putBoolean(KEY_IDLE_DIMMING, value).apply()

    var ballPosX: Int
        get() = prefs.getInt(KEY_BALL_POS_X, -1)
        set(value) = prefs.edit().putInt(KEY_BALL_POS_X, value).apply()

    var ballPosY: Int
        get() = prefs.getInt(KEY_BALL_POS_Y, -1)
        set(value) = prefs.edit().putInt(KEY_BALL_POS_Y, value).apply()

    var ballSizeDp: Int
        get() = prefs.getInt(KEY_BALL_SIZE_DP, 56)
        set(value) = prefs.edit().putInt(KEY_BALL_SIZE_DP, value).apply()

    /**
     * Sensitivity factor: 0.5f (less sensitive) to 2.0f (more sensitive). Default: 1.0f.
     */
    var sensitivity: Float
        get() = prefs.getFloat(KEY_SENSITIVITY, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_SENSITIVITY, value).apply()

    var showVisualLine: Boolean
        get() = prefs.getBoolean(KEY_VISUAL_LINE, true)
        set(value) = prefs.edit().putBoolean(KEY_VISUAL_LINE, value).apply()
}
