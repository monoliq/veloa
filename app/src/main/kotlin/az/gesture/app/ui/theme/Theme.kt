package az.gesture.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import az.gesture.app.data.AppColorTheme
import az.gesture.app.data.AppThemeMode

val LocalIsAmoled = staticCompositionLocalOf { false }

private data class AccentPalette(
    val lightPrimary: Color,
    val lightOnPrimary: Color,
    val lightPrimaryContainer: Color,
    val lightOnPrimaryContainer: Color,
    val lightSecondary: Color,
    val lightSecondaryContainer: Color,
    val lightOnSecondaryContainer: Color,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkPrimaryContainer: Color,
    val darkOnPrimaryContainer: Color,
    val darkSecondary: Color,
    val darkSecondaryContainer: Color,
    val darkOnSecondaryContainer: Color
)

private fun paletteFor(colorTheme: AppColorTheme): AccentPalette = when (colorTheme) {
    AppColorTheme.OCEAN -> AccentPalette(
        lightPrimary = Color(0xFF0284C7),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFE0F2FE),
        lightOnPrimaryContainer = Color(0xFF0C4A6E),
        lightSecondary = Color(0xFF0EA5E9),
        lightSecondaryContainer = Color(0xFFEFF6FF),
        lightOnSecondaryContainer = Color(0xFF1E3A5F),
        darkPrimary = Color(0xFF38BDF8),
        darkOnPrimary = Color(0xFF052436),
        darkPrimaryContainer = Color(0xFF10354B),
        darkOnPrimaryContainer = Color(0xFFBAE6FD),
        darkSecondary = Color(0xFF7DD3FC),
        darkSecondaryContainer = Color(0xFF132C3E),
        darkOnSecondaryContainer = Color(0xFFE0F2FE)
    )
    AppColorTheme.EMERALD -> AccentPalette(
        lightPrimary = Color(0xFF0D9488),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFCCFBF1),
        lightOnPrimaryContainer = Color(0xFF042F2E),
        lightSecondary = Color(0xFF14B8A6),
        lightSecondaryContainer = Color(0xFFE6FFFA),
        lightOnSecondaryContainer = Color(0xFF0F3D3A),
        darkPrimary = Color(0xFF2DD4BF),
        darkOnPrimary = Color(0xFF032624),
        darkPrimaryContainer = Color(0xFF103B38),
        darkOnPrimaryContainer = Color(0xFF99F6E4),
        darkSecondary = Color(0xFF5EEAD4),
        darkSecondaryContainer = Color(0xFF12302E),
        darkOnSecondaryContainer = Color(0xFFCCFBF1)
    )
    AppColorTheme.VIOLET -> AccentPalette(
        lightPrimary = Color(0xFF7C3AED),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFEDE9FE),
        lightOnPrimaryContainer = Color(0xFF3B0764),
        lightSecondary = Color(0xFF8B5CF6),
        lightSecondaryContainer = Color(0xFFF3E8FF),
        lightOnSecondaryContainer = Color(0xFF4C1D95),
        darkPrimary = Color(0xFFA78BFA),
        darkOnPrimary = Color(0xFF220E47),
        darkPrimaryContainer = Color(0xFF2D204D),
        darkOnPrimaryContainer = Color(0xFFDDD6FE),
        darkSecondary = Color(0xFFC4B5FD),
        darkSecondaryContainer = Color(0xFF231A3D),
        darkOnSecondaryContainer = Color(0xFFEDE9FE)
    )
    AppColorTheme.CORAL -> AccentPalette(
        lightPrimary = Color(0xFFE11D48),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFFFE4E6),
        lightOnPrimaryContainer = Color(0xFF4C0519),
        lightSecondary = Color(0xFFF43F5E),
        lightSecondaryContainer = Color(0xFFFFF1F2),
        lightOnSecondaryContainer = Color(0xFF881337),
        darkPrimary = Color(0xFFFB7185),
        darkOnPrimary = Color(0xFF3D0613),
        darkPrimaryContainer = Color(0xFF4A1825),
        darkOnPrimaryContainer = Color(0xFFFECDD3),
        darkSecondary = Color(0xFFFDA4AF),
        darkSecondaryContainer = Color(0xFF381620),
        darkOnSecondaryContainer = Color(0xFFFFE4E6)
    )
    AppColorTheme.AMBER -> AccentPalette(
        lightPrimary = Color(0xFFD97706),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFFEF3C7),
        lightOnPrimaryContainer = Color(0xFF451A03),
        lightSecondary = Color(0xFFF59E0B),
        lightSecondaryContainer = Color(0xFFFFFBEB),
        lightOnSecondaryContainer = Color(0xFF78350F),
        darkPrimary = Color(0xFFFBBF24),
        darkOnPrimary = Color(0xFF331802),
        darkPrimaryContainer = Color(0xFF422C0A),
        darkOnPrimaryContainer = Color(0xFFFDE68A),
        darkSecondary = Color(0xFFFCD34D),
        darkSecondaryContainer = Color(0xFF33230B),
        darkOnSecondaryContainer = Color(0xFFFEF3C7)
    )
    AppColorTheme.MONOCHROME -> AccentPalette(
        lightPrimary = Color(0xFF18181B),
        lightOnPrimary = Color.White,
        lightPrimaryContainer = Color(0xFFE4E4E7),
        lightOnPrimaryContainer = Color(0xFF09090B),
        lightSecondary = Color(0xFF52525B),
        lightSecondaryContainer = Color(0xFFF4F4F5),
        lightOnSecondaryContainer = Color(0xFF27272A),
        darkPrimary = Color(0xFFE4E4E7),
        darkOnPrimary = Color(0xFF09090B),
        darkPrimaryContainer = Color(0xFF27272A),
        darkOnPrimaryContainer = Color(0xFFFAFAFA),
        darkSecondary = Color(0xFFA1A1AA),
        darkSecondaryContainer = Color(0xFF1E1E22),
        darkOnSecondaryContainer = Color(0xFFE4E4E7)
    )
}

private fun buildColorScheme(
    themeMode: AppThemeMode,
    colorTheme: AppColorTheme
): ColorScheme {
    val p = paletteFor(colorTheme)
    return when (themeMode) {
        AppThemeMode.LIGHT -> lightColorScheme(
            primary = p.lightPrimary,
            onPrimary = p.lightOnPrimary,
            primaryContainer = p.lightPrimaryContainer,
            onPrimaryContainer = p.lightOnPrimaryContainer,
            secondary = p.lightSecondary,
            onSecondary = Color.White,
            secondaryContainer = p.lightSecondaryContainer,
            onSecondaryContainer = p.lightOnSecondaryContainer,
            tertiary = p.lightPrimary,
            background = Color(0xFFF6F7F9),
            onBackground = Color(0xFF111318),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF111318),
            surfaceVariant = Color(0xFFEDF0F5),
            onSurfaceVariant = Color(0xFF525A69),
            surfaceContainerLow = Color(0xFFF2F4F8),
            surfaceContainer = Color(0xFFECEFF4),
            surfaceContainerHigh = Color(0xFFE5E9F0),
            outline = Color(0xFF9AA3B2),
            outlineVariant = Color(0xFFE0E5EE)
        )
        AppThemeMode.DARK -> darkColorScheme(
            primary = p.darkPrimary,
            onPrimary = p.darkOnPrimary,
            primaryContainer = p.darkPrimaryContainer,
            onPrimaryContainer = p.darkOnPrimaryContainer,
            secondary = p.darkSecondary,
            onSecondary = p.darkOnPrimary,
            secondaryContainer = p.darkSecondaryContainer,
            onSecondaryContainer = p.darkOnSecondaryContainer,
            tertiary = p.darkPrimary,
            background = Color(0xFF0C0E12),
            onBackground = Color(0xFFF1F5F9),
            surface = Color(0xFF14171E),
            onSurface = Color(0xFFF1F5F9),
            surfaceVariant = Color(0xFF1C2029),
            onSurfaceVariant = Color(0xFF94A3B8),
            surfaceContainerLow = Color(0xFF11141A),
            surfaceContainer = Color(0xFF161A22),
            surfaceContainerHigh = Color(0xFF212632),
            outline = Color(0xFF3B4354),
            outlineVariant = Color(0xFF232834)
        )
        AppThemeMode.AMOLED -> darkColorScheme(
            primary = p.darkPrimary,
            onPrimary = p.darkOnPrimary,
            primaryContainer = p.darkPrimaryContainer,
            onPrimaryContainer = p.darkOnPrimaryContainer,
            secondary = p.darkSecondary,
            onSecondary = p.darkOnPrimary,
            secondaryContainer = p.darkSecondaryContainer,
            onSecondaryContainer = p.darkOnSecondaryContainer,
            tertiary = p.darkPrimary,
            background = Color(0xFF000000),
            onBackground = Color(0xFFF8FAFC),
            surface = Color(0xFF090B0E),
            onSurface = Color(0xFFF8FAFC),
            surfaceVariant = Color(0xFF12161C),
            onSurfaceVariant = Color(0xFF94A3B8),
            surfaceContainerLow = Color(0xFF06080A),
            surfaceContainer = Color(0xFF0C0F14),
            surfaceContainerHigh = Color(0xFF181D25),
            outline = Color(0xFF475569),
            outlineVariant = Color(0xFF1D222B)
        )
    }
}

@Composable
fun GestureTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    colorTheme: AppColorTheme = AppColorTheme.OCEAN,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = remember(themeMode, colorTheme) {
        buildColorScheme(themeMode, colorTheme)
    }

    CompositionLocalProvider(LocalIsAmoled provides (themeMode == AppThemeMode.AMOLED)) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
