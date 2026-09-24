package ir.chardivari.core.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Supabase GoTrue phone-OTP contract (auth/v1).
 * Language-neutral wire format; UI maps to Persian copy.
 */

@Serializable
data class OtpRequest(
    val phone: String,
    val channel: String = "sms",
)

/** GoTrue returns 200 + opaque body when OTP is queued for SMS. */
@Serializable
data class OtpSentResponse(
    val id: String? = null,
)

@Serializable
data class VerifyOtpRequest(
    val type: String = "sms",
    val phone: String,
    val token: String,
)

@Serializable
data class RefreshRequest(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
data class AuthUser(
    val id: String,
    val phone: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class SessionResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Int? = null,
    @SerialName("expires_at") val expiresAt: Long? = null,
    @SerialName("token_type") val tokenType: String? = null,
    val user: AuthUser? = null,
)

@Serializable
data class AuthErrorBody(
    val code: Int? = null,
    @SerialName("error_code") val errorCode: String? = null,
    val msg: String? = null,
    val message: String? = null,
)
