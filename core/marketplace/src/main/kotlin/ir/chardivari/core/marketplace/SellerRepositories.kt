package ir.chardivari.core.marketplace

import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.environment.AppConfig
import ir.chardivari.core.network.SafeApi
import ir.chardivari.core.network.SessionTokenProvider
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Lifecycle actions a seller can request — server enforces the transition map. */
enum class SellerAction(val toStatus: String) {
    PUBLISH("ACTIVE"),
    PAUSE("PAUSED"),
    RESUME("ACTIVE"),
    MARK_SOLD("SOLD"),
    ;
}

/**
 * Seller writes — all through SECURITY DEFINER RPCs + Storage API.
 * Never writes `properties` directly (admin-only RLS by design).
 */
interface SellerRepository {
    /** Create property + DRAFT listing atomically; returns created ids. */
    suspend fun createDraft(
        property: JsonObject,
        listing: JsonObject,
    ): AppResult<SellerDraftCreated>

    /** Request a lifecycle transition; server re-validates ownership + map. */
    suspend fun setStatus(listingId: String, action: SellerAction): AppResult<Unit>

    /** Seller's own listings for the manage screen. */
    suspend fun myListings(): AppResult<List<SellerListingItem>>

    /**
     * Upload one media file to the `property-media` bucket and record
     * its `property_media` row (party-checked under RLS).
     */
    suspend fun uploadMedia(
        propertyId: String,
        mimeType: String,
        bytes: ByteArray,
        mediaType: String,
        sortOrder: Int,
        isCover: Boolean,
    ): AppResult<Unit>
}

data class SellerDraftCreated(val listingId: String, val propertyId: String)

private val SELLER_LISTING_SELECT =
    "id,property_id,deal_type,price_rial,deposit_rial,rent_rial,status," +
        "published_at,created_at," +
        "property:properties(id,property_type,area_sqm,city,neighborhood," +
        "media:property_media(id,storage_path,media_type,sort_order,is_cover))"

/**
 * Supabase-backed implementation.
 *
 * Fail-closed on missing config (never invents data); missing session maps to
 * [AppError.Unauthorized] so the UI can prompt login instead of faking state.
 */
@Singleton
class SupabaseSellerRepository @Inject constructor(
    private val config: AppConfig,
    private val api: MarketplaceApi,
    private val safeApi: SafeApi,
    private val session: SessionTokenProvider,
) : SellerRepository {

    override suspend fun createDraft(
        property: JsonObject,
        listing: JsonObject,
    ): AppResult<SellerDraftCreated> {
        val (_, key) = config.requireRest() ?: return marketplaceConfigError()
        session.accessToken()?.takeIf { it.isNotBlank() }
            ?: return AppResult.Failure(AppError.Unauthorized)
        return when (
            val result = safeApi.call {
                api.sellerCreateDraft(
                    apiKey = key,
                    body = SellerCreateDraftRequestDto(property = property, listing = listing),
                )
            }
        ) {
            is AppResult.Success -> {
                val row = result.data
                if (row == null) {
                    AppResult.Failure(AppError.Serialization)
                } else {
                    AppResult.Success(
                        SellerDraftCreated(
                            listingId = row.listingId,
                            propertyId = row.propertyId,
                        ),
                    )
                }
            }
            is AppResult.Failure -> result
            AppResult.Empty -> AppResult.Failure(AppError.Serialization)
        }
    }

    override suspend fun setStatus(listingId: String, action: SellerAction): AppResult<Unit> {
        val (_, key) = config.requireRest() ?: return marketplaceConfigError()
        session.accessToken()?.takeIf { it.isNotBlank() }
            ?: return AppResult.Failure(AppError.Unauthorized)
        return when (
            val result = safeApi.call {
                api.sellerSetStatus(
                    apiKey = key,
                    body = SellerSetStatusRequestDto(
                        listingId = listingId,
                        to = action.toStatus,
                    ),
                )
            }
        ) {
            is AppResult.Success, AppResult.Empty -> AppResult.Success(Unit)
            is AppResult.Failure -> result
        }
    }

    override suspend fun myListings(): AppResult<List<SellerListingItem>> {
        val (base, key) = config.requireRest() ?: return marketplaceConfigError()
        val userId = session.currentUserId()
            ?: return AppResult.Failure(AppError.Unauthorized)
        val authHeader = session.accessToken()
            ?.takeIf { it.isNotBlank() }
            ?.let { "Bearer $it" }
            ?: return AppResult.Failure(AppError.Unauthorized)

        return when (
            val result = safeApi.call {
                api.fetchSellerListings(
                    url = "$base/rest/v1/listings",
                    apiKey = key,
                    authorization = authHeader,
                    select = SELLER_LISTING_SELECT,
                    sellerId = userId,
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

    override suspend fun uploadMedia(
        propertyId: String,
        mimeType: String,
        bytes: ByteArray,
        mediaType: String,
        sortOrder: Int,
        isCover: Boolean,
    ): AppResult<Unit> {
        val (base, key) = config.requireRest() ?: return marketplaceConfigError()
        val authHeader = session.accessToken()
            ?.takeIf { it.isNotBlank() }
            ?.let { "Bearer $it" }
            ?: return AppResult.Failure(AppError.Unauthorized)
        if (bytes.isEmpty()) {
            return AppResult.Failure(AppError.Validation())
        }
        if (bytes.size > MAX_MEDIA_BYTES) {
            return AppResult.Failure(
                AppError.Client(code = 413, serverMessage = "file too large"),
            )
        }

        val extension = extensionFor(mimeType)
            ?: return AppResult.Failure(AppError.Validation())
        val storagePath = "$propertyId/${UUID.randomUUID()}.$extension"

        // 1) Bytes to Storage (bucket enforces 50 MB + MIME allow-list).
        val uploadUrl = "$base/storage/v1/object/property-media/$storagePath"
        val body = bytes.toRequestBody(mimeType.toMediaType())
        when (
            val upload = safeApi.call {
                api.uploadStorageObject(
                    url = uploadUrl,
                    apiKey = key,
                    authorization = authHeader,
                    body = body,
                )
            }
        ) {
            is AppResult.Failure -> return upload
            else -> Unit
        }

        // 2) Metadata row — party-checked by property_media_party_insert RLS.
        return when (
            val insert = safeApi.call {
                api.insertPropertyMedia(
                    apiKey = key,
                    authorization = authHeader,
                    body = PropertyMediaWriteDto(
                        propertyId = propertyId,
                        storagePath = storagePath,
                        mediaType = mediaType,
                        mimeType = mimeType,
                        byteSize = bytes.size,
                        sortOrder = sortOrder,
                        isCover = isCover,
                    ),
                )
            }
        ) {
            is AppResult.Success, AppResult.Empty -> AppResult.Success(Unit)
            is AppResult.Failure -> insert
        }
    }

    private fun extensionFor(mimeType: String): String? = when (mimeType) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "application/pdf" -> "pdf"
        "video/mp4" -> "mp4"
        else -> null
    }

    private companion object {
        const val MAX_MEDIA_BYTES = 52_428_800
    }
}

/** Build the jsonb payload for `seller_create_draft` — p_property. */
fun buildSellerPropertyPayload(
    propertyType: String,
    areaSqm: Int,
    bedrooms: Int,
    province: String,
    city: String,
    neighborhood: String,
    floor: Int?,
    totalFloors: Int?,
    buildYear: Int?,
    hasElevator: Boolean,
    hasParking: Boolean,
    hasStorage: Boolean,
    hasBalcony: Boolean,
    description: String?,
): JsonObject = JsonObject(
    buildMap {
        put("property_type", JsonPrimitive(propertyType))
        put("area_sqm", JsonPrimitive(areaSqm))
        put("bedrooms", JsonPrimitive(bedrooms))
        put("province", JsonPrimitive(province))
        put("city", JsonPrimitive(city))
        if (neighborhood.isNotBlank()) put("neighborhood", JsonPrimitive(neighborhood))
        floor?.let { put("floor", JsonPrimitive(it)) }
        totalFloors?.let { put("total_floors", JsonPrimitive(it)) }
        buildYear?.let { put("build_year", JsonPrimitive(it)) }
        put("has_elevator", JsonPrimitive(hasElevator))
        put("has_parking", JsonPrimitive(hasParking))
        put("has_storage", JsonPrimitive(hasStorage))
        put("has_balcony", JsonPrimitive(hasBalcony))
        description?.takeIf { it.isNotBlank() }?.let { put("description", JsonPrimitive(it)) }
    },
)

/** Build the jsonb payload for `seller_create_draft` — p_listing. */
fun buildSellerListingPayload(
    dealType: String,
    priceRial: Long,
    depositRial: Long?,
    rentRial: Long?,
    terms: String?,
): JsonObject = JsonObject(
    buildMap {
        put("deal_type", JsonPrimitive(dealType))
        put("price_rial", JsonPrimitive(priceRial))
        depositRial?.let { put("deposit_rial", JsonPrimitive(it)) }
        rentRial?.let { put("rent_rial", JsonPrimitive(it)) }
        terms?.takeIf { it.isNotBlank() }?.let { put("terms", JsonPrimitive(it)) }
    },
)
