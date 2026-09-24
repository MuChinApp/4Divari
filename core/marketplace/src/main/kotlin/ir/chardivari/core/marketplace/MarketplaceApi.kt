package ir.chardivari.core.marketplace

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

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
)
