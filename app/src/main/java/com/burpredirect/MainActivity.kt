package com.burpredirect

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var proxyService: ProxyService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            proxyService = (service as ProxyService.LocalBinder).getService()
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            proxyService = null
            bound = false
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ProxyScreen(
                        bindService = {
                            bindService(Intent(this, ProxyService::class.java), connection, Context.BIND_AUTO_CREATE)
                        },
                        unbindService = {
                            if (bound) {
                                unbindService(connection)
                                bound = false
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, ProxyService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }
}

@Composable
fun ProxyScreen(
    bindService: () -> Unit,
    unbindService: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    val scope = rememberCoroutineScope()

    val savedIp by prefs.ip.collectAsState(initial = PreferencesManager.DEFAULT_IP)
    val savedPort by prefs.port.collectAsState(initial = PreferencesManager.DEFAULT_PORT)

    var ip by remember(savedIp) { mutableStateOf(savedIp) }
    var port by remember(savedPort) { mutableStateOf(savedPort.toString()) }
    var isActive by remember { mutableStateOf<Boolean?>(null) }
    var status by remember { mutableStateOf("Checking...") }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        bindService()
        scope.launch {
            val active = IptablesManager.isProxyActive()
            isActive = active
            status = if (active) "Active" else "Inactive"
        }
        onDispose { unbindService() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "BurpRedirect",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = ip,
            onValueChange = { ip = it },
            label = { Text("Burp Suite IP") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = isActive == false
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = port,
            onValueChange = { port = it.filter { c -> c.isDigit() } },
            label = { Text("Port") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = isActive == false
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isActive == true) Color(0xFF4CAF50) else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isActive == true) "Proxy Enabled" else "Proxy Disabled",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isActive == true) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Switch(
                    checked = isActive == true,
                    enabled = isActive != null,
                    onCheckedChange = { enabled ->
                        val portInt = port.toIntOrNull() ?: 8080
                        error = null

                        if (enabled) {
                            status = "Enabling..."
                            scope.launch {
                                prefs.saveSettings(ip, portInt)
                            }
                            ProxyService.start(context, ip, portInt)
                            scope.launch {
                                kotlinx.coroutines.delay(500)
                                val active = IptablesManager.isProxyActive()
                                isActive = active
                                status = if (active) "Active" else "Error"
                            }
                        } else {
                            status = "Disabling..."
                            ProxyService.stop(context)
                            scope.launch {
                                kotlinx.coroutines.delay(500)
                                val active = IptablesManager.isProxyActive()
                                isActive = active
                                status = if (!active) "Inactive" else "Error"
                            }
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Status: $status",
            style = MaterialTheme.typography.bodyLarge
        )

        if (isActive == true) {
            Text(
                text = "Redirecting 80, 443 → $ip:$port",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        error?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
