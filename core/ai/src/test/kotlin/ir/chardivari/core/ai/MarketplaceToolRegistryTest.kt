package ir.chardivari.core.ai

import com.google.common.truth.Truth.assertThat
import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.marketplace.DealType
import ir.chardivari.core.marketplace.Listing
import ir.chardivari.core.marketplace.ListingRepository
import ir.chardivari.core.marketplace.PropertyDto
import ir.chardivari.core.marketplace.PropertyType
import ir.chardivari.core.marketplace.SearchFilters
import ir.chardivari.core.marketplace.StorageBaseUrl
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertNull
import org.junit.Test

class MarketplaceToolRegistryTest {

    private class FakeListingRepository(
        var searchResult: AppResult<List<Listing>> = AppResult.Empty,
        var byIdResults: Map<String, AppResult<Listing>> = emptyMap(),
    ) : ListingRepository {
        var lastFilters: SearchFilters? = null
        var lastLimit: Int? = null

        override suspend fun feed(limit: Int): AppResult<List<Listing>> =
            search(SearchFilters.EMPTY, limit)

        override suspend fun search(
            filters: SearchFilters,
            limit: Int,
        ): AppResult<List<Listing>> {
            lastFilters = filters
            lastLimit = limit
            return searchResult
        }

        override suspend fun byId(listingId: String): AppResult<Listing> =
            byIdResults[listingId] ?: AppResult.Empty
    }

    private fun listing(
        id: String,
        priceRial: Long,
        dealType: DealType = DealType.SALE,
        depositRial: Long? = null,
        areaSqm: Int = 80,
        city: String = "تهران",
        freshness: String = "fresh",
        verificationStatus: String = "verified",
    ): Listing = Listing(
        id = id,
        propertyId = "p-$id",
        dealType = dealType,
        priceRial = priceRial,
        depositRial = depositRial,
        rentRial = null,
        status = "ACTIVE",
        publishedAt = "2026-01-01T00:00:00+00:00",
        verificationStatus = verificationStatus,
        freshness = freshness,
        dataSource = "REAL",
        property = PropertyDto(
            id = "p-$id",
            propertyType = PropertyType.APARTMENT,
            areaSqm = areaSqm,
            city = city,
            province = "تهران",
        ),
    )

    private fun registry(repo: FakeListingRepository): MarketplaceToolRegistry =
        MarketplaceToolRegistry(
            listings = repo,
            storage = StorageBaseUrl("https://example.supabase.co"),
        )

    private fun argsOf(vararg pairs: Pair<String, kotlinx.serialization.json.JsonElement>): JsonObject =
        buildJsonObject { pairs.forEach { (key, value) -> put(key, value) } }

    private fun resultOf(
        registry: MarketplaceToolRegistry,
        name: String,
        args: JsonObject = buildJsonObject { },
    ): ToolResult {
        val result = runBlocking { registry.execute(name, args) }
        check(result is AppResult.Success) { "expected Success, was $result" }
        return result.data
    }

    @Test
    fun `search returns cards, summary json and listing sources`() = runBlocking {
        val repo = FakeListingRepository(
            searchResult = AppResult.Success(
                listOf(
                    listing("l1", priceRial = 5_000_000_000, areaSqm = 90),
                    listing("l2", priceRial = 7_000_000_000, areaSqm = 120),
                ),
            ),
        )

        val result = resultOf(
            registry(repo),
            AiTools.SEARCH,
            argsOf("city" to JsonPrimitive("تهران")),
        )

        assertThat(result.intent?.resultCount).isEqualTo(2)
        assertThat(result.intent?.cards).hasSize(2)
        val json = result.json.jsonObject
        assertThat(json["count"]?.jsonPrimitive?.content).isEqualTo("2")
        assertThat(json["results"]!!.jsonArray).hasSize(2)
        assertThat(result.sources.map { it.kind }).containsExactly(
            "tool",
            "listing",
            "listing",
        )
        assertThat(repo.lastFilters?.city).isEqualTo("تهران")
        assertThat(repo.lastLimit).isEqualTo(10)
    }

    @Test
    fun `search with no matches returns honest empty result`() = runBlocking {
        val repo = FakeListingRepository(searchResult = AppResult.Empty)

        val result = resultOf(registry(repo), AiTools.SEARCH)

        val json = result.json.jsonObject
        assertThat(json["no_results"]?.jsonPrimitive?.content).isEqualTo("true")
        assertThat(result.intent?.resultCount).isEqualTo(0)
        assertThat(result.intent?.cards).isEmpty()
    }

    @Test
    fun `search maps persian-digit strings into filters`() = runBlocking {
        val repo = FakeListingRepository(
            searchResult = AppResult.Success(listOf(listing("l1", priceRial = 1_000))),
        )

        resultOf(
            registry(repo),
            AiTools.SEARCH,
            argsOf("max_price_rial" to JsonPrimitive("۵۰۰۰۰۰۰۰۰")),
        )

        assertThat(repo.lastFilters?.maxPriceRial).isEqualTo(500_000_000L)
    }

    @Test
    fun `estimate price builds band over sale prices`() = runBlocking {
        val prices = (1L..12L).map { it * 1_000_000_000 }
        val repo = FakeListingRepository(
            searchResult = AppResult.Success(
                prices.mapIndexed { index, price -> listing("l$index", priceRial = price) },
            ),
        )

        val result = resultOf(
            registry(repo),
            AiTools.PRICE,
            argsOf(
                "deal_type" to JsonPrimitive("SALE"),
                "city" to JsonPrimitive("تهران"),
            ),
        )

        val band = requireNotNull(result.priceRange)
        assertThat(band.sampleSize).isEqualTo(12)
        assertThat(band.basis).isEqualTo("price_rial")
        assertThat(band.insufficientSamples).isFalse()
        assertThat(result.json.jsonObject["sample_size"]?.jsonPrimitive?.content)
            .isEqualTo("12")
        assertThat(repo.lastLimit).isEqualTo(100)
    }

    @Test
    fun `estimate flags insufficient samples`() = runBlocking {
        val repo = FakeListingRepository(
            searchResult = AppResult.Success(
                (1L..3L).map { listing("l$it", priceRial = it * 1_000_000_000) },
            ),
        )

        val result = resultOf(registry(repo), AiTools.PRICE)

        val band = requireNotNull(result.priceRange)
        assertThat(band.insufficientSamples).isTrue()
        assertThat(band.confidence).isEqualTo(AiConfidence.LOW)
    }

    @Test
    fun `estimate for rent uses deposit basis`() = runBlocking {
        val repo = FakeListingRepository(
            searchResult = AppResult.Success(
                (1L..10L).map {
                    listing(
                        id = "l$it",
                        priceRial = 0,
                        dealType = DealType.RENT,
                        depositRial = it * 100_000_000,
                    )
                },
            ),
        )

        val result = resultOf(
            registry(repo),
            AiTools.PRICE,
            argsOf("deal_type" to JsonPrimitive("RENT")),
        )

        val band = requireNotNull(result.priceRange)
        assertThat(band.basis).isEqualTo("deposit_rial")
        assertThat(band.dealType).isEqualTo(DealType.RENT)
        assertThat(band.minRial).isEqualTo(100_000_000L)
    }

    @Test
    fun `estimate with no data returns no_data json and no artifact`() = runBlocking {
        val repo = FakeListingRepository(searchResult = AppResult.Empty)

        val result = resultOf(registry(repo), AiTools.PRICE)

        assertNull(result.priceRange)
        assertThat(result.json.jsonObject["no_data"]?.jsonPrimitive?.content)
            .isEqualTo("true")
    }

    @Test
    fun `compare builds rows for found listings and records missing ids`() = runBlocking {
        val repo = FakeListingRepository(
            byIdResults = mapOf(
                "a" to AppResult.Success(listing("a", priceRial = 5_000_000_000)),
                "b" to AppResult.Success(listing("b", priceRial = 8_000_000_000, areaSqm = 110)),
                "c" to AppResult.Empty,
            ),
        )
        val args = argsOf(
            "listing_ids" to JsonArray(listOf("a", "b", "c").map { JsonPrimitive(it) }),
        )

        val result = resultOf(registry(repo), AiTools.COMPARE, args)

        val comparison = requireNotNull(result.comparison)
        assertThat(comparison.columns).hasSize(2)
        assertThat(comparison.rows.map { it.label }).contains("قیمت")
        val missing = result.json.jsonObject["missing_ids"]!!.jsonArray
        assertThat(missing.map { it.jsonPrimitive.content }).containsExactly("c")
    }

    @Test
    fun `compare with fewer than two ids returns recoverable error`() = runBlocking {
        val args = argsOf(
            "listing_ids" to JsonArray(listOf(JsonPrimitive("a"))),
        )

        val result = resultOf(registry(FakeListingRepository()), AiTools.COMPARE, args)

        assertThat(result.json.jsonObject["error"]?.jsonPrimitive?.content)
            .isEqualTo("need_at_least_two_ids")
        assertNull(result.comparison)
    }

    @Test
    fun `unknown tool fails closed`() = runBlocking {
        val result = registry(FakeListingRepository())
            .execute("nope", buildJsonObject { })
        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        val failure = result as AppResult.Failure
        assertThat(failure.error).isInstanceOf(AppError.Client::class.java)
    }

    @Test
    fun `specs expose allowed keys for the fairness whitelist`() {
        val specs = registry(FakeListingRepository()).specs()
        assertThat(specs.map { it.name })
            .containsExactly(AiTools.SEARCH, AiTools.PRICE, AiTools.COMPARE)
        val searchSpec = specs.first { it.name == AiTools.SEARCH }
        assertThat(searchSpec.allowedKeys).containsAtLeast(
            "city",
            "deal_type",
            "max_price_rial",
            "min_bedrooms",
        )
        val compareSpec = specs.first { it.name == AiTools.COMPARE }
        assertThat(compareSpec.allowedKeys).containsExactly("listing_ids")
    }
}
