package com.burpredirect

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
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
    private var proxyService: ProxyService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            proxyService = (service as ProxyService.LocalBinder).getService()
            bound = true
            updateTileState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            proxyService = null
            bound = false
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        bindService(Intent(this, ProxyService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStopListening() {
        super.onStopListening()
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }

    override fun onClick() {
        super.onClick()
        val service = proxyService ?: return

        scope.launch {
            if (service.isActive.value) {
                service.disableProxy { updateTileState() }
            } else {
                val prefs = PreferencesManager(applicationContext)
                val ip = prefs.ip.first()
                val port = prefs.port.first()
                service.enableProxy(ip, port) { updateTileState() }
            }
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isActive = proxyService?.isActive?.value ?: false

        tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (isActive) "Proxy ON" else "Proxy OFF"
        tile.updateTile()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
