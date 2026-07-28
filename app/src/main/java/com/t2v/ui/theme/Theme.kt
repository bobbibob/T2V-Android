package com.t2v.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Фирменные цвета — единые с десктопом AndromedaNova. */
object LTVColors {
    val Seed = Color(0xFF5C2D91)
    val SeedLight = Color(0xFF8B5DD8)
    val OnSeed = Color(0xFFFFFFFF)
    val Accent = Color(0xFFFFB300)
    val Surface = Color(0xFFFAF9FB)
    val SurfaceDark = Color(0xFF15131A)
    val OnSurface = Color(0xFF1A1820)
    val OnSurfaceDark = Color(0xFFEDEAF2)
    val Error = Color(0xFFB3261E)

    // Подсветка LTV-разметки
    val PauseColor = Color(0xFF92400E)
    val PauseBg = Color(0xFFFFF7ED)
    val VoiceColor = Color(0xFF6D28D9)
    val VoiceBg = Color(0xFFF5F3FF)
    val LangColor = Color(0xFF0E7490)
    val LangBg = Color(0xFFECFEFF)
    val SpeedColor = Color(0xFF166534)
    val SpeedBg = Color(0xFFF0FDF4)
    val VolumeColor = Color(0xFF9F1239)
    val VolumeBg = Color(0xFFFFF1F2)
    val ChapterColor = Color(0xFF1E40AF)
    val ChapterBg = Color(0xFFEFF6FF)
}

private val LightColors = lightColorScheme(
    primary = LTVColors.Seed,
    onPrimary = LTVColors.OnSeed,
    primaryContainer = LTVColors.SeedLight,
    onPrimaryContainer = LTVColors.OnSeed,
    secondary = LTVColors.Accent,
    onSecondary = LTVColors.OnSurface,
    background = LTVColors.Surface,
    onBackground = LTVColors.OnSurface,
    surface = Color.White,
    onSurface = LTVColors.OnSurface,
    error = LTVColors.Error,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = LTVColors.SeedLight,
    onPrimary = LTVColors.OnSeed,
    primaryContainer = LTVColors.Seed,
    onPrimaryContainer = LTVColors.OnSeed,
    secondary = LTVColors.Accent,
    onSecondary = LTVColors.OnSurface,
    background = LTVColors.SurfaceDark,
    onBackground = LTVColors.OnSurfaceDark,
    surface = Color(0xFF1F1C28),
    onSurface = LTVColors.OnSurfaceDark,
    error = LTVColors.Error,
    onError = Color.White,
)

private val LtvTypography = Typography(
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun LTVTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = LtvTypography, content = content)
}
