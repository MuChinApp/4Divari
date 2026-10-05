package ir.chardivari.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import ir.chardivari.core.designsystem.tokens.AppShapes
import ir.chardivari.core.designsystem.tokens.AppSpacing
import ir.chardivari.core.designsystem.tokens.AppTypographyScale
import ir.chardivari.core.designsystem.tokens.BrandColors

/**
 * Verification chip — only shown when backend verification records exist.
 * Never fabricated client-side.
 */
@Composable
fun VerificationBadge(
    label: String,
    modifier: Modifier = Modifier,
    verified: Boolean = true,
) {
    if (!verified) return
    Surface(
        modifier = modifier,
        shape = AppShapes.XSmall,
        color = BrandColors.TrustBackground,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppSpacing.Xs, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                modifier = Modifier.height(12.dp),
                tint = BrandColors.Trust,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = BrandColors.Trust,
                maxLines = 1,
            )
        }
    }
}

/**
 * Data-source provenance label for development builds.
 * Real production data shows nothing; fixtures show a clear badge.
 */
@Composable
fun DataProvenanceTag(
    isFixture: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!isFixture) return
    DevDataBadge(modifier = modifier)
}

/**
 * Property card — premium, scannable, RTL-safe.
 *
 * All text passed in should already be locale-formatted (Persian digits).
 */
@Composable
fun PropertyCard(
    photoUrl: String?,
    priceLine: String,
    pricePerSqmLine: String,
    titleLine: String,
    locationLine: String,
    amenitiesLine: String?,
    verificationLabel: String?,
    updatedLabel: String,
    modifier: Modifier = Modifier,
    isFixtureData: Boolean = false,
    onFavorite: () -> Unit = {},
    onShare: () -> Unit = {},
    onCompare: () -> Unit = {},
    onClick: () -> Unit = {},
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.Medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onClick,
    ) {
        Column {
            // Media — fixed aspect, lazy-loaded. Placeholder = solid, not blur mush.
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentScale = ContentScale.Crop,
            )

            Column(
                modifier = Modifier.padding(AppSpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Xs),
            ) {
                if (isFixtureData) {
                    DataProvenanceTag(isFixture = true)
                }

                Text(
                    text = priceLine,
                    style = AppTypographyScale.PriceEmphasis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = pricePerSqmLine,
                    style = AppTypographyScale.PriceSecondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(AppSpacing.Xxs))

                Text(
                    text = titleLine,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = locationLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (amenitiesLine != null) {
                    Text(
                        text = amenitiesLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (verificationLabel != null) {
                    Spacer(Modifier.height(AppSpacing.Xxs))
                    VerificationBadge(label = verificationLabel)
                }

                Spacer(Modifier.height(AppSpacing.Xs))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = updatedLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Xs)) {
                        IconButton(onClick = onCompare, enabled = false) {
                            Icon(
                                imageVector = Icons.Outlined.CompareArrows,
                                contentDescription = "مقایسه",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = onShare) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = "اشتراک‌گذاری",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = onFavorite) {
                            Icon(
                                imageVector = Icons.Outlined.FavoriteBorder,
                                contentDescription = "ذخیره",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Glass-styled container for floating controls only
 * (map controls, bottom nav overlays) — never for dense body text.
 */
@Composable
fun FloatingSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = AppShapes.Pill,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 6.dp,
        shadowElevation = 4.dp,
    ) {
        content()
    }
}
