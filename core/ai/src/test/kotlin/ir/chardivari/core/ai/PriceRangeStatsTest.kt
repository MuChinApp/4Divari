package ir.chardivari.core.ai

import com.google.common.truth.Truth.assertThat
import ir.chardivari.core.marketplace.DealType
import org.junit.Assert.assertNull
import org.junit.Test

class PriceRangeStatsTest {

    private fun band(vararg values: Long): PriceRangeEstimate? = PriceRangeStats.from(
        values = values.toList(),
        basis = "price_rial",
        dealType = DealType.SALE,
        city = "تهران",
    )

    @Test
    fun `empty input returns null — never invents a band`() {
        assertNull(band())
    }

    @Test
    fun `percentile band over ten samples`() {
        val estimate = band(
            1_000_000_000, 2_000_000_000, 3_000_000_000, 4_000_000_000, 5_000_000_000,
            6_000_000_000, 7_000_000_000, 8_000_000_000, 9_000_000_000, 10_000_000_000,
        )
        requireNotNull(estimate)
        assertThat(estimate.sampleSize).isEqualTo(10)
        assertThat(estimate.minRial).isEqualTo(1_000_000_000)
        assertThat(estimate.maxRial).isEqualTo(10_000_000_000)
        assertThat(estimate.medianRial).isEqualTo(5_500_000_000)
        assertThat(estimate.p25Rial).isEqualTo(3_250_000_000)
        assertThat(estimate.p75Rial).isEqualTo(7_750_000_000)
        assertThat(estimate.insufficientSamples).isFalse()
        assertThat(estimate.confidence).isEqualTo(AiConfidence.MEDIUM)
    }

    @Test
    fun `below eight samples is flagged insufficient with low confidence`() {
        val estimate = band(1_000_000_000, 2_000_000_000, 3_000_000_000)
        requireNotNull(estimate)
        assertThat(estimate.insufficientSamples).isTrue()
        assertThat(estimate.confidence).isEqualTo(AiConfidence.LOW)
        assertThat(estimate.sampleSize).isEqualTo(3)
    }

    @Test
    fun `thirty or more samples reach high confidence`() {
        val values = (1L..40L).map { it * 1_000_000_000 }
        val estimate = PriceRangeStats.from(values, "price_rial", DealType.SALE, null)
        requireNotNull(estimate)
        assertThat(estimate.confidence).isEqualTo(AiConfidence.HIGH)
        assertThat(estimate.insufficientSamples).isFalse()
    }

    @Test
    fun `single sample collapses all quartiles`() {
        val estimate = band(7_000_000_000)
        requireNotNull(estimate)
        assertThat(estimate.p25Rial).isEqualTo(7_000_000_000)
        assertThat(estimate.medianRial).isEqualTo(7_000_000_000)
        assertThat(estimate.p75Rial).isEqualTo(7_000_000_000)
        assertThat(estimate.insufficientSamples).isTrue()
    }

    @Test
    fun `percentile interpolates between neighbours`() {
        val sorted = listOf(10L, 20L, 30L, 40L)
        assertThat(PriceRangeStats.percentile(sorted, 50.0)).isEqualTo(25L)
        assertThat(PriceRangeStats.percentile(sorted, 25.0)).isEqualTo(17L)
    }
}
