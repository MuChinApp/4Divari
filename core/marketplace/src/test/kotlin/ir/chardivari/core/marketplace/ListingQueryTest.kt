package ir.chardivari.core.marketplace

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ListingQueryTest {

    @Test
    fun emptyFilters_queryActiveOrderedFeed() {
        val q = ListingQuery.build(SearchFilters.EMPTY, limit = 20)
        assertThat(q).contains("status=eq.ACTIVE")
        assertThat(q).contains("deleted_at=is.null")
        assertThat(q).contains("order=published_at.desc")
        assertThat(q).contains("limit=20")
        assertThat(q).contains("select=")
        // No property filter → no !inner join (avoid dropping rows).
        assertThat(q).doesNotContain("!inner")
    }

    @Test
    fun cityFilter_usesInnerEmbed() {
        val q = ListingQuery.build(SearchFilters(city = "تهران"))
        assertThat(q).contains("property:properties!inner(")
        assertThat(q).contains("property.city=")
    }

    @Test
    fun dealTypeAndPriceRange() {
        val q = ListingQuery.build(
            SearchFilters(
                dealType = DealType.SALE,
                minPriceRial = 1_000_000L,
                maxPriceRial = 9_000_000_000L,
            ),
        )
        assertThat(q).contains("deal_type=eq.SALE")
        assertThat(q).contains("price_rial=gte.1000000")
        assertThat(q).contains("price_rial=lte.9000000000")
    }

    @Test
    fun areaAndBedrooms_onProperty() {
        val q = ListingQuery.build(
            SearchFilters(minAreaSqm = 80, maxAreaSqm = 120, minBedrooms = 2),
        )
        assertThat(q).contains("property.area_sqm=gte.80")
        assertThat(q).contains("property.area_sqm=lte.120")
        assertThat(q).contains("property.bedrooms=gte.2")
        assertThat(q).contains("!inner")
    }

    @Test
    fun freeText_buildsOrIlike() {
        val q = ListingQuery.build(SearchFilters(query = "پونک"))
        assertThat(q).contains("or=(")
        assertThat(q).contains("property.neighborhood.ilike.")
    }

    @Test
    fun activeCount_countsNonBlankQueryAndFilters() {
        assertThat(SearchFilters.EMPTY.activeCount).isEqualTo(0)
        assertThat(SearchFilters(query = "  ").activeCount).isEqualTo(0)
        assertThat(SearchFilters(query = "x").activeCount).isEqualTo(1)
        assertThat(
            SearchFilters(query = "x", city = "تهران", dealType = DealType.RENT).activeCount,
        ).isEqualTo(3)
    }

    @Test
    fun listingDto_mapsToDomainAndCoverUrl() {
        val dto = ListingDto(
            id = "l1",
            propertyId = "p1",
            dealType = DealType.SALE,
            priceRial = 12_800_000_000L,
            status = "ACTIVE",
            dataSource = "DEV_FIXTURE",
            verificationStatus = "verified",
            property = PropertyDto(
                id = "p1",
                propertyType = PropertyType.APARTMENT,
                areaSqm = 90,
                city = "تهران",
                province = "تهران",
                media = listOf(
                    PropertyMediaDto(
                        id = "m2",
                        storagePath = "b.jpg",
                        isCover = false,
                        sortOrder = 1,
                    ),
                    PropertyMediaDto(
                        id = "m1",
                        storagePath = "a.jpg",
                        isCover = true,
                        sortOrder = 0,
                    ),
                ),
            ),
        )
        val domain = dto.toDomain()
        assertThat(domain.isFixture).isTrue()
        assertThat(domain.isVerified).isTrue()
        val url = domain.coverImageUrl("https://x.supabase.co/storage/v1")
        assertThat(url).isEqualTo(
            "https://x.supabase.co/storage/v1/object/public/property-media/a.jpg",
        )
        assertThat(domain.coverImageUrl(null)).isNull()
    }
}
