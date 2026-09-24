package ir.chardivari.core.marketplace

import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.environment.AppConfig
import ir.chardivari.core.network.SafeApi
import ir.chardivari.core.network.SessionTokenProvider
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/*
 * Agent tooling contracts + repository — Phase 5.
 *
 * Gate = AGENT role (read own `user_roles` under RLS); dashboard/requirement/
 * matching/assignment all go through SECURITY DEFINER RPCs from
 * `00094_agent_tooling.sql`. Leads/visits/matches are plain tables read under
 * their own RLS (agent-side policies). Everything fails closed without a
 * session — never fabricates rows.
 */

@Serializable
data class UserRoleDto(
    val role: String,
)

@Serializable
data class AgentDashboardDto(
    @SerialName("files_active")
    val filesActive: Int = 0,
    @SerialName("files_paused")
    val filesPaused: Int = 0,
    @SerialName("leads_new")
    val leadsNew: Int = 0,
    @SerialName("leads_in_progress")
    val leadsInProgress: Int = 0,
    @SerialName("visits_pending")
    val visitsPending: Int = 0,
    @SerialName("matches_today")
    val matchesToday: Int = 0,
    @SerialName("requirements_active")
    val requirementsActive: Int = 0,
)

/** Dashboard tiles — server-computed counts (no client-side guessing). */
data class AgentDashboard(
    val filesActive: Int,
    val filesPaused: Int,
    val leadsNew: Int,
    val leadsInProgress: Int,
    val visitsPending: Int,
    val matchesToday: Int,
    val requirementsActive: Int,
)

@Serializable
data class LeadDto(
    val id: String,
    @SerialName("listing_id")
    val listingId: String,
    val source: String? = null,
    val stage: String,
    val priority: Int = 3,
    val notes: String? = null,
    @SerialName("next_action_at")
    val nextActionAt: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    val listing: LeadListingRefDto? = null,
    val buyer: LeadUserRefDto? = null,
)

@Serializable
data class LeadListingRefDto(
    val id: String,
    @SerialName("deal_type")
    val dealType: DealType,
    @SerialName("price_rial")
    val priceRial: Long,
    @SerialName("deposit_rial")
    val depositRial: Long? = null,
    @SerialName("rent_rial")
    val rentRial: Long? = null,
    val status: String,
    val property: LeadPropertyRefDto? = null,
)

@Serializable
data class LeadPropertyRefDto(
    @SerialName("property_type")
    val propertyType: PropertyType? = null,
    @SerialName("area_sqm")
    val areaSqm: Int? = null,
    val city: String? = null,
    val neighborhood: String? = null,
)

@Serializable
data class LeadUserRefDto(
    val id: String? = null,
    @SerialName("phone_e164")
    val phoneE164: String? = null,
    val profiles: LeadProfileDto? = null,
)

@Serializable
data class LeadProfileDto(
    @SerialName("display_name")
    val displayName: String? = null,
)

/** Lead pipeline row — agent view (CRM). */
data class AgentLead(
    val id: String,
    val listingId: String,
    val stage: String,
    val priority: Int,
    val source: String?,
    val notes: String?,
    val createdAt: String?,
    val buyerName: String?,
    val buyerPhone: String?,
    val address: String?,
    val priceRial: Long?,
    val dealType: DealType?,
    val listingStatus: String?,
)

fun LeadDto.toAgentLead(): AgentLead {
    val property = listing?.property
    val address = listOfNotNull(property?.city, property?.neighborhood)
        .takeIf { it.isNotEmpty() }
        ?.joinToString("، ")
    return AgentLead(
        id = id,
        listingId = listingId,
        stage = stage,
        priority = priority,
        source = source,
        notes = notes,
        createdAt = createdAt,
        buyerName = buyer?.profiles?.displayName,
        buyerPhone = buyer?.phoneE164,
        address = address,
        priceRial = listing?.priceRial,
        dealType = listing?.dealType,
        listingStatus = listing?.status,
    )
}

/**
 * PATCH body — null fields must be OMITTED (never sent as explicit null,
 * which would clear the column). [kotlinx.serialization.EncodeDefault.Mode.NEVER]
 * makes that deterministic regardless of the app-wide encodeDefaults flag.
 */
@Serializable
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
data class LeadPatchDto(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val stage: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val priority: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val notes: String? = null,
)

@Serializable
data class LeadMatchDto(
    val id: String? = null,
    @SerialName("listing_id")
    val listingId: String,
    val score: Double? = null,
    val explanation: JsonObject? = null,
    val listing: LeadListingRefDto? = null,
)

/** Match row for one lead's requirement x one of the agent's listings. */
data class LeadMatch(
    val listingId: String,
    val score: Double,
    val explanation: LeadExplanation?,
    val address: String?,
    val priceRial: Long?,
    val dealType: DealType?,
)

private val EXPLANATION_JSON = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
}

@Serializable
data class LeadExplanation(
    val city: Boolean? = null,
    val budget: String? = null,
    val area: String? = null,
    val bedrooms: Boolean? = null,
)

fun LeadMatchDto.toLeadMatch(): LeadMatch? {
    val score = score ?: return null
    val property = listing?.property
    val address = listOfNotNull(property?.city, property?.neighborhood)
        .takeIf { it.isNotEmpty() }
        ?.joinToString("، ")
    return LeadMatch(
        listingId = listingId,
        score = score,
        explanation = explanation?.let { raw ->
            EXPLANATION_JSON.decodeFromString(LeadExplanation.serializer(), raw.toString())
        },
        address = address,
        priceRial = listing?.priceRial,
        dealType = listing?.dealType,
    )
}

@Serializable
data class BuyerRequirementDto(
    val id: String,
    @SerialName("lead_id")
    val leadId: String? = null,
    @SerialName("buyer_id")
    val buyerId: String? = null,
    @SerialName("deal_type")
    val dealType: DealType,
    @SerialName("budget_min_rial")
    val budgetMinRial: Long? = null,
    @SerialName("budget_max_rial")
    val budgetMaxRial: Long? = null,
    @SerialName("area_min")
    val areaMin: Int? = null,
    @SerialName("area_max")
    val areaMax: Int? = null,
    val bedrooms: List<Int>? = null,
    val cities: List<String>? = null,
    val features: JsonObject? = null,
    @SerialName("is_active")
    val isActive: Boolean = true,
)

/** One lead's buyer requirement (form pre-fill). */
data class BuyerRequirement(
    val id: String,
    val dealType: DealType,
    val budgetMinRial: Long?,
    val budgetMaxRial: Long?,
    val areaMin: Int?,
    val areaMax: Int?,
    val bedrooms: List<Int>?,
    val cities: List<String>?,
    val features: JsonObject?,
)

fun BuyerRequirementDto.toRequirement(): BuyerRequirement = BuyerRequirement(
    id = id,
    dealType = dealType,
    budgetMinRial = budgetMinRial,
    budgetMaxRial = budgetMaxRial,
    areaMin = areaMin,
    areaMax = areaMax,
    bedrooms = bedrooms,
    cities = cities,
    features = features,
)

/** Requirement form input — validated in the ViewModel before the RPC. */
data class RequirementInput(
    val leadId: String,
    val dealType: DealType,
    val budgetMinRial: Long?,
    val budgetMaxRial: Long?,
    val areaMin: Int?,
    val areaMax: Int?,
    val bedrooms: List<Int>?,
    val cities: List<String>?,
    val features: JsonObject?,
)

@Serializable
data class AgentUpsertRequirementRequestDto(
    @SerialName("p_lead_id")
    val leadId: String,
    @SerialName("p_deal_type")
    val dealType: String,
    @SerialName("p_budget_min_rial")
    val budgetMinRial: Long? = null,
    @SerialName("p_budget_max_rial")
    val budgetMaxRial: Long? = null,
    @SerialName("p_area_min")
    val areaMin: Int? = null,
    @SerialName("p_area_max")
    val areaMax: Int? = null,
    @SerialName("p_bedrooms")
    val bedrooms: List<Int>? = null,
    @SerialName("p_cities")
    val cities: List<String>? = null,
    @SerialName("p_features")
    val features: JsonObject? = null,
)

@Serializable
data class RefreshLeadMatchesRequestDto(
    @SerialName("p_lead_id")
    val leadId: String,
)

@Serializable
data class AssignAgentRequestDto(
    @SerialName("p_listing_id")
    val listingId: String,
    @SerialName("p_agent_phone")
    val agentPhone: String? = null,
)

@Serializable
data class AssignAgentResultDto(
    @SerialName("agent_id")
    val agentId: String? = null,
)

@Serializable
data class VisitDto(
    val id: String,
    @SerialName("listing_id")
    val listingId: String,
    @SerialName("slot_start")
    val slotStart: String,
    @SerialName("slot_end")
    val slotEnd: String,
    val status: String,
    @SerialName("created_at")
    val createdAt: String? = null,
    val listing: LeadListingRefDto? = null,
    val buyer: LeadUserRefDto? = null,
)

/** Visit row — agent side (RLS `visits_party_read` + own-agent filter). */
data class AgentVisit(
    val id: String,
    val listingId: String,
    val slotStart: String,
    val slotEnd: String,
    val status: String,
    val buyerName: String?,
    val buyerPhone: String?,
    val address: String?,
    val priceRial: Long?,
)

fun VisitDto.toAgentVisit(): AgentVisit {
    val property = listing?.property
    val address = listOfNotNull(property?.city, property?.neighborhood)
        .takeIf { it.isNotEmpty() }
        ?.joinToString("، ")
    return AgentVisit(
        id = id,
        listingId = listingId,
        slotStart = slotStart,
        slotEnd = slotEnd,
        status = status,
        buyerName = buyer?.profiles?.displayName,
        buyerPhone = buyer?.phoneE164,
        address = address,
        priceRial = listing?.priceRial,
    )
}

@Serializable
data class VisitPatchDto(
    val status: String,
)

@Serializable
data class ProfileDto(
    val id: String? = null,
    @SerialName("display_name")
    val displayName: String? = null,
    val bio: String? = null,
)

data class AgentProfile(
    val displayName: String?,
    val bio: String?,
)

/**
 * Agent surface — dashboard, files, leads, requirement matching, visits,
 * assignment. See interface docs on [SupabaseAgentRepository].
 */
interface AgentRepository {
    /** True when the signed-in user holds the AGENT role; Unauthorized without session. */
    suspend fun isAgent(): AppResult<Boolean>

    suspend fun dashboard(): AppResult<AgentDashboard>

    /** Own ACTIVE + PAUSED files. */
    suspend fun myFiles(): AppResult<List<SellerListingItem>>

    suspend fun leads(): AppResult<List<AgentLead>>

    suspend fun leadMatches(leadId: String): AppResult<List<LeadMatch>>

    /** Active requirement for the lead — Empty when none exists yet. */
    suspend fun requirementForLead(leadId: String): AppResult<BuyerRequirement>

    suspend fun upsertRequirement(input: RequirementInput): AppResult<Unit>

    /** Recompute scores server-side; returns how many matches were written. */
    suspend fun refreshMatches(leadId: String): AppResult<Int>

    suspend fun updateLead(
        leadId: String,
        stage: String? = null,
        priority: Int? = null,
        notes: String? = null,
    ): AppResult<Unit>

    suspend fun visits(): AppResult<List<AgentVisit>>

    suspend fun setVisitStatus(visitId: String, status: String): AppResult<Unit>

    /** Assign/transfer (phone) or unassign (null); returns the new agent id. */
    suspend fun assignAgent(listingId: String, agentPhone: String?): AppResult<String?>

    suspend fun profile(): AppResult<AgentProfile>
}

private const val AGENT_LEAD_SELECT =
    "id,listing_id,source,stage,priority,notes,created_at," +
        "listing:listings(id,deal_type,price_rial,deposit_rial,rent_rial,status," +
        "property:properties(property_type,area_sqm,city,neighborhood))," +
        "buyer:users(id,phone_e164,profiles(display_name))"

private const val AGENT_VISIT_SELECT =
    "id,listing_id,slot_start,slot_end,status,created_at," +
        "listing:listings(id,deal_type,price_rial,deposit_rial,rent_rial,status," +
        "property:properties(property_type,area_sqm,city,neighborhood))," +
        "buyer:users(id,phone_e164,profiles(display_name))"

private const val AGENT_MATCH_SELECT =
    "id,listing_id,score,explanation," +
        "listing:listings(id,deal_type,price_rial,deposit_rial,rent_rial,status," +
        "property:properties(property_type,area_sqm,city,neighborhood))"

/**
 * Supabase-backed agent repository.
 *
 * Fails closed on missing config/session; list empties map to
 * [AppResult.Empty] so screens show honest empty states.
 */
@Singleton
class SupabaseAgentRepository @Inject constructor(
    private val config: AppConfig,
    private val api: MarketplaceApi,
    private val safeApi: SafeApi,
    private val session: SessionTokenProvider,
) : AgentRepository {

    override suspend fun isAgent(): AppResult<Boolean> {
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
                    role = "eq.AGENT",
                )
            }
        ) {
            is AppResult.Success -> AppResult.Success(result.data.orEmpty().isNotEmpty())
            is AppResult.Failure -> result
            AppResult.Empty -> AppResult.Success(false)
        }
    }

    override suspend fun dashboard(): AppResult<AgentDashboard> {
        val (_, key) = config.requireRest() ?: return marketplaceConfigError()
        session.currentUserId() ?: return AppResult.Failure(AppError.Unauthorized)
        session.accessToken()?.takeIf { it.isNotBlank() }
            ?: return AppResult.Failure(AppError.Unauthorized)
        return when (
            val result = safeApi.call {
                api.agentDashboardStats(apiKey = key)
            }
        ) {
            is AppResult.Success -> {
                val row = result.data ?: return AppResult.Failure(AppError.Serialization)
                AppResult.Success(
                    AgentDashboard(
                        filesActive = row.filesActive,
                        filesPaused = row.filesPaused,
                        leadsNew = row.leadsNew,
                        leadsInProgress = row.leadsInProgress,
                        visitsPending = row.visitsPending,
                        matchesToday = row.matchesToday,
                        requirementsActive = row.requirementsActive,
                    ),
                )
            }
            is AppResult.Failure -> result
            AppResult.Empty -> AppResult.Failure(AppError.Serialization)
        }
    }

    override suspend fun myFiles(): AppResult<List<SellerListingItem>> {
        val (base, key) = config.requireRest() ?: return marketplaceConfigError()
        val uid = session.currentUserId() ?: return AppResult.Failure(AppError.Unauthorized)
        val auth = session.accessToken()?.takeIf { it.isNotBlank() }
            ?: return AppResult.Failure(AppError.Unauthorized)
        return when (
            val result = safeApi.call {
                api.fetchAgentListings(
                    url = "$base/rest/v1/listings",
                    apiKey = key,
                    authorization = "Bearer $auth",
                    select = SELLER_LISTING_SELECT,
                    agentId = "eq.$uid",
                    status = "in.(ACTIVE,PAUSED)",
                    order = "created_at.desc",
                )
            }
        ) {
            is AppResult.Success -> {
                val rows = result.data.orEmpty().map { it.toSellerItem() }
                if (rows.isEmpty()) AppResult.Empty else AppResult.Success(rows)
            }
            is AppResult.Failure -> result
            AppResult.Empty -> AppResult.Empty
        }
    }

    override suspend fun leads(): AppResult<List<AgentLead>> {
        val (base, key) = config.requireRest() ?: return marketplaceConfigError()
        val uid = session.currentUserId() ?: return AppResult.Failure(AppError.Unauthorized)
        val auth = session.accessToken()?.takeIf { it.isNotBlank() }
            ?: return AppResult.Failure(AppError.Unauthorized)
        return when (
            val result = safeApi.call {
                api.fetchAgentLeads(
                    url = "$base/rest/v1/leads",
                    apiKey = key,
                    authorization = "Bearer $auth",
                    select = AGENT_LEAD_SELECT,
                    agentId = "eq.$uid",
                    order = "created_at.desc",
                )
            }
        ) {
            is AppResult.Success -> {
                val rows = result.data.orEmpty().map { it.toAgentLead() }
                if (rows.isEmpty()) AppResult.Empty else AppResult.Success(rows)
            }
            is AppResult.Failure -> result
            AppResult.Empty -> AppResult.Empty
        }
    }

    override suspend fun leadMatches(leadId: String): AppResult<List<LeadMatch>> {
        val (base, key) = config.requireRest() ?: return marketplaceConfigError()
        val auth = session.accessToken()?.takeIf { it.isNotBlank() }
            ?: return AppResult.Failure(AppError.Unauthorized)
        return when (
            val result = safeApi.call {
                api.fetchLeadMatches(
                    url = "$base/rest/v1/matches",
                    apiKey = key,
                    authorization = "Bearer $auth",
                    select = AGENT_MATCH_SELECT,
                    requirement = "buyer_requirements!inner(lead_id)",
                    leadId = "eq.$leadId",
                    order = "score.desc",
                )
            }
        ) {
            is AppResult.Success -> {
                val rows = result.data.orEmpty().mapNotNull { it.toLeadMatch() }
                if (rows.isEmpty()) AppResult.Empty else AppResult.Success(rows)
            }
            is AppResult.Failure -> result
            AppResult.Empty -> AppResult.Empty
        }
    }

    override suspend fun requirementForLead(leadId: String): AppResult<BuyerRequirement> {
        val (base, key) = config.requireRest() ?: return marketplaceConfigError()
        val auth = session.accessToken()?.takeIf { it.isNotBlank() }
            ?: return AppResult.Failure(AppError.Unauthorized)
        return when (
            val result = safeApi.call {
                api.fetchRequirement(
                    url = "$base/rest/v1/buyer_requirements",
                    apiKey = key,
                    authorization = "Bearer $auth",
                    select = "id,lead_id,deal_type,budget_min_rial,budget_max_rial," +
                        "area_min,area_max,bedrooms,cities,features",
                    leadId = "eq.$leadId",
                    isActive = "eq.true",
                    limit = 1,
                )
            }
        ) {
            is AppResult.Success -> {
                val row = result.data?.firstOrNull()
                    ?: return AppResult.Empty
                AppResult.Success(row.toRequirement())
            }
            is AppResult.Failure -> result
            AppResult.Empty -> AppResult.Empty
        }
    }

    override suspend fun upsertRequirement(input: RequirementInput): AppResult<Unit> {
        val (_, key) = config.requireRest() ?: return marketplaceConfigError()
        session.currentUserId() ?: return AppResult.Failure(AppError.Unauthorized)
        session.accessToken()?.takeIf { it.isNotBlank() }
            ?: return AppResult.Failure(AppError.Unauthorized)
        return when (
            val result = safeApi.call {
                api.agentUpsertRequirement(
                    apiKey = key,
                    body = AgentUpsertRequirementRequestDto(
                        leadId = input.leadId,
                        dealType = input.dealType.name,
                        budgetMinRial = input.budgetMinRial,
                        budgetMaxRial = input.budgetMaxRial,
                        areaMin = input.areaMin,
                        areaMax = input.areaMax,
                        bedrooms = input.bedrooms,
                        cities = input.cities,
                        features = input.features,
                    ),
                )
            }
        ) {
            is AppResult.Success, AppResult.Empty -> AppResult.Success(Unit)
            is AppResult.Failure -> result
        }
    }

    override suspend fun refreshMatches(leadId: String): AppResult<Int> {
        val (_, key) = config.requireRest() ?: return marketplaceConfigError()
        session.currentUserId() ?: return AppResult.Failure(AppError.Unauthorized)
        session.accessToken()?.takeIf { it.isNotBlank() }
            ?: return AppResult.Failure(AppError.Unauthorized)
        return when (
            val result = safeApi.call {
                api.refreshLeadMatches(
                    apiKey = key,
                    body = RefreshLeadMatchesRequestDto(leadId = leadId),
                )
            }
        ) {
            is AppResult.Success -> AppResult.Success(result.data ?: 0)
            is AppResult.Failure -> result
            AppResult.Empty -> AppResult.Success(0)
        }
    }

    override suspend fun updateLead(
        leadId: String,
        stage: String?,
        priority: Int?,
        notes: String?,
    ): AppResult<Unit> {
        val (_, key) = config.requireRest() ?: return marketplaceConfigError()
        val auth = session.accessToken()?.takeIf { it.isNotBlank() }
            ?: return AppResult.Failure(AppError.Unauthorized)
        if (stage == null && priority == null && notes == null) {
            return AppResult.Success(Unit)
        }
        return when (
            val result = safeApi.call {
                api.updateLead(
                    id = "eq.$leadId",
                    apiKey = key,
                    authorization = "Bearer $auth",
                    body = LeadPatchDto(stage = stage, priority = priority, notes = notes),
                )
            }
        ) {
            is AppResult.Success, AppResult.Empty -> AppResult.Success(Unit)
            is AppResult.Failure -> result
        }
    }

    override suspend fun visits(): AppResult<List<AgentVisit>> {
        val (base, key) = config.requireRest() ?: return marketplaceConfigError()
        val uid = session.currentUserId() ?: return AppResult.Failure(AppError.Unauthorized)
        val auth = session.accessToken()?.takeIf { it.isNotBlank() }
            ?: return AppResult.Failure(AppError.Unauthorized)
        return when (
            val result = safeApi.call {
                api.fetchAgentVisits(
                    url = "$base/rest/v1/visits",
                    apiKey = key,
                    authorization = "Bearer $auth",
                    select = AGENT_VISIT_SELECT,
                    agentId = "eq.$uid",
                    order = "slot_start.asc",
                )
            }
        ) {
            is AppResult.Success -> {
                val rows = result.data.orEmpty().map { it.toAgentVisit() }
                if (rows.isEmpty()) AppResult.Empty else AppResult.Success(rows)
            }
            is AppResult.Failure -> result
            AppResult.Empty -> AppResult.Empty
        }
    }

    override suspend fun setVisitStatus(visitId: String, status: String): AppResult<Unit> {
        val (_, key) = config.requireRest() ?: return marketplaceConfigError()
        val auth = session.accessToken()?.takeIf { it.isNotBlank() }
            ?: return AppResult.Failure(AppError.Unauthorized)
        return when (
            val result = safeApi.call {
                api.updateVisitStatus(
                    id = "eq.$visitId",
                    apiKey = key,
                    authorization = "Bearer $auth",
                    body = VisitPatchDto(status = status),
                )
            }
        ) {
            is AppResult.Success, AppResult.Empty -> AppResult.Success(Unit)
            is AppResult.Failure -> result
        }
    }

    override suspend fun assignAgent(listingId: String, agentPhone: String?): AppResult<String?> {
        val (_, key) = config.requireRest() ?: return marketplaceConfigError()
        session.currentUserId() ?: return AppResult.Failure(AppError.Unauthorized)
        session.accessToken()?.takeIf { it.isNotBlank() }
            ?: return AppResult.Failure(AppError.Unauthorized)
        return when (
            val result = safeApi.call {
                api.assignAgent(
                    apiKey = key,
                    body = AssignAgentRequestDto(
                        listingId = listingId,
                        agentPhone = agentPhone,
                    ),
                )
            }
        ) {
            is AppResult.Success -> AppResult.Success(result.data?.agentId)
            is AppResult.Failure -> result
            AppResult.Empty -> AppResult.Success(null)
        }
    }

    override suspend fun profile(): AppResult<AgentProfile> {
        val (base, key) = config.requireRest() ?: return marketplaceConfigError()
        val uid = session.currentUserId() ?: return AppResult.Failure(AppError.Unauthorized)
        val auth = session.accessToken()?.takeIf { it.isNotBlank() }
            ?: return AppResult.Failure(AppError.Unauthorized)
        return when (
            val result = safeApi.call {
                api.fetchProfile(
                    url = "$base/rest/v1/profiles",
                    apiKey = key,
                    authorization = "Bearer $auth",
                    select = "id,display_name,bio",
                    id = "eq.$uid",
                )
            }
        ) {
            is AppResult.Success -> {
                val row = result.data?.firstOrNull()
                if (row == null) {
                    AppResult.Success(AgentProfile(displayName = null, bio = null))
                } else {
                    AppResult.Success(AgentProfile(displayName = row.displayName, bio = row.bio))
                }
            }
            is AppResult.Failure -> result
            AppResult.Empty -> AppResult.Success(AgentProfile(displayName = null, bio = null))
        }
    }

}
