package ir.chardivari.core.marketplace

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Body for zero-argument PostgREST RPCs — PostgREST rejects bodyless POSTs
 * (PostgREST/postgrest#777), so `{}` is always sent.
 */
@Serializable
data object EmptyRpcRequest

/**
 * Supabase PostgREST surface for marketplace tables.
 *
 * Absolute [Url] values keep base-url coupling out of Retrofit (same pattern
 * as AuthApi). Every call requires the `apikey` header; Bearer is optional
 * (public ACTIVE listings) and supplied by core:network when a session exists.
 */
interface MarketplaceApi {

    @GET
    suspend fun fetchListings(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Header("Accept") accept: String,
    ): Response<List<ListingDto>>

    @GET
    suspend fun fetchListingById(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Header("Accept") accept: String,
    ): Response<List<ListingDto>>

    @GET
    suspend fun fetchFavorites(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Query("select") select: String,
        @Query("user_id") userId: String,
        @Query("order") order: String,
    ): Response<List<FavoriteDto>>

    @Headers("Prefer: return=minimal")
    @POST("favorites")
    suspend fun insertFavorite(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: FavoriteWriteDto,
    ): Response<Void>

    @DELETE
    suspend fun deleteFavorite(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
    ): Response<Void>

    @GET
    suspend fun fetchSavedSearches(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("select") select: String,
        @Query("user_id") userId: String,
        @Query("order") order: String,
    ): Response<List<SavedSearchDto>>

    @POST("saved_searches")
    suspend fun insertSavedSearch(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: SavedSearchWriteDto,
    ): Response<SavedSearchDto>

    @DELETE
    suspend fun deleteSavedSearch(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
    ): Response<Void>

    @POST("rpc/listing_contact")
    suspend fun listingContact(
        @Header("apikey") apiKey: String,
        @Body body: ListingContactRequestDto,
    ): Response<List<ListingContactDto>>

    // ---- Seller wizard (SECURITY DEFINER RPCs — 00093_seller_wizard.sql) ----

    @POST("rpc/seller_create_draft")
    suspend fun sellerCreateDraft(
        @Header("apikey") apiKey: String,
        @Body body: SellerCreateDraftRequestDto,
    ): Response<SellerDraftCreatedDto>

    @POST("rpc/seller_set_status")
    suspend fun sellerSetStatus(
        @Header("apikey") apiKey: String,
        @Body body: SellerSetStatusRequestDto,
    ): Response<String>

    @GET
    suspend fun fetchSellerListings(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("select") select: String,
        @Query("seller_id") sellerId: String,
        @Query("order") order: String,
    ): Response<List<SellerListingDto>>

    @Headers("Prefer: return=minimal")
    @POST("property_media")
    suspend fun insertPropertyMedia(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: PropertyMediaWriteDto,
    ): Response<Void>

    /**
     * Raw binary upload to `{supabaseUrl}/storage/v1/object/...`.
     * Content-Type comes from the RequestBody media type (OkHttp bridge).
     */
    @POST
    suspend fun uploadStorageObject(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Header("x-upsert") upsert: String = "true",
        @Body body: okhttp3.RequestBody,
    ): Response<okhttp3.ResponseBody>

    // ---- Agent tooling (Phase 5 — 00094_agent_tooling.sql) ----

    /** Own roles only — `user_roles_self_read` RLS; used as the agent gate. */
    @GET
    suspend fun fetchMyRoles(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("select") select: String,
        @Query("user_id") userId: String,
        @Query("role") role: String,
    ): Response<List<UserRoleDto>>

    @POST("rpc/agent_dashboard_stats")
    suspend fun agentDashboardStats(
        @Header("apikey") apiKey: String,
        @Body body: EmptyRpcRequest,
    ): Response<AgentDashboardDto>

    /** Agent's own files (ACTIVE + PAUSED). Same row shape as the seller manage screen. */
    @GET
    suspend fun fetchAgentListings(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("select") select: String,
        @Query("agent_id") agentId: String,
        @Query("status") status: String,
        @Query("order") order: String,
    ): Response<List<SellerListingDto>>

    @GET
    suspend fun fetchAgentLeads(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("select") select: String,
        @Query("agent_id") agentId: String,
        @Query("order") order: String,
    ): Response<List<LeadDto>>

    @HTTP(method = "PATCH", path = "leads", hasBody = true)
    @Headers("Prefer: return=minimal")
    suspend fun updateLead(
        @Query("id") id: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: LeadPatchDto,
    ): Response<Void>

    /**
     * Active requirement for one lead — Empty when none exists yet.
     */
    @GET
    suspend fun fetchRequirement(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("select") select: String,
        @Query("lead_id") leadId: String,
        @Query("is_active") isActive: String,
        @Query("limit") limit: Int,
    ): Response<List<BuyerRequirementDto>>

    /**
     * Matches for one lead's requirement (inner-joined embed filter —
     * `matches_requirement_owner_read` lets the listing agent see them).
     */
    @GET
    suspend fun fetchLeadMatches(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("select") select: String,
        @Query("requirement") requirement: String,
        @Query("requirement.lead_id") leadId: String,
        @Query("order") order: String,
    ): Response<List<LeadMatchDto>>

    @POST("rpc/agent_upsert_requirement")
    suspend fun agentUpsertRequirement(
        @Header("apikey") apiKey: String,
        @Body body: AgentUpsertRequirementRequestDto,
    ): Response<String>

    @POST("rpc/refresh_lead_matches")
    suspend fun refreshLeadMatches(
        @Header("apikey") apiKey: String,
        @Body body: RefreshLeadMatchesRequestDto,
    ): Response<Int>

    @POST("rpc/assign_agent")
    suspend fun assignAgent(
        @Header("apikey") apiKey: String,
        @Body body: AssignAgentRequestDto,
    ): Response<AssignAgentResultDto>

    @GET
    suspend fun fetchAgentVisits(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("select") select: String,
        @Query("agent_id") agentId: String,
        @Query("order") order: String,
    ): Response<List<VisitDto>>

    @HTTP(method = "PATCH", path = "visits", hasBody = true)
    @Headers("Prefer: return=minimal")
    suspend fun updateVisitStatus(
        @Query("id") id: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: VisitPatchDto,
    ): Response<Void>

    /** Agent profile row (public read under RLS). */
    @GET
    suspend fun fetchProfile(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("select") select: String,
        @Query("id") id: String,
    ): Response<List<ProfileDto>>

    // ---- Communication (Phase 6 — 00095_communication_tooling.sql) ----

    @POST("rpc/start_conversation")
    suspend fun startConversation(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: StartConversationRequestDto,
    ): Response<String>

    @GET("rpc/my_conversations")
    suspend fun myConversations(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
    ): Response<List<ConversationRowDto>>

    @POST("rpc/mark_conversation_read")
    suspend fun markConversationRead(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: MarkConversationReadRequestDto,
    ): Response<Void>

    @GET
    suspend fun fetchMessages(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("conversation_id") conversationId: String,
        @Query("order") order: String,
    ): Response<List<MessageDto>>

    @Headers("Prefer: return=representation")
    @POST("messages")
    suspend fun insertMessage(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: MessageWriteDto,
    ): Response<List<MessageDto>>

    @Headers("Prefer: return=representation")
    @POST("visits")
    suspend fun insertVisit(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: VisitWriteDto,
    ): Response<List<VisitWriteRowDto>>

    @GET
    suspend fun fetchMyVisits(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("select") select: String,
        @Query("buyer_id") buyerId: String,
        @Query("order") order: String,
    ): Response<List<MyVisitDto>>

    @HTTP(method = "PATCH", path = "visits", hasBody = true)
    @Headers("Prefer: return=minimal")
    suspend fun updateMyVisit(
        @Query("id") id: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: VisitPatchDto,
    ): Response<Void>

    @GET
    suspend fun fetchNotifications(
        @Url url: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("select") select: String,
        @Query("user_id") userId: String,
        @Query("order") order: String,
        @Query("limit") limit: Int,
    ): Response<List<NotificationDto>>

    @POST("rpc/mark_notification_read")
    suspend fun markNotificationRead(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Body body: MarkNotificationReadRequestDto,
    ): Response<Void>
}

@Serializable
data class FavoriteDto(
    @SerialName("listing_id")
    val listingId: String,
    @SerialName("created_at")
    val createdAt: String? = null,
)

@Serializable
data class FavoriteWriteDto(
    @SerialName("listing_id")
    val listingId: String,
    @SerialName("user_id")
    val userId: String,
)

@Serializable
data class SavedSearchDto(
    val id: String,
    val name: String? = null,
    /** jsonb — opaque to the client beyond rehydration into [SearchFilters]. */
    val query: kotlinx.serialization.json.JsonObject? = null,
    @SerialName("notify_new_listing")
    val notifyNewListing: Boolean = true,
    @SerialName("notify_price_drop")
    val notifyPriceDrop: Boolean = true,
    @SerialName("created_at")
    val createdAt: String? = null,
)

@Serializable
data class SavedSearchWriteDto(
    val name: String? = null,
    val query: kotlinx.serialization.json.JsonObject,
    @SerialName("user_id")
    val userId: String,
    @SerialName("notify_new_listing")
    val notifyNewListing: Boolean = true,
    @SerialName("notify_price_drop")
    val notifyPriceDrop: Boolean = true,
)

@Serializable
data class ListingContactRequestDto(
    @SerialName("p_listing_id")
    val listingId: String,
)

@Serializable
data class ListingContactDto(
    @SerialName("phone_e164")
    val phoneE164: String? = null,
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("party_role")
    val partyRole: String? = null,
    /**
     * True when this authenticated contact created a new lead for the
     * listing's agent (00094 `listing_contact`). Default false keeps older
     * server builds source-compatible; false is also the honest default.
     */
    @SerialName("lead_created")
    val leadCreated: Boolean = false,
)
