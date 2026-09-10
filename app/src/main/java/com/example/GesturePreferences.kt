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
        private const val KEY_HAPTIC_ENABLED = "key_haptic_enabled"
        private const val KEY_SENSITIVITY = "key_sensitivity"
        private const val KEY_VISUAL_LINE = "key_visual_line"
    }

    var isGestureEnabled: Boolean
        get() = prefs.getBoolean(KEY_GESTURE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_GESTURE_ENABLED, value).apply()

    var isHapticEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC_ENABLED, value).apply()

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
