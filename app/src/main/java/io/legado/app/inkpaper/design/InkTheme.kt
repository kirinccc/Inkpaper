package io.legado.app.inkpaper.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle

data class InkColors(
    val bg: Color,
    val surface: Color,
    val surfaceSubtle: Color,
    val ink: Color,
    val inkSecondary: Color,
    val inkTertiary: Color,
    val line: Color,
    val chip: Color,
    val inkOnSolid: Color,
    val isDark: Boolean,
)

val PaperColors = InkColors(
    bg = Color(0xFFF7F6F3),
    surface = Color(0xFFFFFFFF),
    surfaceSubtle = Color(0xFFF1EFE9),
    ink = Color(0xFF1B1B18),
    inkSecondary = Color(0xFF8E8C84),
    inkTertiary = Color(0xFFB4B1A8),
    line = Color(0xFFE8E6E0),
    chip = Color(0xFFEFEDE8),
    inkOnSolid = Color(0xFFF7F6F3),
    isDark = false,
)

val MistColors = InkColors(
    bg = Color(0xFFF4F4F2),
    surface = Color(0xFFFFFFFF),
    surfaceSubtle = Color(0xFFECECE8),
    ink = Color(0xFF161616),
    inkSecondary = Color(0xFF7A7A75),
    inkTertiary = Color(0xFFA8A8A2),
    line = Color(0xFFE4E4E0),
    chip = Color(0xFFEAEAE5),
    inkOnSolid = Color(0xFFF4F4F2),
    isDark = false,
)

val InkNightColors = InkColors(
    bg = Color(0xFF141413),
    surface = Color(0xFF1E1E1C),
    surfaceSubtle = Color(0xFF191917),
    ink = Color(0xFFDCDAD3),
    inkSecondary = Color(0xFF7C7B74),
    inkTertiary = Color(0xFF585751),
    line = Color(0xFF2C2C28),
    chip = Color(0xFF252522),
    inkOnSolid = Color(0xFF141413),
    isDark = true,
)

val AmoledColors = InkColors(
    bg = Color(0xFF000000),
    surface = Color(0xFF141414),
    surfaceSubtle = Color(0xFF0D0D0D),
    ink = Color(0xFFD4D2CB),
    inkSecondary = Color(0xFF706F69),
    inkTertiary = Color(0xFF4D4C47),
    line = Color(0xFF262626),
    chip = Color(0xFF1C1C1C),
    inkOnSolid = Color(0xFF000000),
    isDark = true,
)

fun InkThemePreset.toColors(): InkColors = when (this) {
    InkThemePreset.PAPER -> PaperColors
    InkThemePreset.MIST -> MistColors
    InkThemePreset.INK_NIGHT -> InkNightColors
    InkThemePreset.AMOLED -> AmoledColors
}

val LocalInkColors = staticCompositionLocalOf { PaperColors }
val LocalInkSerifFont = staticCompositionLocalOf<FontFamily> { FontFamily.Serif }

object InkTheme {
    val colors: InkColors
        @Composable get() = LocalInkColors.current

    val serifFont: FontFamily
        @Composable get() = LocalInkSerifFont.current
}

@Composable
fun rememberWenKaiFontFamily(): FontFamily {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            FontFamily(Font("font/LXGWWenKai-Regular.ttf", context.assets))
        }.getOrElse {
            FontFamily.Serif
        }
    }
}

@Composable
fun InkPaperTheme(
    content: @Composable () -> Unit,
) {
    val prefs by InkThemeController.prefs.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val activePreset = if (prefs.followSystem) {
        if (systemDark) prefs.darkPreset else InkThemePreset.PAPER
    } else {
        prefs.selectedPreset
    }
    val colors = activePreset.toColors()
    val serifFont = rememberWenKaiFontFamily()

    val materialScheme = if (colors.isDark) {
        darkColorScheme(
            primary = colors.ink,
            onPrimary = colors.inkOnSolid,
            secondary = colors.inkSecondary,
            onSecondary = colors.inkOnSolid,
            background = colors.bg,
            onBackground = colors.ink,
            surface = colors.surface,
            onSurface = colors.ink,
            surfaceVariant = colors.chip,
            onSurfaceVariant = colors.inkSecondary,
            outline = colors.line,
            outlineVariant = colors.line,
        )
    } else {
        lightColorScheme(
            primary = colors.ink,
            onPrimary = colors.inkOnSolid,
            secondary = colors.inkSecondary,
            onSecondary = colors.inkOnSolid,
            background = colors.bg,
            onBackground = colors.ink,
            surface = colors.surface,
            onSurface = colors.ink,
            surfaceVariant = colors.chip,
            onSurfaceVariant = colors.inkSecondary,
            outline = colors.line,
            outlineVariant = colors.line,
        )
    }

    CompositionLocalProvider(
        LocalInkColors provides colors,
        LocalInkSerifFont provides serifFont,
    ) {
        MaterialTheme(
            colorScheme = materialScheme,
            content = content,
        )
    }
}
