package ir.chardivari.core.designsystem.tokens

import androidx.compose.ui.graphics.Color

/**
 * 4Divari color tokens — Information First.
 *
 * Deep neutral canvas + off-white surfaces + single brand accent.
 * No decorative gradients; accent is reserved for primary actions and trust.
 */
object BrandColors {
    /** Brand accent — confident teal, distinct from generic "app blue". */
    val Accent = Color(0xFF0E7C7B)
    val AccentPressed = Color(0xFF0A5F5E)
    val AccentMuted = Color(0xFFD3EDEC)
    val AccentDarkMode = Color(0xFF5FCFCD)
    val OnAccent = Color(0xFFFFFFFF)

    /** Trust / verification semantic. */
    val Trust = Color(0xFF1B7F4E)
    val TrustBackground = Color(0xFFE3F5EC)

    /** Alert / destructive / stale listings. */
    val Danger = Color(0xFFB3261E)
    val DangerBackground = Color(0xFFFBEAE8)
    val DangerDark = Color(0xFFFFB4AB)
    val DangerDarkContainer = Color(0xFF93000A)
    val OnDangerDarkContainer = Color(0xFFFFDAD6)
    val OnDangerDark = Color(0xFF690005)

    /** Price drop semantic. */
    val PositiveChange = Color(0xFF1B7F4E)
}

object NeutralColors {
    // Light
    val LightBackground = Color(0xFFF7F7F5)
    val LightSurface = Color(0xFFFFFFFF)
    val LightOutline = Color(0xFFE4E4E0)
    val LightOutlineVariant = Color(0xFFEFEFEB)
    val LightOnBackground = Color(0xFF1A1C1B)
    val LightOnSurface = Color(0xFF1A1C1B)
    val LightOnSurfaceVariant = Color(0xFF5C615E)
    val LightSurfaceVariant = Color(0xFFEFEFEB)

    // Dark — depth + contrast, not pure black
    val DarkBackground = Color(0xFF101312)
    val DarkSurface = Color(0xFF1A1E1D)
    val DarkSurfaceElevated = Color(0xFF222726)
    val DarkOutline = Color(0xFF2E3432)
    val DarkOutlineVariant = Color(0xFF252A29)
    val DarkOnBackground = Color(0xFFE7EAE8)
    val DarkOnSurface = Color(0xFFE7EAE8)
    val DarkOnSurfaceVariant = Color(0xFFA8AEAB)
    val DarkSurfaceVariant = Color(0xFF252A29)

    // Shared
    val Scrim = Color(0x66000000)
}

object PriceColors {
    val LightSale = Color(0xFF0E7C7B)
    val LightRent = Color(0xFF8A5A00)
    val DarkSale = Color(0xFF5FCFCD)
    val DarkRent = Color(0xFFE4B155)
}

object SkeletonColors {
    val Light = Color(0xFFECECE8)
    val Dark = Color(0xFF252A29)
}
