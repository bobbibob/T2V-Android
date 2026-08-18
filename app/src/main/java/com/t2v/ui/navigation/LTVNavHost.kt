package com.t2v.ui.navigation

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.t2v.ui.screens.editor.EditorScreen
import com.t2v.ui.screens.generation.GenerationScreen
import com.t2v.ui.screens.music.MusicMixScreen
import com.t2v.ui.screens.projects.ProjectsScreen
import com.t2v.ui.screens.review.ReviewScreen
import com.t2v.ui.screens.settings.SettingsScreen
import com.t2v.ui.screens.voices.VoicesScreen
import com.t2v.ui.screens.models.ModelsScreen
import com.t2v.ui.screens.onboarding.OnboardingScreen
import com.t2v.ui.screens.audioeditor.AudioEditorScreen

object Routes {
    const val Onboarding = "onboarding"
    const val Editor = "editor"
    const val Projects = "projects"
    const val Voices = "voices"
    const val Settings = "settings"
    const val Models = "models"
    const val Generation = "generation/{projectId}"
    const val Review = "review/{audiobookId}"
    const val MusicMix = "music/{audiobookId}"
    const val AudioEditor = "audio-editor/{audiobookId}"

    fun generation(projectId: Long) = "generation/$projectId"
    fun review(audiobookId: Long) = "review/$audiobookId"
    fun musicMix(audiobookId: Long) = "music/$audiobookId"
    fun audioEditor(audiobookId: Long) = "audio-editor/$audiobookId"
}

@Composable
fun LTVNavHost(
    nav: NavHostController,
    startDestination: String = Routes.Editor,
    windowSizeClass: WindowSizeClass? = null,
) {
    NavHost(navController = nav, startDestination = startDestination) {
        composable(Routes.Onboarding) { OnboardingScreen(nav) }
        composable(Routes.Editor) { EditorScreen(nav, windowSizeClass = windowSizeClass) }
        composable(Routes.Projects) { ProjectsScreen(nav, windowSizeClass = windowSizeClass) }
        composable(Routes.Generation) { entry ->
            val id = entry.arguments?.getString("projectId")?.toLongOrNull() ?: 0L
            GenerationScreen(nav, id, windowSizeClass = windowSizeClass)
        }
        composable(Routes.Review) { entry ->
            val id = entry.arguments?.getString("audiobookId")?.toLongOrNull() ?: 0L
            ReviewScreen(nav, id, windowSizeClass = windowSizeClass)
        }
        composable(Routes.MusicMix) { entry ->
            val id = entry.arguments?.getString("audiobookId")?.toLongOrNull() ?: 0L
            MusicMixScreen(nav, id, windowSizeClass = windowSizeClass)
        }
        composable(Routes.AudioEditor) { entry ->
            val id = entry.arguments?.getString("audiobookId")?.toLongOrNull() ?: 0L
            AudioEditorScreen(nav, id, windowSizeClass = windowSizeClass)
        }
        composable(Routes.Voices) { VoicesScreen(nav, windowSizeClass = windowSizeClass) }
        composable(Routes.Models) { ModelsScreen(nav, windowSizeClass = windowSizeClass) }
        composable(Routes.Settings) { SettingsScreen(nav, windowSizeClass = windowSizeClass) }
    }
}