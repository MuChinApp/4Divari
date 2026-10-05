package ir.chardivari.core.designsystem.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import ir.chardivari.core.designsystem.tokens.AppTypography
import ir.chardivari.core.designsystem.tokens.BrandColors
import ir.chardivari.core.designsystem.tokens.NeutralColors

private val LightColors = lightColorScheme(
    primary = BrandColors.Accent,
    onPrimary = BrandColors.OnAccent,
    primaryContainer = BrandColors.AccentMuted,
    onPrimaryContainer = BrandColors.AccentPressed,
    secondary = NeutralColors.LightOnSurfaceVariant,
    onSecondary = NeutralColors.LightSurface,
    background = NeutralColors.LightBackground,
    onBackground = NeutralColors.LightOnBackground,
    surface = NeutralColors.LightSurface,
    onSurface = NeutralColors.LightOnSurface,
    surfaceVariant = NeutralColors.LightSurfaceVariant,
    onSurfaceVariant = NeutralColors.LightOnSurfaceVariant,
    outline = NeutralColors.LightOutline,
    outlineVariant = NeutralColors.LightOutlineVariant,
    error = BrandColors.Danger,
    onError = BrandColors.OnAccent,
    errorContainer = BrandColors.DangerBackground,
    onErrorContainer = BrandColors.Danger,
    surfaceTint = BrandColors.Accent,
)

private val DarkColors = darkColorScheme(
    primary = BrandColors.AccentDarkMode,
    onPrimary = BrandColors.AccentPressed,
    primaryContainer = BrandColors.AccentPressed,
    onPrimaryContainer = BrandColors.AccentMuted,
    secondary = NeutralColors.DarkOnSurfaceVariant,
    onSecondary = NeutralColors.DarkSurface,
    background = NeutralColors.DarkBackground,
    onBackground = NeutralColors.DarkOnBackground,
    surface = NeutralColors.DarkSurface,
    onSurface = NeutralColors.DarkOnSurface,
    surfaceVariant = NeutralColors.DarkSurfaceVariant,
    onSurfaceVariant = NeutralColors.DarkOnSurfaceVariant,
    outline = NeutralColors.DarkOutline,
    outlineVariant = NeutralColors.DarkOutlineVariant,
    error = BrandColors.DangerDark,
    onError = BrandColors.OnDangerDark,
    errorContainer = BrandColors.DangerDarkContainer,
    onErrorContainer = BrandColors.OnDangerDarkContainer,
    surfaceTint = BrandColors.AccentDarkMode,
)

/**
 * 4Divari theme — Material 3 with brand tokens.
 *
 * Dynamic color intentionally off in Phase 1: brand identity first.
 * RTL is applied at the Activity/window level, not inside the theme.
 */
@Composable
fun ChardivariTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
