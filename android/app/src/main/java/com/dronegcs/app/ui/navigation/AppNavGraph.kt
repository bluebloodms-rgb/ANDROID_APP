package com.dronegcs.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dronegcs.app.ui.screens.MainScreen
import com.dronegcs.app.ui.screens.OnboardingScreen
import com.dronegcs.app.ui.screens.Phase1TestScreen
import com.dronegcs.app.ui.screens.SettingsScreen
import com.dronegcs.app.viewmodel.CameraViewModel
import com.dronegcs.app.viewmodel.ConnectionViewModel
import com.dronegcs.app.viewmodel.SettingsViewModel
import com.dronegcs.app.viewmodel.TelemetryViewModel

/**
 * Navigation graph for the app
 */
@Composable
fun AppNavHost(
    startDestination: String = NavigationDestinations.ONBOARDING,
    settingsViewModel: SettingsViewModel
) {
    val navController = rememberNavController()
    // Activity-scoped so the video source selection made in Settings is shared with MainScreen.
    val cameraViewModel = hiltViewModel<CameraViewModel>()
    androidx.navigation.compose.NavHost(navController, startDestination) {
        composable(NavigationDestinations.ONBOARDING) {
            OnboardingScreen(
                settingsViewModel = settingsViewModel,
                onComplete = { navController.navigate(NavigationDestinations.MAIN) { popUpTo(NavigationDestinations.ONBOARDING) { inclusive = true } } }
            )
        }
        composable(NavigationDestinations.MAIN) {
            MainScreen(
                connectionViewModel = hiltViewModel(),
                telemetryViewModel = hiltViewModel(),
                cameraViewModel = cameraViewModel,
                onOpenSettings = { navController.navigate(NavigationDestinations.SETTINGS) }
            )
        }
        composable(NavigationDestinations.SETTINGS) {
            SettingsScreen(
                settingsViewModel = hiltViewModel(),
                connectionViewModel = hiltViewModel(),
                cameraViewModel = cameraViewModel
            )
        }
        composable(NavigationDestinations.PHASE1_TEST) {
            Phase1TestScreen(
                connectionViewModel = hiltViewModel(),
                telemetryViewModel = hiltViewModel()
            )
        }
    }
}

/**
 * Navigation destinations
 */
object NavigationDestinations {
    const val ONBOARDING = "onboarding"
    const val MAIN = "main"
    const val SETTINGS = "settings"
    const val PHASE1_TEST = "phase1test"
}