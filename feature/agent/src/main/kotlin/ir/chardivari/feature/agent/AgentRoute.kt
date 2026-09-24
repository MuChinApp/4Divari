package ir.chardivari.feature.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ir.chardivari.core.common.UiState
import ir.chardivari.core.designsystem.components.ErrorState
import ir.chardivari.core.designsystem.components.LoadingState
import ir.chardivari.core.designsystem.tokens.AppSpacing

/**
 * Agent entry — honest gate states (not logged in / no agent role), then the
 * five-tab internal shell. Reached from the Profile screen; customer root
 * tabs never swap.
 */
@Composable
fun AgentRoute(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onListingClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    gateViewModel: AgentGateViewModel = hiltViewModel(),
) {
    val state by gateViewModel.uiState.collectAsStateWithLifecycle()
    when (val s = state) {
        UiState.Idle, UiState.Loading -> LoadingState(
            modifier = modifier.fillMaxSize(),
            label = "در حال بررسی دسترسی…",
        )

        is UiState.Content -> when (val gate = s.data) {
            AgentGate.Loading -> LoadingState(
                modifier = modifier.fillMaxSize(),
                label = "در حال بررسی دسترسی…",
            )

            AgentGate.NotLoggedIn -> GateMessage(
                modifier = modifier,
                icon = { Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                title = "ورود به حساب",
                description = "برای دسترسی به داشبورد مشاور وارد حساب خود شوید.",
                primaryActionLabel = "ورود / ثبت‌نام",
                onPrimaryAction = onLogin,
                secondaryActionLabel = "بازگشت",
                onSecondaryAction = onBack,
            )

            AgentGate.NotAgent -> GateMessage(
                modifier = modifier,
                icon = { Icon(Icons.Outlined.PersonOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                title = "دسترسی مشاور ندارید",
                description = "این بخش فقط برای مشاوران فعال در چاردیواری است. حساب شما نقش مشاور ندارد.",
                primaryActionLabel = "بازگشت",
                onPrimaryAction = onBack,
                secondaryActionLabel = null,
                onSecondaryAction = null,
            )

            is AgentGate.Ready -> AgentShell(
                displayName = gate.displayName,
                onListingClick = onListingClick,
            )
        }

        is UiState.Error -> ErrorState(
            modifier = modifier.fillMaxSize(),
            title = "دسترسی بررسی نشد",
            description = "اتصال خود را بررسی کنید و دوباره تلاش کنید.",
            canRetry = true,
            onRetry = gateViewModel::load,
        )

        UiState.Empty -> Unit
    }
}

@Composable
private fun GateMessage(
    modifier: Modifier,
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    secondaryActionLabel: String?,
    onSecondaryAction: (() -> Unit)?,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(AppSpacing.Xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Spacer(Modifier.height(AppSpacing.Lg))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(AppSpacing.Sm))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(AppSpacing.Xxl))
        OutlinedButton(
            onClick = onPrimaryAction,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(primaryActionLabel)
        }
        if (secondaryActionLabel != null && onSecondaryAction != null) {
            Spacer(Modifier.height(AppSpacing.Md))
            OutlinedButton(
                onClick = onSecondaryAction,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(secondaryActionLabel)
            }
        }
    }
}

@Composable
private fun AgentShell(
    displayName: String?,
    onListingClick: (String) -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                AgentTab.all.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(AgentRoutes.DASHBOARD) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.labelFa,
                            )
                        },
                        label = { Text(tab.labelFa) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AgentRoutes.DASHBOARD,
            modifier = Modifier.padding(padding),
        ) {
            composable(AgentRoutes.DASHBOARD) {
                AgentDashboardScreen()
            }
            composable(AgentRoutes.FILES) {
                AgentFilesScreen(onListingClick = onListingClick)
            }
            composable(AgentRoutes.LEADS) {
                AgentLeadsScreen()
            }
            composable(AgentRoutes.VISITS) {
                AgentVisitsScreen()
            }
            composable(AgentRoutes.PROFILE) {
                AgentProfileScreen(displayName = displayName)
            }
        }
    }
}
