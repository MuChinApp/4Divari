package ir.chardivari.core.marketplace

import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.environment.AppConfig
import ir.chardivari.core.network.SafeApi
import ir.chardivari.core.network.SessionTokenProvider
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/*
 * Phase 7 trust surfaces — reporting, freshness confirmation, verification
 * lifecycle, admin moderation. All flags/queues are computed server-side from
 * real data (00096_trust_tooling.sql); nothing here invents a score.
 */

@Serializable
data class ReportListingRequestDto(
    @SerialName("p_listing_id")
    val listingId: String,
    @SerialName("p_reason")
    val reason: String,
    @SerialName("p_detail")
    val detail: String? = null,
)

@Serializable
data class ListingIdRequestDto(
    @SerialName("p_listing_id")
    val listingId: String,
)

@Serializable
data class VerificationDecisionRequestDto(
    @SerialName("p_listing_id")
    val listingId: String,
    @SerialName("p_status")
    val status: String,
)

@Serializable
data class ReportStatusPatchDto(
    val status: String,
)

@Serializable
data class QueueCountsDto(
    @SerialName("open_reports")
    val openReports: Int = 0,
    @SerialName("pending_verifications")
    val pendingVerifications: Int = 0,
)

/** Evidence behind a risk flag — kept as raw jsonb, never interpreted twice. */
data class RiskFlag(
    val code: String,
    val severity: String,
    val evidence: JsonObject? = null,
)

@Serializable
data class RiskFlagDto(
    val code: String,
    val severity: String,
    val evidence: JsonObject? = null,
)

fun RiskFlagDto.toRiskFlag(): RiskFlag = RiskFlag(
    code = code,
    severity = severity,
    evidence = evidence,
)

@Serializable
data class QueueListingInfoDto(
    val city: String? = null,
    val neighborhood: String? = null,
    @SerialName("area_sqm")
    val areaSqm: Int? = null,
    @SerialName("price_rial")
    val priceRial: Long? = null,
    @SerialName("deal_type")
    val dealType: DealType? = null,
    @SerialName("verification_status")
    val verificationStatus: String? = null,
    val freshness: String? = null,
)

data class QueueListingInfo(
    val address: String?,
    val areaSqm: Int?,
    val priceRial: Long?,
    val verificationStatus: String?,
    val freshness: String?,
)

fun QueueListingInfoDto.toQueueListingInfo(): QueueListingInfo = QueueListingInfo(
    address = listOfNotNull(city, neighborhood)
        .takeIf { it.isNotEmpty() }
        ?.joinToString("، "),
    areaSqm = areaSqm,
    priceRial = priceRial,
    verificationStatus = verificationStatus,
    freshness = freshness,
)

@Serializable
data class QueueReportDto(
    val id: String,
    @SerialName("listing_id")
    val listingId: String? = null,
    val reason: String,
    val detail: String? = null,
    val status: String,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("reporter_id")
    val reporterId: String? = null,
    val listing: QueueListingInfoDto? = null,
    val risk: List<RiskFlagDto> = emptyList(),
)

data class QueueReport(
    val id: String,
    val listingId: String?,
    val reason: String,
    val detail: String?,
    val status: String,
    val createdAt: String?,
    val listing: QueueListingInfo?,
    val risk: List<RiskFlag>,
)

fun QueueReportDto.toQueueReport(): QueueReport = QueueReport(
    id = id,
    listingId = listingId,
    reason = reason,
    detail = detail,
    status = status,
    createdAt = createdAt,
    listing = listing?.toQueueListingInfo(),
    risk = risk.map { it.toRiskFlag() },
)

@Serializable
data class QueueVerificationDto(
    @SerialName("listing_id")
    val listingId: String,
    @SerialName("verification_status")
    val verificationStatus: String,
    @SerialName("seller_id")
    val sellerId: String? = null,
    @SerialName("agent_id")
    val agentId: String? = null,
    @SerialName("published_at")
    val publishedAt: String? = null,
    val listing: QueueListingInfoDto? = null,
    val risk: List<RiskFlagDto> = emptyList(),
)

data class QueueVerification(
    val listingId: String,
    val verificationStatus: String,
    val publishedAt: String?,
    val listing: QueueListingInfo?,
    val risk: List<RiskFlag>,
)

fun QueueVerificationDto.toQueueVerification(): QueueVerification = QueueVerification(
    listingId = listingId,
    verificationStatus = verificationStatus,
    publishedAt = publishedAt,
    listing = listing?.toQueueListingInfo(),
    risk = risk.map { it.toRiskFlag() },
)

@Serializable
data class ModerationQueueDto(
    val counts: QueueCountsDto = QueueCountsDto(),
    val reports: List<QueueReportDto> = emptyList(),
    val verifications: List<QueueVerificationDto> = emptyList(),
    @SerialName("generated_at")
    val generatedAt: String? = null,
)

data class ModerationQueue(
    val openReports: Int,
    val pendingVerifications: Int,
    val reports: List<QueueReport>,
    val verifications: List<QueueVerification>,
)

fun ModerationQueueDto.toModerationQueue(): ModerationQueue = ModerationQueue(
    openReports = counts.openReports,
    pendingVerifications = counts.pendingVerifications,
    reports = reports.map { it.toQueueReport() },
    verifications = verifications.map { it.toQueueVerification() },
)

/** Customer-side trust actions. */
interface TrustRepository {
    suspend fun reportListing(
        listingId: String,
        reason: String,
        detail: String?,
    ): AppResult<String>

    /** Party-only freshness reset; returns the new freshness value. */
    suspend fun confirmListing(listingId: String): AppResult<String>

    /** Party-only verification request; returns verification_status. */
    suspend fun requestVerification(listingId: String): AppResult<String>
}

/** Admin moderation queue + decisions. */
interface AdminRepository {
    suspend fun isAdmin(): AppResult<Boolean>

    suspend fun queue(): AppResult<ModerationQueue>

    suspend fun resolveReport(reportId: String, status: String): AppResult<Unit>

    /** status ∈ verified|rejected. */
    suspend fun decideVerification(listingId: String, status: String): AppResult<String>
}

@Singleton
class SupabaseTrustRepository @Inject constructor(
    private val config: AppConfig,
    private val api: MarketplaceApi,
    private val safeApi: SafeApi,
    private val session: SessionTokenProvider,
) : TrustRepository {

    override suspend fun reportListing(
        listingId: String,
        reason: String,
        detail: String?,
    ): AppResult<String> {
        val (_, key) = config.requireRest() ?: return marketplaceConfigError()
        session.currentUserId() ?: return AppResult.Failure(AppError.Unauthorized)
        val auth = session.accessToken()?.takeIf { it.isNotBlank() }
            ?: return AppResult.Failure(AppError.Unauthorized)
        val trimmed = reason.trim()
        if (trimmed.length !in 3..500) {
            return AppResult.Failure(AppError.Validation())
        }
        return when (
            val result = safeApi.call {
                api.reportListing(
                    apiKey = key,
                    authorization = "Bearer $auth",
                    body = ReportListingRequestDto(
                        listingId = listingId,
                        reason = trimmed,
                        detail = detail?.trim()?.takeIf { it.isNotEmpty() },
                    ),
                )
            }
        ) {
            is AppResult.Success -> result.data?.let { AppResult.Success(it) }
                ?: AppResult.Failure(AppError.Serialization)
            is AppResult.Failure -> result
            AppResult.Empty -> AppResult.Failure(AppError.Serialization)
        }
    }

    override suspend fun confirmListing(listingId: String): AppResult<String> =
        trustStringRpc { api, key, auth ->
            api.confirmListing(
                apiKey = key,
                authorization = "Bearer $auth",
                body = ListingIdRequestDto(listingId = listingId),
            )
        }

    override suspend fun requestVerification(listingId: String): AppResult<String> =
        trustStringRpc { api, key, auth ->
            api.requestListingVerification(
                apiKey = key,
                authorization = "Bearer $auth",
                body = ListingIdRequestDto(listingId = listingId),
            )
        }

    private suspend fun trustStringRpc(
        call: suspend (
            api: MarketplaceApi,
            key: String,
            auth: String,
        ) -> retrofit2.Response<String>,
    ): AppResult<String> {
        val (_, key) = config.requireRest() ?: return marketplaceConfigError()
        session.currentUserId() ?: return AppResult.Failure(AppError.Unauthorized)
        val auth = session.accessToken()?.takeIf { it.isNotBlank() }
            ?: return AppResult.Failure(AppError.Unauthorized)
        return when (val result = safeApi.call { call(api, key, auth) }) {
            is AppResult.Success -> result.data?.let { AppResult.Success(it) }
                ?: AppResult.Failure(AppError.Serialization)
            is AppResult.Failure -> result
            AppResult.Empty -> AppResult.Failure(AppError.Serialization)
        }
    }
}

@Singleton
class SupabaseAdminRepository @Inject constructor(
    private val config: AppConfig,
    private val api: MarketplaceApi,
    private val safeApi: SafeApi,
    private val session: SessionTokenProvider,
) : AdminRepository {

    override suspend fun isAdmin(): AppResult<Boolean> {
        val (base, key) = config.requireRest() ?: return marketplaceConfigError()
        val uid = session.currentUserId() ?: return AppResult.Failure(AppError.Unauthorized)
        val auth = session.accessToken()?.takeIf { it.isNotBlank() }
            ?: return AppResult.Failure(AppError.Unauthorized)
        return when (
            val result = safeApi.call {
                api.fetchMyRoles(
                    url = "$base/rest/v1/user_roles",
                    apiKey = key,
                    authorization = "Bearer $auth",
                    select = "role",
                    userId = uid,
                    role = "eq.ADMIN",
                )
            }
        ) {
            is AppResult.Success -> AppResult.Success(result.data.orEmpty().isNotEmpty())
            is AppResult.Failure -> result
            AppResult.Empty -> AppResult.Success(false)
        }
    }

    override suspend fun queue(): AppResult<ModerationQueue> {
        val (_, key) = config.requireRest() ?: return marketplaceConfigError()
        session.currentUserId() ?: return AppResult.Failure(AppError.Unauthorized)
        val auth = session.accessToken()?.takeIf { it.isNotBlank() }
            ?: return AppResult.Failure(AppError.Unauthorized)
        return when (
            val result = safeApi.call {
                api.moderationQueue(apiKey = key, authorization = "Bearer $auth")
            }
        ) {
            is AppResult.Success -> {
                val row = result.data ?: return AppResult.Failure(AppError.Serialization)
                AppResult.Success(row.toModerationQueue())
            }
            is AppResult.Failure -> result
            AppResult.Empty -> AppResult.Failure(AppError.Serialization)
        }
    }

    override suspend fun resolveReport(reportId: String, status: String): AppResult<Unit> {
        val (_, key) = config.requireRest() ?: return marketplaceConfigError()
        session.currentUserId() ?: return AppResult.Failure(AppError.Unauthorized)
        val auth = session.accessToken()?.takeIf { it.isNotBlank() }
            ?: return AppResult.Failure(AppError.Unauthorized)
        return when (
            val result = safeApi.call {
                api.updateReportStatus(
                    id = "eq.$reportId",
                    apiKey = key,
                    authorization = "Bearer $auth",
                    body = ReportStatusPatchDto(status = status),
                )
            }
        ) {
            is AppResult.Success, AppResult.Empty -> AppResult.Success(Unit)
            is AppResult.Failure -> result
        }
    }

    override suspend fun decideVerification(
        listingId: String,
        status: String,
    ): AppResult<String> {
        val (_, key) = config.requireRest() ?: return marketplaceConfigError()
        session.currentUserId() ?: return AppResult.Failure(AppError.Unauthorized)
        val auth = session.accessToken()?.takeIf { it.isNotBlank() }
            ?: return AppResult.Failure(AppError.Unauthorized)
        return when (
            val result = safeApi.call {
                api.adminSetListingVerification(
                    apiKey = key,
                    authorization = "Bearer $auth",
                    body = VerificationDecisionRequestDto(
                        listingId = listingId,
                        status = status,
                    ),
                )
            }
        ) {
            is AppResult.Success -> result.data?.let { AppResult.Success(it) }
                ?: AppResult.Failure(AppError.Serialization)
            is AppResult.Failure -> result
            AppResult.Empty -> AppResult.Failure(AppError.Serialization)
        }
    }
}
