package ir.chardivari.feature.agent

import ir.chardivari.core.analytics.AnalyticsEvent
import ir.chardivari.core.analytics.AnalyticsTracker
import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.marketplace.AgentDashboard
import ir.chardivari.core.marketplace.AgentLead
import ir.chardivari.core.marketplace.AgentProfile
import ir.chardivari.core.marketplace.AgentRepository
import ir.chardivari.core.marketplace.AgentVisit
import ir.chardivari.core.marketplace.BuyerRequirement
import ir.chardivari.core.marketplace.DealType
import ir.chardivari.core.marketplace.LeadMatch
import ir.chardivari.core.marketplace.RequirementInput
import ir.chardivari.core.marketplace.SellerListingItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

internal class RecordingTracker : AnalyticsTracker {
    val recorded = mutableListOf<AnalyticsEvent>()
    private val flow = MutableSharedFlow<AnalyticsEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<AnalyticsEvent> = flow
    override fun track(event: AnalyticsEvent) {
        recorded += event
    }
}

internal class FakeAgentRepository : AgentRepository {
    var isAgentResult: AppResult<Boolean> = AppResult.Success(true)
    var profileResult: AppResult<AgentProfile> =
        AppResult.Success(AgentProfile(displayName = "سارا رضایی", bio = null))
    var dashboardResult: AppResult<AgentDashboard> = AppResult.Success(
        AgentDashboard(
            filesActive = 3,
            filesPaused = 1,
            leadsNew = 2,
            leadsInProgress = 4,
            visitsPending = 1,
            matchesToday = 7,
            requirementsActive = 5,
        ),
    )
    var filesResult: AppResult<List<SellerListingItem>> = AppResult.Empty
    var leadsResult: AppResult<List<AgentLead>> = AppResult.Empty
    var matchesResult: AppResult<List<LeadMatch>> = AppResult.Empty
    var requirementResult: AppResult<BuyerRequirement> = AppResult.Empty
    var upsertResult: AppResult<Unit> = AppResult.Success(Unit)
    var refreshResult: AppResult<Int> = AppResult.Success(2)
    var updateLeadResult: AppResult<Unit> = AppResult.Success(Unit)
    var visitsResult: AppResult<List<AgentVisit>> = AppResult.Empty
    var visitStatusResult: AppResult<Unit> = AppResult.Success(Unit)

    val upsertCalls = mutableListOf<RequirementInput>()
    val refreshCalls = mutableListOf<String>()
    val updateLeadCalls = mutableListOf<Triple<String, String?, Int?>>()
    val visitStatusCalls = mutableListOf<Pair<String, String>>()

    override suspend fun isAgent(): AppResult<Boolean> = isAgentResult

    override suspend fun dashboard(): AppResult<AgentDashboard> = dashboardResult

    override suspend fun myFiles(): AppResult<List<SellerListingItem>> = filesResult

    override suspend fun leads(): AppResult<List<AgentLead>> = leadsResult

    override suspend fun leadMatches(leadId: String): AppResult<List<LeadMatch>> = matchesResult

    override suspend fun requirementForLead(leadId: String): AppResult<BuyerRequirement> =
        requirementResult

    override suspend fun upsertRequirement(input: RequirementInput): AppResult<Unit> {
        upsertCalls += input
        return upsertResult
    }

    override suspend fun refreshMatches(leadId: String): AppResult<Int> {
        refreshCalls += leadId
        return refreshResult
    }

    override suspend fun updateLead(
        leadId: String,
        stage: String?,
        priority: Int?,
        notes: String?,
    ): AppResult<Unit> {
        updateLeadCalls += Triple(leadId, stage, priority)
        return updateLeadResult
    }

    override suspend fun visits(): AppResult<List<AgentVisit>> = visitsResult

    override suspend fun setVisitStatus(visitId: String, status: String): AppResult<Unit> {
        visitStatusCalls += visitId to status
        return visitStatusResult
    }

    override suspend fun assignAgent(
        listingId: String,
        agentPhone: String?,
    ): AppResult<String?> = AppResult.Success(null)

    override suspend fun profile(): AppResult<AgentProfile> = profileResult
}

internal fun agentLead(
    id: String = "L1",
    stage: String = "NEW",
    priority: Int = 3,
    dealType: DealType? = DealType.SALE,
): AgentLead = AgentLead(
    id = id,
    listingId = "listing-$id",
    stage = stage,
    priority = priority,
    source = "contact",
    notes = null,
    createdAt = "2026-09-24T10:00:00+00:00",
    buyerName = "علی",
    buyerPhone = "+989121112233",
    address = "تهران، ولنجک",
    priceRial = 5_000_000_000L,
    dealType = dealType,
    listingStatus = "ACTIVE",
)

internal fun agentVisit(
    id: String = "V1",
    status: String = "REQUESTED",
): AgentVisit = AgentVisit(
    id = id,
    listingId = "listing-1",
    slotStart = "2026-09-26T10:00:00+00:00",
    slotEnd = "2026-09-26T11:00:00+00:00",
    status = status,
    buyerName = "علی",
    buyerPhone = "+989121112233",
    address = "تهران، ولنجک",
    priceRial = 5_000_000_000L,
)

internal val unauthorizedRepo: AppResult<Nothing> = AppResult.Failure(AppError.Unauthorized)
