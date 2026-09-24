package ir.chardivari.core.marketplace

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Seller wizard contracts — Phase 4.
 *
 * All writes go through SECURITY DEFINER RPCs (`00093_seller_wizard.sql`);
 * raw `properties` insert stays admin-only under RLS. Payloads mirror the
 * jsonb parameters of `seller_create_draft(p_property, p_listing)`.
 */
@Serializable
data class SellerCreateDraftRequestDto(
    @SerialName("p_property")
    val property: JsonObject,
    @SerialName("p_listing")
    val listing: JsonObject,
)

@Serializable
data class SellerDraftCreatedDto(
    @SerialName("listing_id")
    val listingId: String,
    @SerialName("property_id")
    val propertyId: String,
)

@Serializable
data class SellerSetStatusRequestDto(
    @SerialName("p_listing_id")
    val listingId: String,
    @SerialName("p_to")
    val to: String,
)

/** `property_media` insert row (storage bytes go through Storage API). */
@Serializable
data class PropertyMediaWriteDto(
    @SerialName("property_id")
    val propertyId: String,
    @SerialName("storage_path")
    val storagePath: String,
    @SerialName("media_type")
    val mediaType: String,
    @SerialName("mime_type")
    val mimeType: String,
    @SerialName("byte_size")
    val byteSize: Int,
    @SerialName("sort_order")
    val sortOrder: Int = 0,
    @SerialName("is_cover")
    val isCover: Boolean = false,
)

/** Manage-screen row: seller's own listing with light property embed. */
@Serializable
data class SellerListingDto(
    val id: String,
    @SerialName("property_id")
    val propertyId: String,
    @SerialName("deal_type")
    val dealType: DealType,
    @SerialName("price_rial")
    val priceRial: Long,
    @SerialName("deposit_rial")
    val depositRial: Long? = null,
    @SerialName("rent_rial")
    val rentRial: Long? = null,
    val status: String,
    @SerialName("published_at")
    val publishedAt: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    val property: SellerPropertyRefDto? = null,
)

@Serializable
data class SellerPropertyRefDto(
    val id: String? = null,
    @SerialName("property_type")
    val propertyType: PropertyType? = null,
    @SerialName("area_sqm")
    val areaSqm: Int? = null,
    val city: String? = null,
    val neighborhood: String? = null,
    val media: List<PropertyMediaDto> = emptyList(),
)

/** Manage-screen domain row. */
data class SellerListingItem(
    val id: String,
    val status: String,
    val dealType: DealType,
    val priceRial: Long,
    val depositRial: Long?,
    val rentRial: Long?,
    val publishedAt: String?,
    val city: String?,
    val areaSqm: Int?,
    val propertyType: PropertyType?,
    val coverPath: String?,
) {
    val isFixture: Boolean get() = false
}

fun SellerListingDto.toSellerItem(): SellerListingItem {
    val media = property?.media.orEmpty()
    val cover = media
        .filter { it.mediaType == "image" }
        .minByOrNull { if (it.isCover) 0 else 1 }
        ?.storagePath
    return SellerListingItem(
        id = id,
        status = status,
        dealType = dealType,
        priceRial = priceRial,
        depositRial = depositRial,
        rentRial = rentRial,
        publishedAt = publishedAt,
        city = property?.city,
        areaSqm = property?.areaSqm,
        propertyType = property?.propertyType,
        coverPath = cover,
    )
}
