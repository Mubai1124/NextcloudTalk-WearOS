package com.example.wearmaterial

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.wearmaterial.navigation.AppNavigation
import com.example.wearmaterial.theme.WearMaterialTheme
import com.example.wearmaterial.ui.theme.AppColors
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class MainActivity : ComponentActivity() {
    
    companion object {
        private val THEME_KEY = stringPreferencesKey("theme_name")
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            // 从 DataStore 读取主题
            val themeName by dataStore.data
                .map { it[THEME_KEY] ?: "Nextcloud" }
                .collectAsStateWithLifecycle(initialValue = "Nextcloud")
            
            val colorScheme = AppColors.getByName(themeName)
            
            // 用于保存主题的 scope
            val scope = rememberCoroutineScope()
            
            WearMaterialTheme(colorScheme = colorScheme) {
                AppNavigation(
                    currentTheme = themeName,
                    onThemeChange = { newTheme ->
                        // 保存主题到 DataStore
                        scope.launch {
                            dataStore.edit { it[THEME_KEY] = newTheme }
                        }
                    }
                )
            }
        }
    }
}
