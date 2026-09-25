package ir.chardivari.core.marketplace

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunicationPayloadTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun conversationRow_parsesAndMapsToItem() {
        val dto = json.decodeFromString(
            ConversationRowDto.serializer(),
            """
            {
              "conversation_id": "c1",
              "listing_id": "l1",
              "counterpart_id": "u2",
              "unread_count": 3,
              "last_message": {
                "body": "سلام",
                "sent_at": "2026-01-01T10:00:00+00:00",
                "sender_id": "u2"
              },
              "listing_status": "ACTIVE",
              "deal_type": "SALE",
              "price_rial": 5000000000,
              "city": "تهران",
              "neighborhood": "ونک",
              "area_sqm": 120
            }
            """.trimIndent(),
        )
        val item = dto.toConversationItem()
        assertEquals("c1", item.conversationId)
        assertEquals(3, item.unreadCount)
        assertEquals("سلام", item.lastMessageBody)
        assertEquals(DealType.SALE, item.dealType)
        assertEquals("تهران، ونک", item.address)
        assertTrue(item.listingStatus == "ACTIVE")
    }

    @Test
    fun messageDto_deletedMessage_neutralizesBody() {
        val dto = json.decodeFromString(
            MessageDto.serializer(),
            """
            {
              "id": "m1",
              "conversation_id": "c1",
              "sender_id": "u1",
              "body": "secret",
              "sent_at": "2026-01-01T10:00:00+00:00",
              "deleted_at": "2026-01-01T11:00:00+00:00"
            }
            """.trimIndent(),
        )
        val message = dto.toChatMessage(currentUserId = "u1")
        assertNull(message.body)
        assertTrue(message.isDeleted)
        assertTrue(message.isMine)
    }

    @Test
    fun messageDto_otherSender_isNotMine() {
        val dto = json.decodeFromString(
            MessageDto.serializer(),
            """
            {
              "id": "m2",
              "conversation_id": "c1",
              "sender_id": "u2",
              "body": "عرض",
              "sent_at": "2026-01-01T10:00:00+00:00"
            }
            """.trimIndent(),
        )
        val message = dto.toChatMessage(currentUserId = "u1")
        assertFalse(message.isMine)
        assertEquals("عرض", message.body)
        assertFalse(message.isDeleted)
    }

    @Test
    fun notificationDto_parsesJsonbDataAndReadState() {
        val dto = json.decodeFromString(
            NotificationDto.serializer(),
            """
            {
              "id": "n1",
              "type": "chat_message",
              "title": "new_message",
              "body": "سلام",
              "data": {"conversation_id": "c1"},
              "read_at": null,
              "created_at": "2026-01-01T10:00:00+00:00"
            }
            """.trimIndent(),
        )
        assertEquals("new_message", dto.title)
        assertNull(dto.readAt)
        assertNotNull(dto.data)
    }

    @Test
    fun myVisitDto_mapsAddressAndAgent() {
        val dto = json.decodeFromString(
            MyVisitDto.serializer(),
            """
            {
              "id": "v1",
              "listing_id": "l1",
              "slot_start": "2026-01-02T10:00:00+03:30",
              "slot_end": "2026-01-02T11:00:00+03:30",
              "status": "REQUESTED",
              "listing": {
                "id": "l1",
                "deal_type": "SALE",
                "price_rial": 100,
                "property": {
                  "property_type": "APARTMENT",
                  "area_sqm": 90,
                  "city": "تهران",
                  "neighborhood": "جردن"
                }
              },
              "agent": {
                "id": "u9",
                "phone_e164": "+989121111111",
                "profiles": {"display_name": "علی"}
              }
            }
            """.trimIndent(),
        )
        val visit = dto.toMyVisit()
        assertEquals("v1", visit.id)
        assertEquals("تهران، جردن", visit.address)
        assertEquals("علی", visit.agentName)
        assertEquals("+989121111111", visit.agentPhone)
        assertEquals("REQUESTED", visit.status)
    }
}
