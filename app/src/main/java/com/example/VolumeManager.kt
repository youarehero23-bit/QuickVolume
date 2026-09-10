package com.example

import android.content.Context
import android.media.AudioManager
import android.os.Build

/**
 * Helper manager for system media volume controls.
 * Uses AudioManager to control STREAM_MUSIC with native UI feedback.
 */
class VolumeManager(private val context: Context) {

    private val audioManager: AudioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Increase system media volume by 1 step.
     * Displays Android's native volume slider (FLAG_SHOW_UI).
     */
    fun volumeUp() {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_RAISE,
            AudioManager.FLAG_SHOW_UI
        )
    }

    /**
     * Decrease system media volume by 1 step.
     * Displays Android's native volume slider (FLAG_SHOW_UI).
     */
    fun volumeDown() {
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )
    }

    /**
     * Returns the current volume level of STREAM_MUSIC.
     */
    fun getVolume(): Int {
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    /**
     * Returns the maximum possible volume level of STREAM_MUSIC.
     */
    fun getMaxVolume(): Int {
        return audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    }

    /**
     * Returns the minimum possible volume level of STREAM_MUSIC (0 or API 28+ min volume).
     */
    fun getMinVolume(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        } else {
            0
        }
    }

    /**
     * Sets the media stream volume directly to a specific level.
     */
    fun setVolume(level: Int, showUi: Boolean = true) {
        val flags = if (showUi) AudioManager.FLAG_SHOW_UI else 0
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, level, flags)
    }

    /**
     * Returns whether the media stream is currently muted or at minimum level.
     */
    fun isMuted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.isStreamMute(AudioManager.STREAM_MUSIC) || getVolume() == 0
        } else {
            getVolume() == 0
        }
    }

    /**
     * Toggles mute on media volume.
     */
    fun toggleMute() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val direction = if (isMuted()) AudioManager.ADJUST_UNMUTE else AudioManager.ADJUST_MUTE
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        } else {
            if (getVolume() > 0) {
                setVolume(0, showUi = true)
            } else {
                volumeUp()
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
