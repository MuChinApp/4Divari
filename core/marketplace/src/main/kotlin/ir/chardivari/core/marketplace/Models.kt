package ir.chardivari.core.marketplace

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Deal type — mirrors Postgres `deal_type` enum (language-neutral backend).
 */
@Serializable
enum class DealType {
    @SerialName("SALE")
    SALE,

    @SerialName("RENT")
    RENT,

    @SerialName("RENT_WITH_DEPOSIT")
    RENT_WITH_DEPOSIT,
    ;
}

@Serializable
enum class PropertyType {
    @SerialName("APARTMENT")
    APARTMENT,

    @SerialName("HOUSE")
    HOUSE,

    @SerialName("VILLA")
    VILLA,

    @SerialName("LAND")
    LAND,

    @SerialName("SHOP")
    SHOP,

    @SerialName("OFFICE")
    OFFICE,

    @SerialName("INDUSTRIAL")
    INDUSTRIAL,

    @SerialName("OTHER")
    OTHER,
    ;
}

/** Raw PostgREST row for `property_media`. */
@Serializable
data class PropertyMediaDto(
    val id: String? = null,
    @SerialName("storage_path")
    val storagePath: String,
    @SerialName("media_type")
    val mediaType: String = "image",
    @SerialName("sort_order")
    val sortOrder: Int = 0,
    @SerialName("is_cover")
    val isCover: Boolean = false,
    @SerialName("data_source")
    val dataSource: String = "REAL",
)

/** Raw PostgREST row for `properties`. */
@Serializable
data class PropertyDto(
    val id: String,
    @SerialName("property_type")
    val propertyType: PropertyType,
    @SerialName("area_sqm")
    val areaSqm: Int,
    @SerialName("built_area_sqm")
    val builtAreaSqm: Int? = null,
    @SerialName("land_sqm")
    val landSqm: Int? = null,
    val bedrooms: Int = 0,
    val floor: Int? = null,
    @SerialName("total_floors")
    val totalFloors: Int? = null,
    @SerialName("build_year")
    val buildYear: Int? = null,
    @SerialName("has_elevator")
    val hasElevator: Boolean = false,
    @SerialName("has_parking")
    val hasParking: Boolean = false,
    @SerialName("has_storage")
    val hasStorage: Boolean = false,
    @SerialName("has_balcony")
    val hasBalcony: Boolean = false,
    val orientation: String? = null,
    @SerialName("renovation_status")
    val renovationStatus: String? = null,
    val heating: String? = null,
    val cooling: String? = null,
    @SerialName("deed_status")
    val deedStatus: String? = null,
    @SerialName("has_tenant")
    val hasTenant: Boolean = false,
    val description: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val neighborhood: String? = null,
    val city: String,
    val province: String,
    val media: List<PropertyMediaDto> = emptyList(),
)

/** Raw PostgREST row for `listings` with embedded `property`. */
@Serializable
data class ListingDto(
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
    val terms: String? = null,
    val status: String,
    @SerialName("published_at")
    val publishedAt: String? = null,
    @SerialName("verification_status")
    val verificationStatus: String = "unverified",
    val freshness: String = "fresh",
    @SerialName("data_source")
    val dataSource: String = "REAL",
    @SerialName("last_verified_at")
    val lastVerifiedAt: String? = null,
    val property: PropertyDto,
)

/** Domain model consumed by ViewModels (already validated non-null property). */
data class Listing(
    val id: String,
    val propertyId: String,
    val dealType: DealType,
    val priceRial: Long,
    val depositRial: Long?,
    val rentRial: Long?,
    val status: String,
    val publishedAt: String?,
    val verificationStatus: String,
    val freshness: String,
    val dataSource: String,
    val property: PropertyDto,
) {
    val isFixture: Boolean get() = dataSource == "DEV_FIXTURE"
    val isVerified: Boolean get() = verificationStatus == "verified"

    /** Primary photo URL for Supabase public storage, or null when no media. */
    fun coverImageUrl(storageBaseUrl: String?): String? {
        val path = property.media
            .filter { it.mediaType == "image" }
            .minByOrNull { if (it.isCover) 0 else 1 }
            ?.storagePath
            ?: return null
        if (storageBaseUrl.isNullOrBlank()) return null
        return storageBaseUrl.trimEnd('/') +
            "/object/public/property-media/" + path.trimStart('/')
    }
}

fun ListingDto.toDomain(): Listing = Listing(
    id = id,
    propertyId = propertyId,
    dealType = dealType,
    priceRial = priceRial,
    depositRial = depositRial,
    rentRial = rentRial,
    status = status,
    publishedAt = publishedAt,
    verificationStatus = verificationStatus,
    freshness = freshness,
    dataSource = dataSource,
    property = property,
)
