package com.ostimate.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ostimate.app.ui.home.HomeScreen
import com.ostimate.app.ui.theme.OstimateTheme
import kotlinx.serialization.Serializable

@Serializable
object HomeDestination

@Composable
fun App() {
    OstimateTheme {
        Surface(Modifier.fillMaxSize()) {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = HomeDestination) {
                composable<HomeDestination> {
                    HomeScreen()
                }
            }
        }
    }
}
