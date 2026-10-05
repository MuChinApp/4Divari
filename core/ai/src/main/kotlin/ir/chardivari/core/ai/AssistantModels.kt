package ir.chardivari.core.ai

import ir.chardivari.core.common.toAsciiIntOrNull
import ir.chardivari.core.common.toAsciiLongOrNull
import ir.chardivari.core.marketplace.DealType
import ir.chardivari.core.marketplace.ListingCardUi
import ir.chardivari.core.marketplace.SearchFilters
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * One assistant answer plus the STRUCTURED artifacts produced by real tool
 * executions. The prose [text] is an LLM explanation; every number, filter
 * and listing shown in the UI comes from the artifacts — never from prose.
 */
data class AssistantTurn(
    val text: String,
    val intent: DetectedIntent? = null,
    val priceRange: PriceRangeEstimate? = null,
    val comparison: ListingComparison? = null,
    val sources: List<AssistantSource> = emptyList(),
    val fairnessNotice: String? = null,
    val toolsUsed: List<String> = emptyList(),
)

/** Prior exchange sent as lightweight context (tool transcripts stay per-turn). */
data class PriorTurn(
    val userText: String,
    val assistantText: String,
)

/** Search that actually executed — chips + result cards are rendered from this. */
data class DetectedIntent(
    val filters: SearchFilters,
    val resultCount: Int,
    val cards: List<ListingCardUi>,
)

enum class AiConfidence { LOW, MEDIUM, HIGH }

/**
 * Price band computed over REAL search results. [insufficientSamples] is
 * true below the 8-sample floor shared with the server risk signals.
 */
data class PriceRangeEstimate(
    val basis: String,
    val dealType: DealType,
    val city: String?,
    val minRial: Long,
    val p25Rial: Long,
    val medianRial: Long,
    val p75Rial: Long,
    val maxRial: Long,
    val sampleSize: Int,
    val confidence: AiConfidence,
    val insufficientSamples: Boolean,
)

data class ListingComparison(
    val columns: List<String>,
    val rows: List<ComparisonRow>,
)

data class ComparisonRow(
    val label: String,
    val values: List<String?>,
)

/** Citation chip — which tool and which listings grounded the answer. */
data class AssistantSource(
    val kind: String,
    val id: String,
    val label: String,
)

/* ------------------------------------------------------------------ */
/* JSON helpers — tolerant of Persian digits and string-encoded numbers */
/* ------------------------------------------------------------------ */

fun JsonObject.aiLong(key: String): Long? =
    (this[key] as? JsonPrimitive)?.content?.toAsciiLongOrNull()

fun JsonObject.aiInt(key: String): Int? =
    (this[key] as? JsonPrimitive)?.content?.toAsciiIntOrNull()

fun JsonObject.aiString(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

fun JsonObject.aiStringArray(key: String): List<String> =
    (this[key] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { v -> v.isNotBlank() } }
        .orEmpty()

fun parseDealType(raw: String?): DealType? = when (raw) {
    "SALE" -> DealType.SALE
    "RENT" -> DealType.RENT
    "RENT_WITH_DEPOSIT" -> DealType.RENT_WITH_DEPOSIT
    else -> null
}

fun parseConfidence(raw: String?): AiConfidence? = when (raw) {
    "LOW" -> AiConfidence.LOW
    "MEDIUM" -> AiConfidence.MEDIUM
    "HIGH" -> AiConfidence.HIGH
    else -> null
}
