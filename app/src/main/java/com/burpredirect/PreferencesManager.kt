package com.burpredirect

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {

    companion object {
        private val KEY_IP = stringPreferencesKey("burp_ip")
        private val KEY_PORT = intPreferencesKey("burp_port")
        const val DEFAULT_IP = "192.168.1.100"
        const val DEFAULT_PORT = 8080
    }

    val ip: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_IP] ?: DEFAULT_IP
    }

    val port: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_PORT] ?: DEFAULT_PORT
    }

    suspend fun saveSettings(ip: String, port: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_IP] = ip
            prefs[KEY_PORT] = port
        }
    }
}
