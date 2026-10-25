package com.aftra.noscroll

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Tuile des réglages rapides : allumée = blocage actif, éteinte = en pause 15 min. */
class PauseTileService : TileService() {

    override fun onStartListening() = refresh()

    override fun onClick() {
        if (Prefs.isPaused(this)) Prefs.resume(this) else Prefs.pause(this)
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        tile.state = if (Prefs.isPaused(this)) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.updateTile()
    }
}
