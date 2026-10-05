package ir.chardivari.feature.messaging

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.chardivari.core.common.toPersianDigits
import ir.chardivari.core.designsystem.tokens.AppSpacing
import ir.chardivari.core.marketplace.ChatMessage
import ir.chardivari.core.ui.UiStateRenderer
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    viewModel: ThreadViewModel,
    onBack: () -> Unit,
    onLogin: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                ThreadEvent.LoginRequired -> onLogin()
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("گفتگو") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "بازگشت",
                        )
                    }
                },
            )
        },
    ) { padding ->
        UiStateRenderer(
            state = state,
            modifier = Modifier.padding(padding),
            onRetry = viewModel::load,
            emptyTitle = "پیامی در این گفتگو نیست",
            emptyDescription = "اولین پیام را بنویسید.",
        ) { thread ->
            LaunchedEffect(thread.messages.size) {
                if (thread.messages.isNotEmpty()) {
                    listState.animateScrollToItem(thread.messages.size - 1)
                }
            }
            Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(AppSpacing.Md),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm),
                ) {
                    items(thread.messages, key = { it.id }) { message ->
                        MessageBubble(message = message)
                    }
                }
                thread.sendError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppSpacing.Md),
                        textAlign = TextAlign.Center,
                    )
                }
                MessageInput(
                    value = thread.input,
                    sending = thread.sending,
                    onValueChange = viewModel::setInput,
                    onSend = viewModel::send,
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val mine = message.isMine
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) {
            Arrangement.End
        } else {
            Arrangement.Start
        },
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    color = if (mine) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = MaterialTheme.shapes.medium,
                )
                .padding(horizontal = AppSpacing.Md, vertical = AppSpacing.Sm),
        ) {
            Column {
                Text(
                    text = message.body ?: "پیام حذف شده است",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (mine) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Spacer(Modifier.width(AppSpacing.Sm))
                Text(
                    text = message.sentAt
                        .substringAfter('T', "")
                        .take(5)
                        .toPersianDigits(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (mine) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

@Composable
private fun MessageInput(
    value: String,
    sending: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(AppSpacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("پیام بنویسید…") },
            enabled = !sending,
            maxLines = 4,
        )
        Spacer(Modifier.width(AppSpacing.Sm))
        if (sending) {
            CircularProgressIndicator(modifier = Modifier.width(24.dp))
        } else {
            FilledIconButton(
                onClick = onSend,
                enabled = value.isNotBlank(),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Send,
                    contentDescription = "ارسال",
                )
            }
        }
    }
}
