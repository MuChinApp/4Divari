package ir.chardivari.core.marketplace

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class SellerPayloadTest {

    @Test
    fun propertyPayload_containsRequiredFieldsAndSkipsBlankOptional() {
        val payload = buildSellerPropertyPayload(
            propertyType = "APARTMENT",
            areaSqm = 80,
            bedrooms = 2,
            province = "تهران",
            city = "تهران",
            neighborhood = "",
            floor = 3,
            totalFloors = null,
            buildYear = 1400,
            hasElevator = true,
            hasParking = false,
            hasStorage = true,
            hasBalcony = false,
            description = "توضیح",
        )
        assertThat(payload["property_type"]!!.jsonPrimitive.content).isEqualTo("APARTMENT")
        assertThat(payload["area_sqm"]!!.jsonPrimitive.content).isEqualTo("80")
        assertThat(payload["bedrooms"]!!.jsonPrimitive.content).isEqualTo("2")
        assertThat(payload["province"]!!.jsonPrimitive.content).isEqualTo("تهران")
        assertThat(payload["has_elevator"]!!.jsonPrimitive.content).isEqualTo("true")
        assertThat(payload["has_parking"]!!.jsonPrimitive.content).isEqualTo("false")
        assertThat(payload["floor"]!!.jsonPrimitive.content).isEqualTo("3")
        assertThat(payload["build_year"]!!.jsonPrimitive.content).isEqualTo("1400")
        // Optional blanks must be omitted (DB has nullable columns, not '').
        assertThat(payload).doesNotContainKey("neighborhood")
        assertThat(payload).doesNotContainKey("total_floors")
    }

    @Test
    fun listingPayload_saleKeepsPriceOnly() {
        val payload = buildSellerListingPayload(
            dealType = "SALE",
            priceRial = 5_000_000_000L,
            depositRial = null,
            rentRial = null,
            terms = null,
        )
        assertThat(payload["deal_type"]!!.jsonPrimitive.content).isEqualTo("SALE")
        assertThat(payload["price_rial"]!!.jsonPrimitive.content).isEqualTo("5000000000")
        assertThat(payload).doesNotContainKey("deposit_rial")
        assertThat(payload).doesNotContainKey("rent_rial")
    }

    @Test
    fun listingPayload_rentMirrorsMonthlyRentIntoPrice() {
        val payload = buildSellerListingPayload(
            dealType = "RENT",
            priceRial = 90_000_000L,
            depositRial = null,
            rentRial = 90_000_000L,
            terms = null,
        )
        assertThat(payload["price_rial"]!!.jsonPrimitive.content).isEqualTo("90000000")
        assertThat(payload["rent_rial"]!!.jsonPrimitive.content).isEqualTo("90000000")
    }

    @Test
    fun listingPayload_rentWithDepositKeepsBothLegs() {
        val payload = buildSellerListingPayload(
            dealType = "RENT_WITH_DEPOSIT",
            priceRial = 2_000_000_000L,
            depositRial = 2_000_000_000L,
            rentRial = 45_000_000L,
            terms = null,
        )
        assertThat(payload["deposit_rial"]!!.jsonPrimitive.content).isEqualTo("2000000000")
        assertThat(payload["rent_rial"]!!.jsonPrimitive.content).isEqualTo("45000000")
        assertThat(payload["price_rial"]!!.jsonPrimitive.content).isEqualTo("2000000000")
    }

    @Test
    fun sellerItem_mapsCoverFromEmbeddedMedia() {
        val dto = SellerListingDto(
            id = "L1",
            propertyId = "P1",
            dealType = DealType.SALE,
            priceRial = 1L,
            status = "ACTIVE",
            property = SellerPropertyRefDto(
                id = "P1",
                city = "تهران",
                areaSqm = 80,
                media = listOf(
                    PropertyMediaDto(id = "m2", storagePath = "p/2.jpg", sortOrder = 1),
                    PropertyMediaDto(
                        id = "m1",
                        storagePath = "p/1.jpg",
                        sortOrder = 0,
                        isCover = true,
                    ),
                ),
            ),
        )
        val item = dto.toSellerItem()
        assertThat(item.coverPath).isEqualTo("p/1.jpg")
        assertThat(item.city).isEqualTo("تهران")
        assertThat(item.status).isEqualTo("ACTIVE")
    }
}
