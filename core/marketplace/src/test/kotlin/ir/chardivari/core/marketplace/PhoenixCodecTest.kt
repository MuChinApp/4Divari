package ir.chardivari.core.marketplace

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoenixCodecTest {

    @Test
    fun encodeJoin_targetsPostgresChangesWithFilter() {
        val encoded = PhoenixCodec.encodeJoin("1", "1", "conv-42")
        val arr = kotlinx.serialization.json.Json.parseToJsonElement(encoded).jsonArray
        assertEquals("1", arr[0].jsonPrimitive.content)
        assertEquals("1", arr[1].jsonPrimitive.content)
        assertEquals("realtime:postgres_changes", arr[2].jsonPrimitive.content)
        assertEquals("phx_join", arr[3].jsonPrimitive.content)
        val config = arr[4].jsonObject["config"]!!.jsonObject
        val changes = config["postgres_changes"]!!.jsonArray
        assertEquals(1, changes.size)
        val change = changes[0].jsonObject
        assertEquals("INSERT", change["event"]!!.jsonPrimitive.content)
        assertEquals("public", change["schema"]!!.jsonPrimitive.content)
        assertEquals("messages", change["table"]!!.jsonPrimitive.content)
        assertEquals(
            "conversation_id=eq.conv-42",
            change["filter"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun encodeHeartbeat_usesPhoenixTopic() {
        val encoded = PhoenixCodec.encodeHeartbeat("7")
        val arr = kotlinx.serialization.json.Json.parseToJsonElement(encoded).jsonArray
        assertEquals("", arr[0].jsonPrimitive.content)
        assertEquals("7", arr[1].jsonPrimitive.content)
        assertEquals("phoenix", arr[2].jsonPrimitive.content)
        assertEquals("phx heartbeat", arr[3].jsonPrimitive.content)
    }

    @Test
    fun decode_joinOkReply_isReplyOk() {
        val frame = PhoenixCodec.decode(
            """["1","1","realtime:postgres_changes","phx_reply",
               {"response":{"postgres_changes":[1]},"status":"ok"}]""".trimIndent(),
        )
        assertTrue(frame is RealtimeFrame.Reply)
        assertTrue((frame as RealtimeFrame.Reply).ok)
        assertEquals("realtime:postgres_changes", frame.topic)
    }

    @Test
    fun decode_insertRow_returnsRowInserted() {
        val frame = PhoenixCodec.decode(
            """["1","2","realtime:postgres_changes","postgres_changes",
               {"ids":[1],
                "data":{"type":"INSERT","table":"messages","schema":"public",
                        "record":{"id":"m1","conversation_id":"c1",
                                  "sender_id":"u1","body":"x",
                                  "sent_at":"2026-01-01T00:00:00+00:00"}}}]"""
                .trimIndent(),
        )
        assertTrue(frame is RealtimeFrame.RowInserted)
        frame as RealtimeFrame.RowInserted
        assertEquals("messages", frame.table)
        assertEquals(
            "m1",
            (frame.record["id"] as JsonPrimitive).content,
        )
        assertTrue(frame.record is JsonObject)
    }

    @Test
    fun decode_garbageOrNull_returnsNull() {
        assertNull(PhoenixCodec.decode("not json"))
        assertNull(PhoenixCodec.decode("""["1","1","topic"]""")) // too short
        assertNull(
            PhoenixCodec.decode(
                """["1","1","t","unknown",{"x":1}]""",
            ),
        )
    }

    @Test
    fun decode_updateRow_notEmitted() {
        val frame = PhoenixCodec.decode(
            """["1","2","realtime:postgres_changes","postgres_changes",
               {"ids":[1],
                "data":{"type":"UPDATE","table":"messages","schema":"public",
                        "record":{"id":"m1"}}}]""".trimIndent(),
        )
        assertNull(frame)
    }
}
