package com.burpredirect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.DataOutputStream

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            // Clear any stale iptables DNAT rules so proxy is always OFF after reboot
            Thread {
                try {
                    val process = Runtime.getRuntime().exec("su")
                    DataOutputStream(process.outputStream).use { os ->
                        os.writeBytes("iptables -t nat -F OUTPUT\n")
                        os.writeBytes("exit\n")
                        os.flush()
                    }
                    process.waitFor()
                } catch (_: Exception) { }
            }.start()
        }
    }
}
