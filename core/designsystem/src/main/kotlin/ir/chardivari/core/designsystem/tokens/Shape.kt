package ir.chardivari.core.designsystem.tokens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Shape scale — restrained radii. No extreme rounding.
 * Cards read as premium through spacing and hierarchy, not blob corners.
 */
object AppShapes {
    val XSmall = RoundedCornerShape(4.dp)
    val Small = RoundedCornerShape(6.dp)
    val Medium = RoundedCornerShape(10.dp)
    val Large = RoundedCornerShape(14.dp)
    val ExtraLarge = RoundedCornerShape(20.dp)
    val BottomSheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    val Pill = RoundedCornerShape(50)
}

/**
 * 8dp-based spacing system.
 */
object AppSpacing {
    val None = 0.dp
    val Xxs = 2.dp
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 20.dp
    val Xxl = 24.dp
    val Xxxl = 32.dp
    val Section = 40.dp
}
