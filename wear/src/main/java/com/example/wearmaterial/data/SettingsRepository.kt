package com.example.wearmaterial.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*

private val Context.dataStore by preferencesDataStore("talk_settings")

class SettingsRepository(private val context: Context) {
    
    companion object {
        val SERVER_URL = stringPreferencesKey("server_url")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
    }
    
    // 保存登录信息
    suspend fun saveLogin(serverUrl: String, username: String, password: String) {
        context.dataStore.edit { prefs ->
            prefs[SERVER_URL] = serverUrl
            prefs[USERNAME] = username
            prefs[PASSWORD] = password
            prefs[IS_LOGGED_IN] = true
        }
    }
    
    // 获取登录状态
    val isLoggedIn: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[IS_LOGGED_IN] ?: false }
    
    // 获取服务器URL
    val serverUrl: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[SERVER_URL] }
    
    // 获取凭证
    val credentials: Flow<Pair<String, String>?> = context.dataStore.data
        .map { prefs ->
            val user = prefs[USERNAME]
            val pass = prefs[PASSWORD]
            if (user != null && pass != null) user to pass else null
        }
    
    // 清除登录信息
    suspend fun clearLogin() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
    
    // 通知设置
    val notificationsEnabled: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[NOTIFICATIONS_ENABLED] ?: true }
    
    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[NOTIFICATIONS_ENABLED] = enabled
        }
    }
    
    // 振动设置
    val vibrationEnabled: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[VIBRATION_ENABLED] ?: true }
    
    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[VIBRATION_ENABLED] = enabled
        }
    }
}
