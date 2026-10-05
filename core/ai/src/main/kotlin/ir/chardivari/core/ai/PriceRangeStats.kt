package ir.chardivari.core.ai

import ir.chardivari.core.marketplace.DealType
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Percentile band over real listing prices.
 *
 * Same honesty floor as server-side `listing_risk_signals`: fewer than
 * [MIN_SAMPLES] samples ⇒ [PriceRangeEstimate.insufficientSamples] = true and
 * confidence LOW — the UI must present the band as unreliable.
 */
object PriceRangeStats {

    const val MIN_SAMPLES: Int = 8

    /** Null when there is no data — callers never invent a band. */
    fun from(
        values: List<Long>,
        basis: String,
        dealType: DealType,
        city: String?,
    ): PriceRangeEstimate? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val n = sorted.size
        return PriceRangeEstimate(
            basis = basis,
            dealType = dealType,
            city = city,
            minRial = sorted.first(),
            p25Rial = percentile(sorted, 25.0),
            medianRial = percentile(sorted, 50.0),
            p75Rial = percentile(sorted, 75.0),
            maxRial = sorted.last(),
            sampleSize = n,
            confidence = when {
                n < MIN_SAMPLES -> AiConfidence.LOW
                n < 30 -> AiConfidence.MEDIUM
                else -> AiConfidence.HIGH
            },
            insufficientSamples = n < MIN_SAMPLES,
        )
    }

    /** Linear-interpolation percentile on a pre-sorted list. */
    fun percentile(sorted: List<Long>, p: Double): Long {
        require(sorted.isNotEmpty()) { "percentile of empty list" }
        if (sorted.size == 1) return sorted.first()
        val position = (p / 100.0) * (sorted.size - 1)
        val lower = floor(position).toInt()
        val upper = ceil(position).toInt()
        if (lower == upper) return sorted[lower]
        val fraction = position - lower
        return (sorted[lower] * (1 - fraction) + sorted[upper] * fraction).toLong()
    }
}
