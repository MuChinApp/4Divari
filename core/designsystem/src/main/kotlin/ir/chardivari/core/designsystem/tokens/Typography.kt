package ir.chardivari.core.designsystem.tokens

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ir.chardivari.core.designsystem.R

/**
 * Persian-first typography scale.
 *
 * Vazirmatn variable font (OFL) — bundled, no network font fetch.
 * Line heights tuned for Persian diacritics and numerals.
 */
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_variable, weight = FontWeight.Light),
    Font(R.font.vazirmatn_variable, weight = FontWeight.Normal),
    Font(R.font.vazirmatn_variable, weight = FontWeight.Medium),
    Font(R.font.vazirmatn_variable, weight = FontWeight.SemiBold),
    Font(R.font.vazirmatn_variable, weight = FontWeight.Bold),
)

object AppTypographyScale {
    val Display = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
    )
    val HeadlineLarge = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 36.sp,
    )
    val HeadlineMedium = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 32.sp,
    )
    val TitleLarge = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 28.sp,
    )
    val TitleMedium = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    )
    val TitleSmall = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    )
    val BodyLarge = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,
    )
    val BodyMedium = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    )
    val BodySmall = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )
    val LabelLarge = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )
    val LabelMedium = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
    val PriceEmphasis = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    )
    val PriceSecondary = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    )
    val NumericTabular = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    )
}

val AppTypography = Typography(
    displayLarge = AppTypographyScale.Display,
    headlineLarge = AppTypographyScale.HeadlineLarge,
    headlineMedium = AppTypographyScale.HeadlineMedium,
    titleLarge = AppTypographyScale.TitleLarge,
    titleMedium = AppTypographyScale.TitleMedium,
    titleSmall = AppTypographyScale.TitleSmall,
    bodyLarge = AppTypographyScale.BodyLarge,
    bodyMedium = AppTypographyScale.BodyMedium,
    bodySmall = AppTypographyScale.BodySmall,
    labelLarge = AppTypographyScale.LabelLarge,
    labelMedium = AppTypographyScale.LabelMedium,
)
