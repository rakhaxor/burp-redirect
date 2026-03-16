package com.burpredirect

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProxyService : Service() {

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive

    private val _currentIp = MutableStateFlow("")
    val currentIp: StateFlow<String> = _currentIp

    private val _currentPort = MutableStateFlow(0)
    val currentPort: StateFlow<Int> = _currentPort

    inner class LocalBinder : Binder() {
        fun getService(): ProxyService = this@ProxyService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        scope.launch {
            _isActive.value = IptablesManager.isProxyActive()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // If the system re-delivers a null intent (MIUI autostart, OOM restart, etc.)
        // or flags indicate a restart, clean up iptables and stop immediately.
        // The proxy should ONLY activate from an explicit user action.
        if (intent == null || (flags and START_FLAG_REDELIVERY) != 0) {
            scope.launch {
                IptablesManager.disableProxy()
                _isActive.value = false
            }
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent.action) {
            ACTION_START -> {
                val ip = intent.getStringExtra(EXTRA_IP) ?: return START_NOT_STICKY
                val port = intent.getIntExtra(EXTRA_PORT, 8080)
                startForeground(NOTIFICATION_ID, createNotification(ip, port))
                scope.launch {
                    val result = IptablesManager.enableProxy(ip, port)
                    if (result.isSuccess) {
                        _isActive.value = true
                        _currentIp.value = ip
                        _currentPort.value = port
                    }
                }
            }
            ACTION_STOP -> {
                scope.launch {
                    IptablesManager.disableProxy()
                    _isActive.value = false
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                // Unknown action — don't enable anything, just stop
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up iptables rules when service is destroyed to prevent
        // stale DNAT rules from persisting and breaking connectivity.
        // Use a daemon thread to avoid ANR from runBlocking on main thread.
        Thread {
            try {
                val process = Runtime.getRuntime().exec("su")
                java.io.DataOutputStream(process.outputStream).use { os ->
                    os.writeBytes("iptables -t nat -F OUTPUT\n")
                    os.writeBytes("exit\n")
                    os.flush()
                }
                process.waitFor()
            } catch (_: Exception) { }
        }.apply { isDaemon = true }.start()
        scope.cancel()
    }

    fun enableProxy(ip: String, port: Int, onResult: (Result<Unit>) -> Unit) {
        scope.launch {
            val result = IptablesManager.enableProxy(ip, port)
            if (result.isSuccess) {
                _isActive.value = true
                _currentIp.value = ip
                _currentPort.value = port
                startForeground(NOTIFICATION_ID, createNotification(ip, port))
            }
            onResult(result)
        }
    }

    fun disableProxy(onResult: (Result<Unit>) -> Unit) {
        scope.launch {
            val result = IptablesManager.disableProxy()
            if (result.isSuccess) {
                _isActive.value = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            onResult(result)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Proxy Status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when proxy redirect is active"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(ip: String, port: Int): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Proxy Active")
            .setContentText("Redirecting 80,443 → $ip:$port")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "proxy_status"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.burpredirect.START"
        const val ACTION_STOP = "com.burpredirect.STOP"
        const val EXTRA_IP = "ip"
        const val EXTRA_PORT = "port"

        fun start(context: Context, ip: String, port: Int) {
            val intent = Intent(context, ProxyService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_IP, ip)
                putExtra(EXTRA_PORT, port)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ProxyService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
