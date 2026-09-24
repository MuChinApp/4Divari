package ir.chardivari.core.marketplace

import kotlinx.serialization.Serializable

/**
 * Structured marketplace filters — language-neutral, serializable to
 * `saved_searches.query` jsonb and mappable 1:1 onto PostgREST params.
 */
@Serializable
data class SearchFilters(
    val query: String = "",
    val dealType: DealType? = null,
    val city: String? = null,
    val minPriceRial: Long? = null,
    val maxPriceRial: Long? = null,
    val minAreaSqm: Int? = null,
    val maxAreaSqm: Int? = null,
    val minBedrooms: Int? = null,
) {
    val activeCount: Int
        get() = (if (query.isNotBlank()) 1 else 0) +
            listOf(
                dealType,
                city?.takeIf { it.isNotBlank() },
                minPriceRial,
                maxPriceRial,
                minAreaSqm,
                maxAreaSqm,
                minBedrooms,
            ).count { it != null }

    val isActive: Boolean get() = activeCount > 0

    fun normalizedQuery(): String = query.trim()

    companion object {
        val EMPTY = SearchFilters()
    }
}

/**
 * Builds PostgREST query strings for `listings` (embedded `property` + media).
 *
 * Pure function — unit-tested; no network. Embed uses `!inner` only when a
 * property-level filter is present so city-less feeds still return rows.
 */
object ListingQuery {

    const val SELECT =
        "id,property_id,deal_type,price_rial,deposit_rial,rent_rial,terms," +
            "status,published_at,verification_status,freshness,data_source," +
            "last_verified_at," +
            "property:properties(" +
            "id,property_type,area_sqm,built_area_sqm,land_sqm,bedrooms,floor," +
            "total_floors,build_year,has_elevator,has_parking,has_storage," +
            "has_balcony,orientation,renovation_status,heating,cooling," +
            "deed_status,has_tenant,description,latitude,longitude,neighborhood," +
            "city,province," +
            "media:property_media(id,storage_path,media_type,sort_order,is_cover,data_source)" +
            ")"

    fun build(
        filters: SearchFilters = SearchFilters.EMPTY,
        limit: Int = 30,
        offset: Int = 0,
        status: String = "ACTIVE",
    ): String {
        val propertyFilters = buildPropertyFilters(filters)
        val needsInner = propertyFilters.isNotEmpty()
        val select = if (needsInner) {
            SELECT.replace("property:properties(", "property:properties!inner(")
        } else {
            SELECT
        }

        val params = mutableListOf(
            "select=$select",
            "status=eq.$status",
            "deleted_at=is.null",
            "order=published_at.desc",
            "limit=$limit",
            "offset=$offset",
        )
        filters.dealType?.let { params += "deal_type=eq.${it.name}" }
        filters.minPriceRial?.let { params += "price_rial=gte.$it" }
        filters.maxPriceRial?.let { params += "price_rial=lte.$it" }
        params += propertyFilters

        val q = filters.normalizedQuery()
        if (q.isNotEmpty()) {
            // Free text: city / neighborhood / description contains.
            val escaped = q.replace(",", "").replace("(", "").replace(")", "")
            params += buildOrIlike(escaped)
        }
        return params.joinToString("&")
    }

    private fun buildPropertyFilters(filters: SearchFilters): List<String> {
        val out = mutableListOf<String>()
        filters.city?.takeIf { it.isNotBlank() }?.let {
            out += "property.city=eq.${encode(it)}"
        }
        filters.minAreaSqm?.let { out += "property.area_sqm=gte.$it" }
        filters.maxAreaSqm?.let { out += "property.area_sqm=lte.$it" }
        filters.minBedrooms?.let { out += "property.bedrooms=gte.$it" }
        return out
    }

    private fun buildOrIlike(term: String): String {
        val pattern = ".*${encode(term)}.*"
        val or = listOf(
            "property.city.ilike.$pattern",
            "property.neighborhood.ilike.$pattern",
            "property.description.ilike.$pattern",
        ).joinToString(",")
        return "or=($or)"
    }

    /** Percent-encode for query values (PostgREST accepts raw UTF-8 path-safe). */
    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
