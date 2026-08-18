package com.t2v.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.t2v.app.AppContainer
import com.t2v.ui.navigation.Routes
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.t2v.ui.navigation.LTVNavHost
import com.t2v.ui.theme.LTVTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this@MainActivity)
            LTVApp(windowSizeClass = windowSizeClass)
        }
    }
}

@Composable
fun LTVApp(windowSizeClass: WindowSizeClass? = null) {
    LTVTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val navController = rememberNavController()
            val settings by AppContainer.settings(LocalContext.current).flow.collectAsState(initial = null)
            if (settings != null) {
                val start = if (settings!!.onboardingCompleted) Routes.Editor else Routes.Onboarding
                LTVNavHost(navController, start, windowSizeClass = windowSizeClass)
            }
        }
    }
}
