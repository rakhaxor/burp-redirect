package com.burpredirect

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object IptablesManager {

    suspend fun enableProxy(ip: String, port: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val commands = listOf(
                "iptables -t nat -A OUTPUT -p tcp --dport 80 -j DNAT --to-destination $ip:$port",
                "iptables -t nat -A OUTPUT -p tcp --dport 443 -j DNAT --to-destination $ip:$port"
            )
            executeAsRoot(commands)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun disableProxy(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val commands = listOf("iptables -t nat -F OUTPUT")
            executeAsRoot(commands)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun isProxyActive(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "iptables -t nat -L OUTPUT -n"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            reader.close()
            process.waitFor()
            output.contains("DNAT")
        } catch (e: Exception) {
            false
        }
    }

    private fun executeAsRoot(commands: List<String>) {
        val process = Runtime.getRuntime().exec("su")
        DataOutputStream(process.outputStream).use { os ->
            commands.forEach { cmd ->
                os.writeBytes("$cmd\n")
            }
            os.writeBytes("exit\n")
            os.flush()
        }
        process.waitFor()
        if (process.exitValue() != 0) {
            val error = BufferedReader(InputStreamReader(process.errorStream)).readText()
            throw RuntimeException("Root command failed: $error")
        }
    }
}
