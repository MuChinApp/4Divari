package ir.chardivari.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.EntryPointAccessors
import ir.chardivari.core.auth.AuthEntryPoint
import ir.chardivari.core.common.AppError
import ir.chardivari.core.designsystem.tokens.AppSpacing
import ir.chardivari.core.ui.errorDescription
import ir.chardivari.core.ui.errorTitle
import kotlinx.coroutines.flow.collectLatest

/**
 * Auth entry — builds [AuthViewModel] through [AuthEntryPoint] so this
 * module needs neither KSP nor the Hilt Gradle plugin (AGP task-cycle fix).
 */
@Composable
fun AuthRoute(
    onLoggedIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val viewModel: AuthViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val entry = EntryPointAccessors.fromApplication(context, AuthEntryPoint::class.java)
                return AuthViewModel(
                    repository = entry.authRepository(),
                    analytics = entry.analyticsTracker(),
                ) as T
            }
        },
    )

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            if (event is AuthEvent.Verified) onLoggedIn()
        }
    }

    AuthScreen(
        state = state,
        error = error,
        modifier = modifier,
        onPhoneChange = viewModel::onPhoneChanged,
        onSendOtp = viewModel::sendOtp,
        onVerify = viewModel::verifyOtp,
        onResend = viewModel::sendOtp,
        onBack = viewModel::backToPhone,
    )
}

@Composable
fun AuthScreen(
    state: AuthUiModel,
    error: AppError?,
    modifier: Modifier = Modifier,
    onPhoneChange: (String) -> Unit,
    onSendOtp: () -> Unit,
    onVerify: (String) -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (state.step == AuthStep.Otp) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "بازگشت",
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(AppSpacing.Xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (state.step == AuthStep.Phone) {
                    "ورود با شماره موبایل"
                } else {
                    "کد تایید"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(AppSpacing.Sm))
            Text(
                text = if (state.step == AuthStep.Phone) {
                    "کد یک‌بارمصرف به شمارهٔ شما پیامک می‌شود."
                } else {
                    "کد ارسال‌شده به ${state.phoneE164} را وارد کنید."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(AppSpacing.Xxl))

            if (state.step == AuthStep.Phone) {
                PhoneStep(
                    phone = state.phoneE164,
                    valid = state.phoneValid,
                    loading = state.loading,
                    onPhoneChange = onPhoneChange,
                    onSubmit = onSendOtp,
                )
            } else {
                OtpStep(
                    loading = state.loading,
                    onSubmit = onVerify,
                    onResend = onResend,
                )
            }

            if (error != null) {
                Spacer(Modifier.height(AppSpacing.Lg))
                Text(
                    text = listOfNotNull(
                        errorTitle(error),
                        errorDescription(error),
                    ).joinToString("\n"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PhoneStep(
    phone: String,
    valid: Boolean,
    loading: Boolean,
    onPhoneChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = phone,
            onValueChange = onPhoneChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("شماره موبایل") },
            placeholder = { Text("09xxxxxxxxx") },
            singleLine = true,
            enabled = !loading,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done,
            ),
            supportingText = {
                if (phone.isNotBlank() && !valid) {
                    Text("شماره موبایل معتبر نیست")
                }
            },
        )
        Spacer(Modifier.height(AppSpacing.Lg))
        Button(
            onClick = onSubmit,
            enabled = valid && !loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("ارسال کد تایید")
            }
        }
    }
}

@Composable
private fun OtpStep(
    loading: Boolean,
    onSubmit: (String) -> Unit,
    onResend: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = code,
            onValueChange = { raw ->
                code = raw.filter { it.isDigit() }.take(6)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("کد تایید") },
            placeholder = { Text("······") },
            singleLine = true,
            enabled = !loading,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
            ),
        )
        Spacer(Modifier.height(AppSpacing.Lg))
        Button(
            onClick = { onSubmit(code) },
            enabled = code.length >= 4 && !loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("تایید و ورود")
            }
        }
        TextButton(
            onClick = onResend,
            enabled = !loading,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("ارسال دوباره کد")
        }
    }
}
