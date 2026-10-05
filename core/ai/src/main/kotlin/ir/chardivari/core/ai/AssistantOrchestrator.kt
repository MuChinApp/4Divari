package ir.chardivari.core.ai

import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tool-calling orchestrator (master prompt: AI = feature layer over real
 * data).
 *
 * Loop: system prompt → transport → tool calls? → whitelist args
 * (FairnessGuard) → execute against real repositories → feed JSON evidence
 * back → … → final explanation. Artifacts (intent, price band, comparison,
 * sources) are collected from tool EXECUTIONS, so the UI only ever renders
 * data that was actually fetched under the user's session.
 */
interface AssistantEngine {
    suspend fun ask(
        priorTurns: List<PriorTurn>,
        userText: String,
    ): AppResult<AssistantTurn>
}

@Singleton
class AssistantOrchestrator @Inject constructor(
    private val transport: AiTransport,
    private val registry: AssistantToolRegistry,
    private val fairness: FairnessGuard,
    private val json: Json,
) : AssistantEngine {

    override suspend fun ask(
        priorTurns: List<PriorTurn>,
        userText: String,
    ): AppResult<AssistantTurn> {
        val protectedHits = fairness.detectProtectedCriteria(userText)
        val fairnessNotice =
            if (protectedHits.isNotEmpty()) FairnessGuard.FAIRNESS_NOTICE else null

        val messages = mutableListOf<WireMessage>()
        messages += WireMessage(role = "system", content = SYSTEM_PROMPT)
        priorTurns.takeLast(MAX_HISTORY_TURNS).forEach { turn ->
            messages += WireMessage(role = "user", content = turn.userText)
            messages += WireMessage(role = "assistant", content = turn.assistantText)
        }
        messages += WireMessage(role = "user", content = userText)

        val specs = registry.specs()
        val wireTools = specs.map { spec ->
            WireTool(
                function = WireToolSpec(
                    name = spec.name,
                    description = spec.description,
                    parameters = spec.parameters,
                ),
            )
        }

        var intent: DetectedIntent? = null
        var priceRange: PriceRangeEstimate? = null
        var comparison: ListingComparison? = null
        val sources = linkedSetOf<AssistantSource>()
        val toolsUsed = linkedSetOf<String>()

        repeat(MAX_TOOL_ROUNDS + 1) {
            when (val response = transport.chat(
                AssistantWireRequest(messages = messages.toList(), tools = wireTools),
            )) {
                is AppResult.Failure -> return response
                AppResult.Empty -> return AppResult.Failure(AppError.Unexpected)
                is AppResult.Success -> {
                    val message = response.data.message
                        ?: return AppResult.Failure(
                            AppError.Client(code = 0, serverMessage = "assistant_empty_response"),
                        )
                    val calls = message.toolCalls.orEmpty()
                        .filter { it.function.name.isNotBlank() }
                    if (calls.isEmpty()) {
                        val text = message.content?.takeIf { it.isNotBlank() }
                            ?: synthesizedText(intent, priceRange, comparison)
                        return AppResult.Success(
                            AssistantTurn(
                                text = text,
                                intent = intent,
                                priceRange = priceRange,
                                comparison = comparison,
                                sources = sources.toList(),
                                fairnessNotice = fairnessNotice,
                                toolsUsed = toolsUsed.toList(),
                            ),
                        )
                    }
                    messages += message
                    for (call in calls) {
                        val name = call.function.name
                        val spec = specs.find { it.name == name }
                        if (spec == null) {
                            messages += WireMessage(
                                role = "tool",
                                toolCallId = call.id,
                                name = name,
                                content = UNKNOWN_TOOL_JSON,
                            )
                            continue
                        }
                        val rawArgs = parseArguments(call.function.arguments)
                        val (cleanArgs, _) =
                            fairness.sanitizeArguments(rawArgs, spec.allowedKeys)
                        when (val executed = registry.execute(name, cleanArgs)) {
                            is AppResult.Failure -> return executed
                            AppResult.Empty -> messages += WireMessage(
                                role = "tool",
                                toolCallId = call.id,
                                name = name,
                                content = EMPTY_TOOL_JSON,
                            )
                            is AppResult.Success -> {
                                intent = executed.data.intent ?: intent
                                priceRange = executed.data.priceRange ?: priceRange
                                comparison = executed.data.comparison ?: comparison
                                sources += executed.data.sources
                                toolsUsed += name
                                messages += WireMessage(
                                    role = "tool",
                                    toolCallId = call.id,
                                    name = name,
                                    content = executed.data.json.toString(),
                                )
                            }
                        }
                    }
                }
            }
        }
        return AppResult.Failure(
            AppError.Client(code = 0, serverMessage = "assistant_tool_loop_limit"),
        )
    }

    private fun parseArguments(raw: String): JsonObject =
        runCatching { json.parseToJsonElement(raw).jsonObject }
            .getOrElse { JsonObject(emptyMap()) }

    private fun synthesizedText(
        intent: DetectedIntent?,
        priceRange: PriceRangeEstimate?,
        comparison: ListingComparison?,
    ): String = when {
        priceRange != null ->
            "بازهٔ تخمینی از ${priceRange.sampleSize} نمونهٔ واقعی آماده شد؛ جزئیات را ببینید."
        intent != null ->
            "نتایج جست‌وجو از داده‌های واقعی آماده شد؛ فیلترها و فهرست را ببینید."
        comparison != null ->
            "مقایسه از داده‌های واقعی ملک‌ها آماده شد؛ جدول را ببینید."
        else -> "پاسخ ساختاریافته از ابزارهای دادهٔ واقعی آماده شد."
    }

    companion object {
        const val MAX_TOOL_ROUNDS = 4
        const val MAX_HISTORY_TURNS = 6

        private const val UNKNOWN_TOOL_JSON = "{\"error\":\"unknown_tool\"}"
        private const val EMPTY_TOOL_JSON = "{\"error\":\"empty\"}"

        val SYSTEM_PROMPT = """
            تو «دستیار چاردیواری» هستی؛ دستیار جست‌وجوی ملک برای اپلیکیشن چاردیواری.
            فقط با خروجی ابزارها و شواهد واقعی پاسخ بده.
            قواعد سخت:
            - هرگز قیمت، متراژ، موجود بودن یا هر اطلاعات ملکی را بدون ابزار نساز.
              اگر ابزاری اجرا نشده یا داده کافی نیست، عدم قطعیت را صریحاً بگو.
            - قیمت‌ها را به ریال بیاور و بازه‌ها را «تخمینی» بنام؛ تعداد نمونه را ذکر کن.
            - معیارهای تفکیک‌کنندهٔ افراد (جنسیت، مذهب، تابعیت، قومیت، وضعیت تأهل، سن)
              را نپذیر و مؤدبانه رد کن؛ معیارها فقط ویژگی ملک‌اند.
            - به شناسهٔ ملک یا نام ابزار استناد کن.
            - پاسخ کوتاه و فارسی بده؛ اگر چیزی را نمی‌دانی بگو نمی‌دانم.
        """.trimIndent()
    }
}
