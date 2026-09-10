package com.example

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Helper manager for system media volume controls.
 * Uses AudioManager to control STREAM_MUSIC reliably across Android versions (including Android 13+).
 */
class VolumeManager(private val context: Context) {

    private val audioManager: AudioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Increase system media volume by 1 step.
     */
    fun volumeUp(showUi: Boolean = true) {
        val flags = if (showUi) AudioManager.FLAG_SHOW_UI else 0
        try {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_RAISE,
                flags
            )
        } catch (e: Exception) {
            Log.e("VolumeManager", "volumeUp failed", e)
        }
    }

    /**
     * Decrease system media volume by 1 step.
     */
    fun volumeDown(showUi: Boolean = true) {
        val flags = if (showUi) AudioManager.FLAG_SHOW_UI else 0
        try {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_LOWER,
                flags
            )
        } catch (e: Exception) {
            Log.e("VolumeManager", "volumeDown failed", e)
        }
    }

    /**
     * Returns the current volume level of STREAM_MUSIC.
     */
    fun getVolume(): Int {
        return try {
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        } catch (e: Exception) {
            Log.e("VolumeManager", "getVolume failed", e)
            0
        }
    }

    /**
     * Returns the maximum possible volume level of STREAM_MUSIC.
     */
    fun getMaxVolume(): Int {
        return try {
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        } catch (e: Exception) {
            Log.e("VolumeManager", "getMaxVolume failed", e)
            15
        }
    }

    /**
     * Returns the minimum possible volume level of STREAM_MUSIC (0 or API 28+ min volume).
     */
    fun getMinVolume(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
            } catch (e: Exception) {
                0
            }
        } else {
            0
        }
    }

    /**
     * Sets the media stream volume directly to a specific level.
     * Incorporates a robust dual-path strategy:
     * 1. Direct setStreamVolume
     * 2. If an OEM implementation or Android 13 restriction prevents direct setStreamVolume with flags=0,
     *    automatically steps via adjustStreamVolume to reach the target volume.
     */
    fun setVolume(level: Int, showUi: Boolean = false): Boolean {
        val flags = if (showUi) AudioManager.FLAG_SHOW_UI else 0
        val clampedLevel = level.coerceIn(getMinVolume(), getMaxVolume())

        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, clampedLevel, flags)
        } catch (e: Exception) {
            Log.e("VolumeManager", "setStreamVolume failed", e)
        }

        var current = getVolume()
        if (current != clampedLevel) {
            // Android 13 / OEM fallback: step via adjustStreamVolume towards clampedLevel
            val direction = if (clampedLevel > current) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            val stepsNeeded = Math.abs(clampedLevel - current)
            for (i in 0 until stepsNeeded) {
                try {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, flags)
                } catch (e: Exception) {
                    Log.e("VolumeManager", "adjustStreamVolume fallback step failed", e)
                    break
                }
            }
            current = getVolume()
        }
        return current == clampedLevel
    }

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
