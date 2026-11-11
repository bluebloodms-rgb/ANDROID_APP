package com.dronegcs.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dronegcs.app.ui.navigation.AppNavHost
import com.dronegcs.app.ui.navigation.NavigationDestinations
import com.dronegcs.app.ui.theme.DroneGCSTheme
import com.dronegcs.app.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DroneGCSTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val settingsViewModel = viewModel<SettingsViewModel>()
                    // Tri-state: null while DataStore loads. Hold on the (empty)
                    // splash surface until known, so the welcome screen only ever
                    // appears on the genuine first run — not on every launch.
                    val isFirstRun by settingsViewModel.isFirstRun.collectAsStateWithLifecycle()

                    if (isFirstRun != null) {
                        AppNavHost(
                            startDestination = if (isFirstRun == true) {
                                NavigationDestinations.ONBOARDING
                            } else {
                                NavigationDestinations.MAIN
                            },
                            settingsViewModel = settingsViewModel
                        )
                    }
                }
            }
        }
    }
}
