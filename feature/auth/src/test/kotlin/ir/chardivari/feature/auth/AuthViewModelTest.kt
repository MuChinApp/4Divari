package ir.chardivari.feature.auth

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import ir.chardivari.core.auth.AuthRepository
import ir.chardivari.core.auth.AuthSession
import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<AuthRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `invalid phone does not call repository`() = runTest {
        val vm = AuthViewModel(repository)
        vm.onPhoneChanged("123")
        vm.sendOtp()
        dispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 0) { repository.sendOtp(any()) }
    }

    @Test
    fun `valid phone sendOtp success moves to otp step`() = runTest {
        coEvery { repository.sendOtp("+989123456789") } returns AppResult.Success(Unit)
        val vm = AuthViewModel(repository)
        vm.uiState.test {
            val initial = awaitItem()
            assertThat(initial.step).isEqualTo(AuthStep.Phone)

            vm.onPhoneChanged("09123456789")
            assertThat(awaitItem().phoneValid).isTrue()

            vm.sendOtp()
            dispatcher.scheduler.advanceUntilIdle()

            val final = expectMostRecentItem()
            assertThat(final.step).isEqualTo(AuthStep.Otp)
            assertThat(final.loading).isFalse()
        }
    }

    @Test
    fun `sendOtp failure surfaces error and stays on phone step`() = runTest {
        coEvery { repository.sendOtp(any()) } returns
            AppResult.Failure(AppError.RateLimited)
        val vm = AuthViewModel(repository)
        vm.onPhoneChanged("09123456789")
        vm.sendOtp()
        dispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.error.value).isEqualTo(AppError.RateLimited)
        assertThat(vm.uiState.value.step).isEqualTo(AuthStep.Phone)
        assertThat(vm.uiState.value.loading).isFalse()
    }

    @Test
    fun `verifyOtp success emits Verified event`() = runTest {
        val session = AuthSession(
            userId = "u1",
            accessToken = "a",
            refreshToken = "r",
            expiresAtEpochSeconds = 0L,
        )
        coEvery { repository.sendOtp(any()) } returns AppResult.Success(Unit)
        coEvery { repository.verifyOtp(any(), any()) } returns AppResult.Success(session)

        val vm = AuthViewModel(repository)
        vm.onPhoneChanged("09123456789")
        vm.sendOtp()
        dispatcher.scheduler.advanceUntilIdle()

        vm.events.test {
            vm.verifyOtp("123456")
            dispatcher.scheduler.advanceUntilIdle()
            val event = awaitItem()
            assertThat(event).isInstanceOf(AuthEvent.Verified::class.java)
            assertThat((event as AuthEvent.Verified).session.userId).isEqualTo("u1")
        }
    }

    @Test
    fun `short otp rejected client side`() = runTest {
        val vm = AuthViewModel(repository)
        vm.onPhoneChanged("09123456789")
        vm.verifyOtp("12")
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.error.value)
            .isInstanceOf(AppError.Validation::class.java)
        coVerify(exactly = 0) { repository.verifyOtp(any(), any()) }
    }
}
