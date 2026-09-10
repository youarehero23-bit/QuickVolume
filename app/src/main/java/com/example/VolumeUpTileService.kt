package com.example

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings Tile for Volume Up.
 * Immediately adjusts media volume up when clicked and shows system volume HUD.
 */
class VolumeUpTileService : TileService() {

    private lateinit var volumeManager: VolumeManager

    override fun onCreate() {
        super.onCreate()
        volumeManager = VolumeManager(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        volumeManager.volumeUp()
        updateTileState()
        // Broadcast update to any active widgets
        VolumeWidgetProvider.updateAllWidgets(applicationContext)
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        tile.label = getString(R.string.tile_volume_up_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_volume_up)
        tile.state = Tile.STATE_ACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val percent = volumeManager.getVolumePercentage()
            tile.subtitle = "$percent%"
        }
        tile.updateTile()
    }
}
