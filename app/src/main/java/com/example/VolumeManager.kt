package com.example

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Helper manager for system media and voice call volume controls.
 * Uses AudioManager to control STREAM_MUSIC and STREAM_VOICE_CALL reliably across Android versions (including Android 13+).
 */
class VolumeManager(private val context: Context) {

    private val audioManager: AudioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Increase volume by 1 step for specified stream.
     */
    fun volumeUp(showUi: Boolean = true, streamType: Int = AudioManager.STREAM_MUSIC) {
        val flags = if (showUi) AudioManager.FLAG_SHOW_UI else 0
        try {
            audioManager.adjustStreamVolume(
                streamType,
                AudioManager.ADJUST_RAISE,
                flags
            )
        } catch (e: Exception) {
            Log.e("VolumeManager", "volumeUp failed for stream $streamType", e)
        }
    }

    /**
     * Decrease volume by 1 step for specified stream.
     */
    fun volumeDown(showUi: Boolean = true, streamType: Int = AudioManager.STREAM_MUSIC) {
        val flags = if (showUi) AudioManager.FLAG_SHOW_UI else 0
        try {
            audioManager.adjustStreamVolume(
                streamType,
                AudioManager.ADJUST_LOWER,
                flags
            )
        } catch (e: Exception) {
            Log.e("VolumeManager", "volumeDown failed for stream $streamType", e)
        }
    }

    /**
     * Returns the current volume level of specified stream.
     */
    fun getVolume(streamType: Int = AudioManager.STREAM_MUSIC): Int {
        return try {
            audioManager.getStreamVolume(streamType)
        } catch (e: Exception) {
            Log.e("VolumeManager", "getVolume failed for stream $streamType", e)
            0
        }
    }

    /**
     * Returns the maximum possible volume level of specified stream.
     */
    fun getMaxVolume(streamType: Int = AudioManager.STREAM_MUSIC): Int {
        return try {
            audioManager.getStreamMaxVolume(streamType)
        } catch (e: Exception) {
            Log.e("VolumeManager", "getMaxVolume failed for stream $streamType", e)
            if (streamType == AudioManager.STREAM_VOICE_CALL) 7 else 15
        }
    }

    /**
     * Returns the minimum possible volume level of specified stream (0 or API 28+ min volume).
     */
    fun getMinVolume(streamType: Int = AudioManager.STREAM_MUSIC): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                audioManager.getStreamMinVolume(streamType)
            } catch (e: Exception) {
                if (streamType == AudioManager.STREAM_VOICE_CALL) 1 else 0
            }
        } else {
            if (streamType == AudioManager.STREAM_VOICE_CALL) 1 else 0
        }
    }

    /**
     * Sets the stream volume directly to a specific level.
     * Incorporates a robust dual-path strategy:
     * 1. Direct setStreamVolume
     * 2. If an OEM implementation or Android 13 restriction prevents direct setStreamVolume with flags=0,
     *    automatically steps via adjustStreamVolume to reach the target volume.
     */
    fun setVolume(level: Int, showUi: Boolean = false, streamType: Int = AudioManager.STREAM_MUSIC): Boolean {
        val flags = if (showUi) AudioManager.FLAG_SHOW_UI else 0
        val clampedLevel = level.coerceIn(getMinVolume(streamType), getMaxVolume(streamType))

        try {
            audioManager.setStreamVolume(streamType, clampedLevel, flags)
        } catch (e: Exception) {
            Log.e("VolumeManager", "setStreamVolume failed for stream $streamType", e)
        }

        var current = getVolume(streamType)
        if (current != clampedLevel) {
            // Android 13 / OEM fallback: step via adjustStreamVolume towards clampedLevel
            val direction = if (clampedLevel > current) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            val stepsNeeded = Math.abs(clampedLevel - current)
            for (i in 0 until stepsNeeded) {
                try {
                    audioManager.adjustStreamVolume(streamType, direction, flags)
                } catch (e: Exception) {
                    Log.e("VolumeManager", "adjustStreamVolume fallback step failed for stream $streamType", e)
                    break
                }
            }
            current = getVolume(streamType)
        }
        return current == clampedLevel
    }

    // --- Dedicated Voice Call Volume APIs ---

    /**
     * Returns current in-call / voice call volume level (STREAM_VOICE_CALL).
     */
    fun getCallVolume(): Int = getVolume(AudioManager.STREAM_VOICE_CALL)

    /**
     * Returns maximum in-call / voice call volume level.
     */
    fun getMaxCallVolume(): Int = getMaxVolume(AudioManager.STREAM_VOICE_CALL)

    /**
     * Returns minimum in-call / voice call volume level.
     */
    fun getMinCallVolume(): Int = getMinVolume(AudioManager.STREAM_VOICE_CALL)

    /**
     * Sets in-call / voice call volume without popping up system volume UI.
     */
    fun setCallVolume(level: Int, showUi: Boolean = false): Boolean =
        setVolume(level, showUi, AudioManager.STREAM_VOICE_CALL)

    /**
     * Step call volume UP by 1 step.
     */
    fun callVolumeUp(showUi: Boolean = false) {
        volumeUp(showUi = showUi, streamType = AudioManager.STREAM_VOICE_CALL)
    }

    /**
     * Step call volume DOWN by 1 step.
     */
    fun callVolumeDown(showUi: Boolean = false) {
        volumeDown(showUi = showUi, streamType = AudioManager.STREAM_VOICE_CALL)
    }

    /**
     * Returns call volume as percentage (0 to 100).
     */
    fun getCallVolumePercentage(): Int {
        val max = getMaxCallVolume()
        val min = getMinCallVolume()
        val range = max - min
        if (range <= 0) return 100
        val current = getCallVolume().coerceIn(min, max)
        return (((current - min).toFloat() / range.toFloat()) * 100).toInt().coerceIn(0, 100)
    }

    // --- Media Mute & Percentage Helpers ---

    /**
     * Returns whether the media stream is currently muted or at minimum level.
     */
    fun isMuted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                audioManager.isStreamMute(AudioManager.STREAM_MUSIC) || getVolume() == 0
            } catch (e: Exception) {
                getVolume() == 0
            }
        } else {
            getVolume() == 0
        }
    }

    /**
     * Toggles mute on media volume.
     */
    fun toggleMute(showUi: Boolean = false) {
        val flags = if (showUi) AudioManager.FLAG_SHOW_UI else 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val direction = if (isMuted()) AudioManager.ADJUST_UNMUTE else AudioManager.ADJUST_MUTE
            try {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, flags)
            } catch (e: Exception) {
                Log.e("VolumeManager", "toggleMute failed", e)
            }
        } else {
            if (getVolume() > 0) {
                setVolume(0, showUi = showUi)
            } else {
                setVolume(1, showUi = showUi)
            }
        }
    }

    /**
     * Returns media volume as a percentage integer (0 to 100).
     */
    fun getVolumePercentage(): Int {
        val max = getMaxVolume()
        if (max <= 0) return 0
        val current = getVolume()
        return ((current.toFloat() / max.toFloat()) * 100).toInt().coerceIn(0, 100)
    }
}
