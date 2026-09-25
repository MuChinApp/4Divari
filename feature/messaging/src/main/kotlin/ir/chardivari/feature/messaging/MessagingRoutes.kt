package ir.chardivari.feature.messaging

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ConversationsRoute(
    onBack: () -> Unit,
    onOpenThread: (conversationId: String) -> Unit,
    viewModel: ConversationsViewModel = hiltViewModel(),
) {
    ConversationsListScreen(
        viewModel = viewModel,
        onBack = onBack,
        onOpenThread = onOpenThread,
        onRefresh = viewModel::load,
    )
}

@Composable
fun ThreadRoute(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    viewModel: ThreadViewModel = hiltViewModel(),
) {
    ThreadScreen(
        viewModel = viewModel,
        onBack = onBack,
        onLogin = onLogin,
    )
}

@Composable
fun NotificationsRoute(
    onBack: () -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    NotificationsScreen(
        viewModel = viewModel,
        onBack = onBack,
        onRefresh = viewModel::load,
    )
}
