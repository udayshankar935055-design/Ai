package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.appcheck.configureAppCheck
import com.example.data.local.SettingsDataStore
import com.example.data.local.UserSettings
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.SmartAiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before rendering UI or creating ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            val settingsStore = remember { SettingsDataStore(applicationContext) }
            val userSettings by settingsStore.settings.collectAsStateWithLifecycle(
                initialValue = UserSettings()
            )
            SmartAiTheme(themeMode = userSettings.themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation()
                }
            }
        }
    }
}
