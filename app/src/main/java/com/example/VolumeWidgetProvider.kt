package com.example

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * AppWidgetProvider for Home Screen pill-shaped volume controls.
 * Handles direct [+] and [-] button clicks via explicit BroadcastReceivers.
 */
class VolumeWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_VOLUME_UP = "com.example.quickvolume.ACTION_VOLUME_UP"
        const val ACTION_VOLUME_DOWN = "com.example.quickvolume.ACTION_VOLUME_DOWN"

        /**
         * Updates all active QuickVolume home screen widgets with current state.
         */
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, VolumeWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                val provider = VolumeWidgetProvider()
                for (widgetId in appWidgetIds) {
                    provider.updateAppWidget(context, appWidgetManager, widgetId)
                }
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        val volumeManager = VolumeManager(context)

        when (action) {
            ACTION_VOLUME_UP -> {
                volumeManager.volumeUp()
                updateAllWidgets(context)
            }
            ACTION_VOLUME_DOWN -> {
                volumeManager.volumeDown()
                updateAllWidgets(context)
            }
            "android.media.VOLUME_CHANGED_ACTION" -> {
                updateAllWidgets(context)
            }
        }
    }

    fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_volume)
        val volumeManager = VolumeManager(context)

        // PendingIntent for Volume Down Button
        val downIntent = Intent(context, VolumeWidgetProvider::class.java).apply {
            action = ACTION_VOLUME_DOWN
        }
        val downPendingIntent = PendingIntent.getBroadcast(
            context,
            1001,
            downIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_volume_down, downPendingIntent)

        // PendingIntent for Volume Up Button
        val upIntent = Intent(context, VolumeWidgetProvider::class.java).apply {
            action = ACTION_VOLUME_UP
        }
        val upPendingIntent = PendingIntent.getBroadcast(
            context,
            1002,
            upIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_volume_up, upPendingIntent)

        // PendingIntent for Center Container to open the Dashboard
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            context,
            1003,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.center_status_container, mainPendingIntent)

        // Display current volume % in center indicator
        val percent = volumeManager.getVolumePercentage()
        views.setTextViewText(R.id.tv_volume_level, "$percent%")

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
