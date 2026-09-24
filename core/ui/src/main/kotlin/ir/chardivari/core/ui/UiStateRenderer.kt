package ir.chardivari.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.UiState
import ir.chardivari.core.designsystem.components.EmptyState
import ir.chardivari.core.designsystem.components.ErrorState
import ir.chardivari.core.designsystem.components.LoadingState
import ir.chardivari.core.designsystem.components.OfflineState

/**
 * Generic renderer for [UiState] — every screen wires Loading/Content/Empty/Error/Offline
 * through this to guarantee consistent UX and no dead-end errors.
 */
@Composable
fun <T> UiStateRenderer(
    state: UiState<T>,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    emptyTitle: String = "موردی یافت نشد",
    emptyDescription: String? = null,
    loadingLabel: String = "در حال بارگذاری…",
    content: @Composable (T) -> Unit,
) {
    when (state) {
        UiState.Idle -> Unit

        UiState.Loading -> LoadingState(
            modifier = modifier.fillMaxSize(),
            label = loadingLabel,
        )

        is UiState.Content -> content(state.data)

        UiState.Empty -> EmptyState(
            modifier = modifier.fillMaxSize(),
            title = emptyTitle,
            description = emptyDescription,
        )

        is UiState.Error -> {
            when (state.error) {
                AppError.Offline -> OfflineState(
                    modifier = modifier.fillMaxSize(),
                    onRetry = onRetry,
                )
                else -> ErrorState(
                    modifier = modifier.fillMaxSize(),
                    title = errorTitle(state.error),
                    description = errorDescription(state.error),
                    canRetry = state.canRetry,
                    onRetry = onRetry,
                )
            }
        }
    }
}

fun errorTitle(error: AppError): String = when (error) {
    AppError.Offline -> "اتصال اینترنت برقرار نیست"
    AppError.Server -> "سرور در دسترس نیست"
    is AppError.Client -> "درخواست انجام نشد"
    AppError.Unauthorized -> "دسترسی مجاز نیست"
    is AppError.Validation -> "اطلاعات واردشده معتبر نیست"
    AppError.Serialization -> "پاسخ قابل پردازش نیست"
    AppError.RateLimited -> "درخواست‌ها زیاد هستند"
    AppError.Unexpected -> "خطای غیرمنتظره‌ای رخ داد"
}

fun errorDescription(error: AppError): String = when (error) {
    AppError.Offline -> "اینترنت خود را بررسی کنید و دوباره تلاش کنید."
    AppError.Server -> "چند لحظه دیگر دوباره تلاش کنید."
    is AppError.Client -> error.serverMessage
        ?: "درخواست شما پردازش نشد. شرایط را بررسی کنید."
    AppError.Unauthorized -> "برای ادامه وارد حساب خود شوید."
    is AppError.Validation -> if (error.fieldErrors.values.any()) {
        error.fieldErrors.values.first()
    } else {
        "فیلدهای الزامی را کامل کنید."
    }
    AppError.Serialization -> "چند لحظه دیگر دوباره تلاش کنید."
    AppError.RateLimited -> "کمی صبر کنید و دوباره امتحان کنید."
    AppError.Unexpected -> "دوباره تلاش کنید. اگر ادامه یافت، موضوع را گزارش دهید."
}

/** Simple centered text for placeholder destinations. */
@Composable
fun PlaceholderScreen(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            androidx.compose.foundation.layout.Spacer(
                Modifier.padding(top = 8.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
