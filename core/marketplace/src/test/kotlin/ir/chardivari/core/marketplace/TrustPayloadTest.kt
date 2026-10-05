package ir.chardivari.core.marketplace

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustPayloadTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun moderationQueue_parsesCountsReportsAndRisk() {
        val dto = json.decodeFromString(
            ModerationQueueDto.serializer(),
            """
            {
              "counts": {"open_reports": 3, "pending_verifications": 2},
              "reports": [
                {
                  "id": "r1",
                  "listing_id": "l1",
                  "reason": "قیمت نامعتبر",
                  "detail": null,
                  "status": "open",
                  "created_at": "2026-01-01T10:00:00+00:00",
                  "reporter_id": "u1",
                  "listing": {
                    "city": "تهران",
                    "neighborhood": "ونک",
                    "area_sqm": 100,
                    "price_rial": 5000000000,
                    "deal_type": "SALE",
                    "verification_status": "unverified",
                    "freshness": "stale"
                  },
                  "risk": [
                    {"code": "OPEN_REPORTS", "severity": "high",
                     "evidence": {"count": 2}},
                    {"code": "PRICE_UNKNOWN", "severity": "info",
                     "evidence": {"samples": 1, "reason": "insufficient_peers"}}
                  ]
                }
              ],
              "verifications": [
                {
                  "listing_id": "l2",
                  "verification_status": "pending",
                  "seller_id": "s1",
                  "agent_id": null,
                  "published_at": "2026-01-01T00:00:00+00:00",
                  "listing": {"city": "تهران", "freshness": "fresh"},
                  "risk": []
                }
              ],
              "generated_at": "2026-01-01T12:00:00+00:00"
            }
            """.trimIndent(),
        )
        val queue = dto.toModerationQueue()
        assertEquals(3, queue.openReports)
        assertEquals(2, queue.pendingVerifications)
        assertEquals(1, queue.reports.size)
        assertEquals(1, queue.verifications.size)

        val report = queue.reports.first()
        assertEquals("قیمت نامعتبر", report.reason)
        assertEquals("تهران، ونک", report.listing?.address)
        assertEquals("stale", report.listing?.freshness)
        assertEquals(2, report.risk.size)
        assertEquals("OPEN_REPORTS", report.risk[0].code)
        assertEquals("high", report.risk[0].severity)
        assertNotNull(report.risk[0].evidence)

        val verification = queue.verifications.first()
        assertEquals("l2", verification.listingId)
        assertTrue(verification.risk.isEmpty())
    }

    @Test
    fun moderationQueue_defaultsAllowEmptyQueue() {
        val dto = json.decodeFromString(
            ModerationQueueDto.serializer(),
            """{"counts": {}, "reports": [], "verifications": []}""",
        )
        val queue = dto.toModerationQueue()
        assertEquals(0, queue.openReports)
        assertEquals(0, queue.pendingVerifications)
        assertTrue(queue.reports.isEmpty())
        assertTrue(queue.verifications.isEmpty())
    }

    @Test
    fun queueListingInfo_addressJoinsWithPersianSeparator() {
        val dto = json.decodeFromString(
            QueueListingInfoDto.serializer(),
            """{"city": "تهران", "neighborhood": "جردن", "area_sqm": 90}""",
        )
        val info = dto.toQueueListingInfo()
        assertEquals("تهران، جردن", info.address)
        assertEquals(90, info.areaSqm)
    }
}
