package ir.chardivari.core.marketplace

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * Phase 5 payload contracts — parsing + null-omission rules that RLS/RPCs
 * depend on. Uses the same Json flags as NetworkModule.
 */
class AgentPayloadTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = false
        encodeDefaults = true
    }

    @Test
    fun `listing contact parses lead_created true`() {
        val dto = json.decodeFromString<ListingContactDto>(
            """{"phone_e164":"+989121112233","display_name":"علی","party_role":"buyer","lead_created":true}""",
        )
        assertThat(dto.leadCreated).isTrue()
        assertThat(dto.phoneE164).isEqualTo("+989121112233")
    }

    @Test
    fun `listing contact defaults lead_created to false when absent`() {
        val dto = json.decodeFromString<ListingContactDto>(
            """{"phone_e164":"+989121112233"}""",
        )
        assertThat(dto.leadCreated).isFalse()
    }

    @Test
    fun `lead patch omits null fields so columns are never cleared`() {
        val encoded = json.encodeToString(LeadPatchDto.serializer(), LeadPatchDto())
        assertThat(encoded).isEqualTo("{}")
    }

    @Test
    fun `lead patch includes only provided fields`() {
        val encoded = json.encodeToString(
            LeadPatchDto.serializer(),
            LeadPatchDto(stage = "QUALIFIED"),
        )
        assertThat(encoded).contains("QUALIFIED")
        assertThat(encoded).doesNotContain("priority")
        assertThat(encoded).doesNotContain("notes")
    }

    @Test
    fun `assign agent request keeps null phone for unassign`() {
        val encoded = json.encodeToString(
            AssignAgentRequestDto.serializer(),
            AssignAgentRequestDto(listingId = "listing-1", agentPhone = null),
        )
        assertThat(encoded).contains("listing-1")
        // explicitNulls=false drops the null → RPC receives its default (null) → unassign.
        assertThat(encoded).doesNotContain("p_agent_phone")
    }

    @Test
    fun `assign agent result tolerates null agent id`() {
        val dto = json.decodeFromString<AssignAgentResultDto>("""{"agent_id":null}""")
        assertThat(dto.agentId).isNull()
    }

    @Test
    fun `dashboard maps jsonb snake_case counters`() {
        val dto = json.decodeFromString<AgentDashboardDto>(
            """{"files_active":3,"files_paused":1,"leads_new":2,"leads_in_progress":4,"visits_pending":1,"matches_today":7,"requirements_active":5}""",
        )
        assertThat(dto.filesActive).isEqualTo(3)
        assertThat(dto.leadsInProgress).isEqualTo(4)
        assertThat(dto.matchesToday).isEqualTo(7)
    }

    @Test
    fun `lead row parses embeds into domain address`() {
        val dto = json.decodeFromString<LeadDto>(
            """
            {
              "id": "lead-1",
              "listing_id": "listing-1",
              "stage": "NEW",
              "priority": 4,
              "listing": {
                "id": "listing-1",
                "deal_type": "SALE",
                "price_rial": 5000000000,
                "status": "ACTIVE",
                "property": {"city": "تهران", "neighborhood": "ولنجک", "area_sqm": 90}
              },
              "buyer": {"phone_e164": "+989121112233", "profiles": {"display_name": "علی"}}
            }
            """.trimIndent(),
        )
        val lead = dto.toAgentLead()
        assertThat(lead.address).isEqualTo("تهران، ولنجک")
        assertThat(lead.buyerPhone).isEqualTo("+989121112233")
        assertThat(lead.buyerName).isEqualTo("علی")
        assertThat(lead.priceRial).isEqualTo(5_000_000_000L)
        assertThat(lead.stage).isEqualTo("NEW")
    }

    @Test
    fun `match row parses score and explanation`() {
        val dto = json.decodeFromString<LeadMatchDto>(
            """
            {
              "listing_id": "listing-1",
              "score": 87.50,
              "explanation": {"city": true, "budget": "exact", "area": "tolerant", "bedrooms": true}
            }
            """.trimIndent(),
        )
        val match = dto.toLeadMatch()
        assertThat(match).isNotNull()
        assertThat(match!!.score).isWithin(0.01).of(87.5)
        assertThat(match.explanation?.budget).isEqualTo("exact")
        assertThat(match.explanation?.city).isTrue()
    }

    @Test
    fun `requirement request serializes p_ prefixed rpc args`() {
        val encoded = json.encodeToString(
            AgentUpsertRequirementRequestDto.serializer(),
            AgentUpsertRequirementRequestDto(
                leadId = "lead-1",
                dealType = "SALE",
                budgetMinRial = 4_000_000_000L,
            ),
        )
        assertThat(encoded).contains("\"p_lead_id\":\"lead-1\"")
        assertThat(encoded).contains("\"p_deal_type\":\"SALE\"")
        assertThat(encoded).contains("\"p_budget_min_rial\":4000000000")
    }
}
