package com.marc.albumartlock

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick settings tile ("Album art") to switch the feature on and off from the notification
 * panel, including from the lock screen. Turning it off puts the normal lock screen back right away.
 */
class ToggleTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        update()
    }

    override fun onClick() {
        super.onClick()
        val prefs = Prefs(this)
        prefs.enabled = !prefs.enabled
        MediaArtListener.instance?.refresh(forceRedraw = prefs.enabled)
        update()
    }

    private fun update() {
        val tile = qsTile ?: return
        val on = Prefs(this).enabled
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Album art"
        tile.subtitle = if (on) "On" else "Off"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile)
        tile.updateTile()
    }
}
