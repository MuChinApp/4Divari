package ir.chardivari.core.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * OpenAI-compatible wire contract between the app and the Supabase Edge
 * Function `assistant` (which proxies a server-configured LLM provider).
 *
 * The function is a thin, key-holding proxy: it never executes tools.
 * Tools run in the client against real repositories with the user's RLS
 * session — AI never sees data the user could not fetch directly.
 */
@Serializable
data class AssistantWireRequest(
    val model: String? = null,
    val messages: List<WireMessage>,
    val tools: List<WireTool>,
)

@Serializable
data class WireMessage(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<WireToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null,
)

@Serializable
data class WireToolCall(
    val id: String,
    val function: WireFunctionCall,
)

@Serializable
data class WireFunctionCall(
    val name: String,
    /** JSON-encoded string (OpenAI convention) — parsed by the orchestrator. */
    val arguments: String,
)

@Serializable
data class WireTool(
    val type: String = "function",
    val function: WireToolSpec,
)

@Serializable
data class WireToolSpec(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

/** Slim response envelope returned by the edge function. */
@Serializable
data class AssistantWireResponse(
    val message: WireMessage? = null,
)
