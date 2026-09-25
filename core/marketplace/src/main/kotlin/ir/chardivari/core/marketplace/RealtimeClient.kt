package ir.chardivari.core.marketplace

import ir.chardivari.core.environment.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.Closeable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase Realtime — Phoenix channels wire format (`vsn=1.0.0`).
 *
 * Pure codec so framing is unit-testable without a live socket. The transport
 * is an optimization only: chat always works through REST polling; when the
 * socket drops we emit [RealtimeEvent.Disconnected] and the UI keeps polling.
 */
object PhoenixCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /** `["<join_ref>","<ref>","<topic>","phx_join",{"config":{...}}]` */
    fun encodeJoin(joinRef: String, ref: String, conversationId: String): String =
        json.encodeToString(
            JsonArray.serializer(),
            JsonArray(
                listOf(
                    JsonPrimitive(joinRef),
                    JsonPrimitive(ref),
                    JsonPrimitive("realtime:postgres_changes"),
                    JsonPrimitive("phx_join"),
                    JsonObject(
                        mapOf(
                            "config" to JsonObject(
                                mapOf(
                                    "postgres_changes" to kotlinx.serialization.json.buildJsonArray {
                                        add(
                                            JsonObject(
                                                mapOf(
                                                    "event" to JsonPrimitive("INSERT"),
                                                    "schema" to JsonPrimitive("public"),
                                                    "table" to JsonPrimitive("messages"),
                                                    "filter" to JsonPrimitive(
                                                        "conversation_id=eq.$conversationId",
                                                    ),
                                                ),
                                            ),
                                        )
                                    },
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

    /** `["","<ref>","phoenix","phx heartbeat",{}]` */
    fun encodeHeartbeat(ref: String): String =
        json.encodeToString(
            JsonArray.serializer(),
            JsonArray(
                listOf(
                    JsonPrimitive(""),
                    JsonPrimitive(ref),
                    JsonPrimitive("phoenix"),
                    JsonPrimitive("phx heartbeat"),
                    JsonObject(emptyMap()),
                ),
            ),
        )

    /** Parse one inbound frame; returns null for anything we don't care about. */
    fun decode(text: String): RealtimeFrame? = try {
        val arr = json.parseToJsonElement(text).jsonArray
        if (arr.size < 5) return null
        val topic = (arr[2] as? JsonPrimitive)?.content ?: return null
        val event = (arr[3] as? JsonPrimitive)?.content ?: return null
        val payload = arr[4] as? JsonObject ?: return null
        when {
            event == "phx_reply" -> {
                val status = payload["status"]?.let { (it as? JsonPrimitive)?.content }
                RealtimeFrame.Reply(topic = topic, ok = status == "ok")
            }
            event == "postgrex" || event == "postgres_changes" -> {
                val data = payload["data"] as? JsonObject ?: return null
                val type = data["type"]?.let { (it as? JsonPrimitive)?.content }
                val table = data["table"]?.let { (it as? JsonPrimitive)?.content }
                val record = data["record"] as? JsonObject
                if (type == "INSERT" && record != null && table != null) {
                    RealtimeFrame.RowInserted(table = table, record = record)
                } else {
                    null
                }
            }
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

sealed interface RealtimeFrame {
    data class Reply(val topic: String, val ok: Boolean) : RealtimeFrame
    data class RowInserted(val table: String, val record: JsonObject) : RealtimeFrame
}

sealed interface RealtimeEvent {
    data object Connected : RealtimeEvent
    data object Disconnected : RealtimeEvent
    data class MessageInserted(val record: JsonObject) : RealtimeEvent
}

/** One-shot subscription: postgres_changes INSERT on one conversation's messages. */
fun interface RealtimeSubscription : Closeable

/** Transport seam so view-models can be tested with a fake socket. */
interface RealtimeClient {
    fun subscribeMessages(
        conversationId: String,
        onEvent: (RealtimeEvent) -> Unit,
    ): RealtimeSubscription
}

@Singleton
class SupabaseRealtimeClient @Inject constructor(
    private val config: AppConfig,
    private val client: OkHttpClient,
) : RealtimeClient {

    /**
     * Opens the socket and joins the conversation channel. [onEvent] is
     * invoked from the socket/heartbeat threads — callers must hop.
     */
    override fun subscribeMessages(
        conversationId: String,
        onEvent: (RealtimeEvent) -> Unit,
    ): RealtimeSubscription {
        val url = wsUrl()
        if (url == null) {
            onEvent(RealtimeEvent.Disconnected)
            return RealtimeSubscription { }
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var socket: WebSocket? = null
        var joined = false
        var heartbeatJob: Job? = null

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(
                    PhoenixCodec.encodeJoin(
                        joinRef = "1",
                        ref = "1",
                        conversationId = conversationId,
                    ),
                )
                heartbeatJob = scope.launch {
                    var ref = 2
                    while (isActive) {
                        delay(30_000)
                        webSocket.send(PhoenixCodec.encodeHeartbeat(ref.toString()))
                        ref++
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                when (val frame = PhoenixCodec.decode(text)) {
                    is RealtimeFrame.Reply -> {
                        if (frame.ok && !joined) {
                            joined = true
                            onEvent(RealtimeEvent.Connected)
                        }
                    }
                    is RealtimeFrame.RowInserted -> {
                        if (frame.table == "messages") {
                            onEvent(RealtimeEvent.MessageInserted(frame.record))
                        }
                    }
                    null -> Unit
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onEvent(RealtimeEvent.Disconnected)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onEvent(RealtimeEvent.Disconnected)
            }
        }

        val request = Request.Builder().url(url).build()
        socket = client.newWebSocket(request, listener)

        return RealtimeSubscription {
            heartbeatJob?.cancel()
            socket?.cancel()
            scope.cancel()
        }
    }

    private fun wsUrl(): String? {
        val rest = config.requireRest() ?: return null
        val (httpsBase, key) = rest
        val wsBase = httpsBase.removePrefix("https://").removePrefix("http://")
        return "wss://$wsBase/realtime/v1/websocket?apikey=$key&vsn=1.0.0"
    }
}
