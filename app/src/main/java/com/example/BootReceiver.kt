package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Ensures the QuickVolume Floating Ball persists and automatically starts after device reboots.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val prefs = GesturePreferences(context)
            if (prefs.isFloatingBallEnabled && prefs.isBootStartEnabled) {
                if (Settings.canDrawOverlays(context)) {
                    FloatingVolumeBallService.start(context)
                }
            }
        }
    }
}
