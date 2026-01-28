package com.burpredirect

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ProxyTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onStopListening() {
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()

        scope.launch {
            val isCurrentlyActive = IptablesManager.isProxyActive()
            if (isCurrentlyActive) {
                ProxyService.stop(applicationContext)
            } else {
                val prefs = PreferencesManager(applicationContext)
                val ip = prefs.ip.first()
                val port = prefs.port.first()
                ProxyService.start(applicationContext, ip, port)
            }
            kotlinx.coroutines.delay(500)
            updateTileState()
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        scope.launch {
            val isActive = IptablesManager.isProxyActive()
            tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = if (isActive) "Proxy ON" else "Proxy OFF"
            tile.updateTile()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
