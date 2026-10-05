package ir.chardivari.core.ai

import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.common.Format
import ir.chardivari.core.marketplace.DealType
import ir.chardivari.core.marketplace.Listing
import ir.chardivari.core.marketplace.ListingPresenter
import ir.chardivari.core.marketplace.ListingRepository
import ir.chardivari.core.marketplace.SearchFilters
import ir.chardivari.core.marketplace.StorageBaseUrl
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/** Tool names exposed to the model (stable contract — never rename lightly). */
object AiTools {
    const val SEARCH = "search_listings"
    const val PRICE = "estimate_price_range"
    const val COMPARE = "compare_listings"

    val LABELS_FA: Map<String, String> = mapOf(
        SEARCH to "جست‌وجوی ملک",
        PRICE to "تخمین بازهٔ قیمت",
        COMPARE to "مقایسهٔ ملک‌ها",
    )
}

/**
 * One tool execution: [json] goes back to the model; the typed artifacts
 * (cards, price band, comparison) are what the UI renders — both derived
 * from the SAME real fetch, so displayed facts cannot diverge from the
 * model's evidence.
 */
data class ToolResult(
    val json: JsonObject,
    val intent: DetectedIntent? = null,
    val priceRange: PriceRangeEstimate? = null,
    val comparison: ListingComparison? = null,
    val sources: List<AssistantSource> = emptyList(),
)

data class AiToolSpec(
    val name: String,
    val description: String,
    val parameters: JsonObject,
) {
    /** Whitelist for FairnessGuard — schema properties are the only legal args. */
    val allowedKeys: Set<String>
        get() = (parameters["properties"] as? JsonObject)?.keys.orEmpty()
}

interface AssistantToolRegistry {
    fun specs(): List<AiToolSpec>

    suspend fun execute(name: String, args: JsonObject): AppResult<ToolResult>
}

/** NL-intent args → real [SearchFilters] (whitelisted keys only, Persian digits tolerated). */
fun searchFiltersFromArgs(args: JsonObject): SearchFilters = SearchFilters(
    query = args.aiString("query") ?: "",
    dealType = parseDealType(args.aiString("deal_type")),
    city = args.aiString("city"),
    minPriceRial = args.aiLong("min_price_rial"),
    maxPriceRial = args.aiLong("max_price_rial"),
    minAreaSqm = args.aiInt("min_area_sqm"),
    maxAreaSqm = args.aiInt("max_area_sqm"),
    minBedrooms = args.aiInt("min_bedrooms"),
)

private const val SEARCH_LIMIT = 10
private const val PRICE_SAMPLE_LIMIT = 100
private const val COMPARE_MAX = 4

/**
 * Real-data tools backed by [ListingRepository] (PostgREST, user RLS).
 * Fail-closed: repository failures propagate — never a fabricated result.
 */
@Singleton
class MarketplaceToolRegistry @Inject constructor(
    private val listings: ListingRepository,
    private val storage: StorageBaseUrl,
) : AssistantToolRegistry {

    override fun specs(): List<AiToolSpec> = listOf(
        AiToolSpec(
            name = AiTools.SEARCH,
            description = "جست‌وجوی ملک‌های واقعی با فیلترهای ساخت‌یافته. " +
                "نتایج شامل شناسه، قیمت (ریال)، متراژ، شهر و وضعیت داده است.",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                put("properties", searchPropertySchema())
                put("additionalProperties", JsonPrimitive(false))
            },
        ),
        AiToolSpec(
            name = AiTools.PRICE,
            description = "بازهٔ تخمینی قیمت از نمونهٔ واقعی نتایج جست‌وجو " +
                "(چارک‌ها، تعداد نمونه و سطح اطمینان). زیر ۸ نمونه، اطمینان کم است.",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                put("properties", searchPropertySchema())
                put("additionalProperties", JsonPrimitive(false))
            },
        ),
        AiToolSpec(
            name = AiTools.COMPARE,
            description = "مقایسهٔ ۲ تا ۴ ملک با شناسهٔ فهرست‌شده — " +
                "قیمت، متراژ، امکانات پایه، تازگی و تأیید.",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                put(
                    "properties",
                    buildJsonObject {
                        put(
                            "listing_ids",
                            buildJsonObject {
                                put("type", JsonPrimitive("array"))
                                put(
                                    "items",
                                    buildJsonObject {
                                        put("type", JsonPrimitive("string"))
                                    },
                                )
                                put("minItems", JsonPrimitive(2))
                                put("maxItems", JsonPrimitive(COMPARE_MAX))
                            },
                        )
                    },
                )
                put("additionalProperties", JsonPrimitive(false))
            },
        ),
    )

    override suspend fun execute(name: String, args: JsonObject): AppResult<ToolResult> =
        when (name) {
            AiTools.SEARCH -> executeSearch(args)
            AiTools.PRICE -> executePrice(args)
            AiTools.COMPARE -> executeCompare(args)
            else -> AppResult.Failure(
                AppError.Client(code = 0, serverMessage = "Unknown AI tool: $name"),
            )
        }

    private suspend fun executeSearch(args: JsonObject): AppResult<ToolResult> {
        val filters = searchFiltersFromArgs(args)
        return when (val result = listings.search(filters, limit = SEARCH_LIMIT)) {
            is AppResult.Success -> AppResult.Success(searchResult(result.data, filters))
            AppResult.Empty -> AppResult.Success(
                ToolResult(
                    json = resultsJson(count = 0, results = emptyList()),
                    intent = DetectedIntent(filters = filters, resultCount = 0, cards = emptyList()),
                    sources = listOf(toolSource(AiTools.SEARCH)),
                ),
            )
            is AppResult.Failure -> result
        }
    }

    private fun searchResult(data: List<Listing>, filters: SearchFilters): ToolResult {
        val cards = data.map { ListingPresenter.card(it, storage.value, favorited = false) }
        return ToolResult(
            json = resultsJson(count = data.size, results = data.map { listingSummary(it) }),
            intent = DetectedIntent(
                filters = filters,
                resultCount = data.size,
                cards = cards,
            ),
            sources = listOf(toolSource(AiTools.SEARCH)) + data.map { listingSource(it) },
        )
    }

    private suspend fun executePrice(args: JsonObject): AppResult<ToolResult> {
        val requested = searchFiltersFromArgs(args)
        val dealType = requested.dealType ?: DealType.SALE
        val filters = requested.copy(dealType = dealType)
        val basis = if (dealType == DealType.SALE) "price_rial" else "deposit_rial"
        return when (val result = listings.search(filters, limit = PRICE_SAMPLE_LIMIT)) {
            is AppResult.Success -> {
                val values = if (dealType == DealType.SALE) {
                    result.data.map { it.priceRial }
                } else {
                    result.data.mapNotNull { it.depositRial }
                }
                val estimate = PriceRangeStats.from(
                    values = values,
                    basis = basis,
                    dealType = dealType,
                    city = filters.city,
                )
                if (estimate == null) {
                    AppResult.Success(
                        ToolResult(
                            json = emptyPriceJson(basis, dealType, filters.city),
                            sources = listOf(toolSource(AiTools.PRICE)),
                        ),
                    )
                } else {
                    AppResult.Success(
                        ToolResult(
                            json = priceJson(estimate),
                            priceRange = estimate,
                            sources = listOf(toolSource(AiTools.PRICE)),
                        ),
                    )
                }
            }
            AppResult.Empty -> AppResult.Success(
                ToolResult(
                    json = emptyPriceJson(basis, dealType, filters.city),
                    sources = listOf(toolSource(AiTools.PRICE)),
                ),
            )
            is AppResult.Failure -> result
        }
    }

    private suspend fun executeCompare(args: JsonObject): AppResult<ToolResult> {
        val ids = args.aiStringArray("listing_ids").take(COMPARE_MAX)
        if (ids.size < 2) {
            return AppResult.Success(
                ToolResult(
                    json = buildJsonObject {
                        put("error", JsonPrimitive("need_at_least_two_ids"))
                    },
                    sources = listOf(toolSource(AiTools.COMPARE)),
                ),
            )
        }
        val found = mutableListOf<Listing>()
        val missing = mutableListOf<String>()
        for (id in ids) {
            when (val result = listings.byId(id)) {
                is AppResult.Success -> found += result.data
                AppResult.Empty -> missing += id
                is AppResult.Failure -> return result
            }
        }
        if (found.isEmpty()) {
            return AppResult.Success(
                ToolResult(
                    json = buildJsonObject {
                        put("error", JsonPrimitive("none_found"))
                        put("missing_ids", JsonArray(missing.map { JsonPrimitive(it) }))
                    },
                    sources = listOf(toolSource(AiTools.COMPARE)),
                ),
            )
        }
        val comparison = buildComparison(found)
        return AppResult.Success(
            ToolResult(
                json = comparisonJson(comparison, missing),
                comparison = comparison,
                sources = listOf(toolSource(AiTools.COMPARE)) + found.map { listingSource(it) },
            ),
        )
    }

    private fun buildComparison(items: List<Listing>): ListingComparison {
        fun row(label: String, pick: (Listing) -> String?): ComparisonRow =
            ComparisonRow(label = label, values = items.map(pick))

        return ListingComparison(
            columns = items.mapIndexed { index, _ -> "ملک ${index + 1}" },
            rows = listOf(
                row("قیمت") { ListingPresenter.priceLine(it) },
                row("قیمت هر متر") {
                    Format.pricePerSqm(it.priceRial, it.property.areaSqm)
                },
                row("متراژ") { Format.area(it.property.areaSqm) },
                row("اتاق") {
                    if (it.property.bedrooms > 0) {
                        Format.bedrooms(it.property.bedrooms)
                    } else {
                        null
                    }
                },
                row("سال ساخت") {
                    it.property.buildYear?.let { year -> Format.toPersianDigits(year) }
                },
                row("شهر و محله") {
                    listOfNotNull(it.property.city, it.property.neighborhood)
                        .joinToString("، ")
                        .takeIf { text -> text.isNotBlank() }
                },
                row("تازگی داده") { freshnessLabel(it.freshness) },
                row("وضعیت تأیید") { verificationLabel(it.verificationStatus) },
                row("نوع معامله") { ListingPresenter.dealTypeLabel(it.dealType) },
            ),
        )
    }

    /* ------------------------------ JSON builders ---------------------- */

    private fun searchPropertySchema(): JsonObject = buildJsonObject {
        put("query", stringSchema("متن جست‌وجوی آزاد"))
        put("deal_type", buildJsonObject {
            put("type", JsonPrimitive("string"))
            put(
                "enum",
                JsonArray(
                    listOf(
                        JsonPrimitive("SALE"),
                        JsonPrimitive("RENT"),
                        JsonPrimitive("RENT_WITH_DEPOSIT"),
                    ),
                ),
            )
        })
        put("city", stringSchema("شهر به فارسی، مثلاً تهران"))
        put("min_price_rial", longSchema("حداقل قیمت به ریال"))
        put("max_price_rial", longSchema("حداکثر قیمت به ریال"))
        put("min_area_sqm", intSchema("حداقل متراژ (متر مربع)"))
        put("max_area_sqm", intSchema("حداکثر متراژ (متر مربع)"))
        put("min_bedrooms", intSchema("حداقل تعداد اتاق خواب"))
    }

    private fun stringSchema(description: String): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("string"))
        put("description", JsonPrimitive(description))
    }

    private fun longSchema(description: String): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("integer"))
        put("description", JsonPrimitive(description))
    }

    private fun intSchema(description: String): JsonObject = buildJsonObject {
        put("type", JsonPrimitive("integer"))
        put("description", JsonPrimitive(description))
    }

    private fun resultsJson(count: Int, results: List<JsonObject>): JsonObject =
        buildJsonObject {
            put("count", JsonPrimitive(count))
            put("no_results", JsonPrimitive(count == 0))
            put("results", JsonArray(results))
        }

    private fun listingSummary(listing: Listing): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(listing.id))
        put("deal_type", JsonPrimitive(listing.dealType.name))
        put("price_rial", JsonPrimitive(listing.priceRial))
        listing.depositRial?.let { put("deposit_rial", JsonPrimitive(it)) }
        listing.rentRial?.let { put("rent_rial", JsonPrimitive(it)) }
        put("area_sqm", JsonPrimitive(listing.property.areaSqm))
        put("bedrooms", JsonPrimitive(listing.property.bedrooms))
        put("city", JsonPrimitive(listing.property.city))
        listing.property.neighborhood?.let { put("neighborhood", JsonPrimitive(it)) }
        put("freshness", JsonPrimitive(listing.freshness))
        put("verification_status", JsonPrimitive(listing.verificationStatus))
        put("data_source", JsonPrimitive(listing.dataSource))
    }

    private fun priceJson(estimate: PriceRangeEstimate): JsonObject = buildJsonObject {
        put("basis", JsonPrimitive(estimate.basis))
        put("deal_type", JsonPrimitive(estimate.dealType.name))
        estimate.city?.let { put("city", JsonPrimitive(it)) }
        put("min_rial", JsonPrimitive(estimate.minRial))
        put("p25_rial", JsonPrimitive(estimate.p25Rial))
        put("median_rial", JsonPrimitive(estimate.medianRial))
        put("p75_rial", JsonPrimitive(estimate.p75Rial))
        put("max_rial", JsonPrimitive(estimate.maxRial))
        put("sample_size", JsonPrimitive(estimate.sampleSize))
        put("confidence", JsonPrimitive(estimate.confidence.name))
        put("insufficient_samples", JsonPrimitive(estimate.insufficientSamples))
    }

    private fun emptyPriceJson(basis: String, dealType: DealType, city: String?): JsonObject =
        buildJsonObject {
            put("basis", JsonPrimitive(basis))
            put("deal_type", JsonPrimitive(dealType.name))
            city?.let { put("city", JsonPrimitive(it)) }
            put("sample_size", JsonPrimitive(0))
            put("no_data", JsonPrimitive(true))
        }

    private fun comparisonJson(comparison: ListingComparison, missing: List<String>): JsonObject =
        buildJsonObject {
            put("columns", JsonArray(comparison.columns.map { JsonPrimitive(it) }))
            put(
                "rows",
                JsonArray(
                    comparison.rows.map { row ->
                        buildJsonObject {
                            put("label", JsonPrimitive(row.label))
                            put(
                                "values",
                                JsonArray(
                                    row.values.map { value ->
                                        if (value == null) {
                                            kotlinx.serialization.json.JsonNull
                                        } else {
                                            JsonPrimitive(value)
                                        }
                                    },
                                ),
                            )
                        }
                    },
                ),
            )
            if (missing.isNotEmpty()) {
                put("missing_ids", JsonArray(missing.map { JsonPrimitive(it) }))
            }
            put("found_count", JsonPrimitive(comparison.columns.size))
        }

    private fun freshnessLabel(freshness: String): String = when (freshness) {
        "fresh" -> "تازه"
        "stale" -> "قدیمی"
        else -> freshness
    }

    private fun verificationLabel(status: String): String = when (status) {
        "verified" -> "تأییدشده"
        "pending" -> "در انتظار تأیید"
        "rejected" -> "ردشده"
        else -> status
    }

    private fun toolSource(name: String): AssistantSource =
        AssistantSource(kind = "tool", id = name, label = AiTools.LABELS_FA[name] ?: name)

    private fun listingSource(listing: Listing): AssistantSource {
        val label = listOfNotNull(
            listing.property.city,
            Format.area(listing.property.areaSqm),
        ).joinToString(" · ")
        return AssistantSource(kind = "listing", id = listing.id, label = label)
    }
}
