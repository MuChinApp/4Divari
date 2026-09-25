package ir.chardivari.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.chardivari.core.designsystem.tokens.AppSpacing

/**
 * Profile — Phase 1 shows an honest auth entry point.
 * No fake "logged in" state. Phase 4 adds the seller (list my property) entry;
 * Phase 5 adds the agent dashboard entry — role gating happens honestly inside
 * the agent route (not-logged-in / no-agent-role states), login gating lives
 * in the seller feature itself.
 */
@Composable
fun ProfileRoute(
    modifier: Modifier = Modifier,
    onLogin: () -> Unit = {},
    onOpenSeller: () -> Unit = {},
    onOpenAgent: () -> Unit = {},
    onOpenMessages: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(AppSpacing.Xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(AppSpacing.Lg))
            Text(
                text = "حساب کاربری",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(AppSpacing.Sm))
            Text(
                text = "با شماره موبایل و کد یک‌بارمصرف وارد شوید.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(AppSpacing.Xxl))
            OutlinedButton(
                onClick = onLogin,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("ورود / ثبت‌نام")
            }
            Spacer(Modifier.height(AppSpacing.Md))
            OutlinedButton(
                onClick = onOpenSeller,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("ثبت ملک و مدیریت آگهی‌ها")
            }
            Spacer(Modifier.height(AppSpacing.Md))
            OutlinedButton(
                onClick = onOpenAgent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("داشبورد مشاور")
            }
            Spacer(Modifier.height(AppSpacing.Md))
            OutlinedButton(
                onClick = onOpenMessages,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("گفتگوها")
            }
            Spacer(Modifier.height(AppSpacing.Md))
            OutlinedButton(
                onClick = onOpenNotifications,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("اعلان‌ها")
            }
        }
    }
}
