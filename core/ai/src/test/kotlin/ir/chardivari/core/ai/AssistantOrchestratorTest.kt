package ir.chardivari.core.ai

import com.google.common.truth.Truth.assertThat
import ir.chardivari.core.common.AppError
import ir.chardivari.core.common.AppResult
import ir.chardivari.core.marketplace.SearchFilters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantOrchestratorTest {

    /* ----------------------------- fakes ------------------------------ */

    private class FakeTransport(
        responses: List<AppResult<AssistantWireResponse>>,
    ) : AiTransport {
        val requests = mutableListOf<AssistantWireRequest>()
        private val queue = ArrayDeque(responses)

        override suspend fun chat(
            request: AssistantWireRequest,
        ): AppResult<AssistantWireResponse> {
            requests += request
            return queue.removeFirstOrNull()
                ?: AppResult.Failure(AppError.Unexpected)
        }
    }

    private class FakeRegistry(
        private val toolSpecs: List<AiToolSpec>,
        private val results: Map<String, AppResult<ToolResult>>,
    ) : AssistantToolRegistry {
        val executed = mutableListOf<Pair<String, JsonObject>>()

        override fun specs(): List<AiToolSpec> = toolSpecs

        override suspend fun execute(
            name: String,
            args: JsonObject,
        ): AppResult<ToolResult> {
            executed += name to args
            return results[name] ?: AppResult.Failure(AppError.Unexpected)
        }
    }

    /* ---------------------------- builders ---------------------------- */

    private fun answer(
        content: String?,
        toolCalls: List<WireToolCall>? = null,
    ): AppResult<AssistantWireResponse> = AppResult.Success(
        AssistantWireResponse(
            message = WireMessage(
                role = "assistant",
                content = content,
                toolCalls = toolCalls,
            ),
        ),
    )

    private fun toolCall(name: String, arguments: String): WireToolCall = WireToolCall(
        id = "call-1",
        function = WireFunctionCall(name = name, arguments = arguments),
    )

    private fun searchSpec(): AiToolSpec = AiToolSpec(
        name = AiTools.SEARCH,
        description = "search",
        parameters = buildJsonObject {
            put(
                "properties",
                buildJsonObject {
                    put("city", buildJsonObject { put("type", "string") })
                    put("max_price_rial", buildJsonObject { put("type", "integer") })
                },
            )
        },
    )

    private fun orchestrator(
        transport: FakeTransport,
        registry: FakeRegistry,
    ): AssistantOrchestrator = AssistantOrchestrator(
        transport = transport,
        registry = registry,
        fairness = FairnessGuard(),
        json = Json,
    )

    /* ------------------------------ tests ----------------------------- */

    @Test
    fun `direct answer returns text with no artifacts`() = runTestLike {
        val transport = FakeTransport(listOf(answer("سلام! کمک می‌خواهید؟")))
        val registry = FakeRegistry(listOf(searchSpec()), emptyMap())

        val result = orchestrator(transport, registry)
            .ask(priorTurns = emptyList(), userText = "سلام")

        val turn = requireSuccess(result)
        assertThat(turn.text).isEqualTo("سلام! کمک می‌خواهید؟")
        assertNull(turn.intent)
        assertNull(turn.priceRange)
        assertThat(turn.toolsUsed).isEmpty()
        assertThat(transport.requests).hasSize(1)
    }

    @Test
    fun `search round whitelists args and records intent and sources`() = runTestLike {
        val transport = FakeTransport(
            listOf(
                answer(
                    content = null,
                    toolCalls = listOf(
                        toolCall(
                            name = AiTools.SEARCH,
                            arguments =
                                """{"city":"تهران","nationality":"x","max_price_rial":"50"}""",
                        ),
                    ),
                ),
                answer("۲ نتیجه پیدا شد."),
            ),
        )
        val registry = FakeRegistry(
            toolSpecs = listOf(searchSpec()),
            results = mapOf(
                AiTools.SEARCH to AppResult.Success(
                    ToolResult(
                        json = buildJsonObject { put("count", 1) },
                        intent = DetectedIntent(
                            filters = SearchFilters(city = "تهران"),
                            resultCount = 1,
                            cards = emptyList(),
                        ),
                        sources = listOf(
                            AssistantSource("tool", AiTools.SEARCH, "جست‌وجوی ملک"),
                        ),
                    ),
                ),
            ),
        )

        val result = orchestrator(transport, registry)
            .ask(priorTurns = emptyList(), userText = "آپارتمان در تهران")

        val turn = requireSuccess(result)
        assertThat(turn.text).isEqualTo("۲ نتیجه پیدا شد.")
        assertThat(turn.intent?.filters?.city).isEqualTo("تهران")
        assertThat(turn.toolsUsed).containsExactly(AiTools.SEARCH)
        assertThat(turn.sources).isNotEmpty()

        val executedArgs = registry.executed.single().second
        assertThat(executedArgs.keys).containsExactly("city", "max_price_rial")
        assertThat(executedArgs.keys).doesNotContain("nationality")

        val second = transport.requests[1]
        val toolMessages = second.messages.filter { it.role == "tool" }
        assertThat(toolMessages).hasSize(1)
        assertThat(toolMessages.single().toolCallId).isEqualTo("call-1")
    }

    @Test
    fun `protected criteria attach fairness notice`() = runTestLike {
        val transport = FakeTransport(listOf(answer("متوجه شدم؛ معیارها فقط ملکی است.")))
        val registry = FakeRegistry(listOf(searchSpec()), emptyMap())

        val turn = requireSuccess(
            orchestrator(transport, registry)
                .ask(priorTurns = emptyList(), userText = "فقط به زنان بفروشید"),
        )

        assertThat(turn.fairnessNotice).isEqualTo(FairnessGuard.FAIRNESS_NOTICE)
    }

    @Test
    fun `clean question has no fairness notice`() = runTestLike {
        val transport = FakeTransport(listOf(answer("ok")))
        val registry = FakeRegistry(listOf(searchSpec()), emptyMap())

        val turn = requireSuccess(
            orchestrator(transport, registry)
                .ask(priorTurns = emptyList(), userText = "آپارتمان دو خوابه در اصفهان"),
        )

        assertNull(turn.fairnessNotice)
    }

    @Test
    fun `price artifact surfaces from tool result`() = runTestLike {
        val estimate = PriceRangeEstimate(
            basis = "price_rial",
            dealType = ir.chardivari.core.marketplace.DealType.SALE,
            city = "تهران",
            minRial = 1_000_000_000,
            p25Rial = 2_000_000_000,
            medianRial = 3_000_000_000,
            p75Rial = 4_000_000_000,
            maxRial = 5_000_000_000,
            sampleSize = 12,
            confidence = AiConfidence.MEDIUM,
            insufficientSamples = false,
        )
        val priceSpec = AiToolSpec(
            name = AiTools.PRICE,
            description = "price",
            parameters = buildJsonObject {
                put("properties", buildJsonObject { put("city", buildJsonObject { put("type", "string") }) })
            },
        )
        val transport = FakeTransport(
            listOf(
                answer(
                    content = null,
                    toolCalls = listOf(
                        toolCall(AiTools.PRICE, """{"city":"تهران"}"""),
                    ),
                ),
                answer("بازه تخمینی آماده است."),
            ),
        )
        val registry = FakeRegistry(
            toolSpecs = listOf(priceSpec),
            results = mapOf(
                AiTools.PRICE to AppResult.Success(
                    ToolResult(
                        json = buildJsonObject { put("sample_size", 12) },
                        priceRange = estimate,
                    ),
                ),
            ),
        )

        val turn = requireSuccess(
            orchestrator(transport, registry)
                .ask(priorTurns = emptyList(), userText = "قیمت تهران؟"),
        )

        assertThat(turn.priceRange).isEqualTo(estimate)
        assertThat(turn.toolsUsed).containsExactly(AiTools.PRICE)
    }

    @Test
    fun `transport failure propagates`() = runTestLike {
        val transport = FakeTransport(listOf(AppResult.Failure(AppError.Server)))
        val registry = FakeRegistry(listOf(searchSpec()), emptyMap())

        val result = orchestrator(transport, registry)
            .ask(priorTurns = emptyList(), userText = "سلام")

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        val failure = result as AppResult.Failure
        assertThat(failure.error).isEqualTo(AppError.Server)
    }

    @Test
    fun `endless tool requests hit the loop limit`() = runTestLike {
        val responses = (0..AssistantOrchestrator.MAX_TOOL_ROUNDS)
            .map {
                answer(
                    content = null,
                    toolCalls = listOf(toolCall(AiTools.SEARCH, """{"city":"تهران"}""")),
                )
            }
        val transport = FakeTransport(responses)
        val registry = FakeRegistry(
            toolSpecs = listOf(searchSpec()),
            results = mapOf(
                AiTools.SEARCH to AppResult.Success(
                    ToolResult(json = buildJsonObject { put("count", 0) }),
                ),
            ),
        )

        val result = orchestrator(transport, registry)
            .ask(priorTurns = emptyList(), userText = "جست‌وجو")

        val failure = result as AppResult.Failure
        assertThat(failure.error).isInstanceOf(AppError.Client::class.java)
        val client = failure.error as AppError.Client
        assertThat(client.serverMessage).isEqualTo("assistant_tool_loop_limit")
        assertThat(transport.requests).hasSize(AssistantOrchestrator.MAX_TOOL_ROUNDS + 1)
    }

    @Test
    fun `blank final content falls back to synthesized text`() = runTestLike {
        val transport = FakeTransport(
            listOf(
                answer(
                    content = null,
                    toolCalls = listOf(toolCall(AiTools.SEARCH, "{}")),
                ),
                answer(content = "   "),
            ),
        )
        val registry = FakeRegistry(
            toolSpecs = listOf(searchSpec()),
            results = mapOf(
                AiTools.SEARCH to AppResult.Success(
                    ToolResult(
                        json = buildJsonObject { put("count", 3) },
                        intent = DetectedIntent(
                            filters = SearchFilters.EMPTY,
                            resultCount = 3,
                            cards = emptyList(),
                        ),
                    ),
                ),
            ),
        )

        val turn = requireSuccess(
            orchestrator(transport, registry)
                .ask(priorTurns = emptyList(), userText = "جست‌وجو"),
        )

        assertThat(turn.text).isNotEmpty()
        assertThat(turn.text).contains("داده")
    }

    @Test
    fun `history is sent as prior turns`() = runTestLike {
        val transport = FakeTransport(listOf(answer("پاسخ دوم")))
        val registry = FakeRegistry(listOf(searchSpec()), emptyMap())

        orchestrator(transport, registry).ask(
            priorTurns = listOf(PriorTurn("سوال اول", "پاسخ اول")),
            userText = "سوال دوم",
        )

        val sent = transport.requests.single().messages
        assertThat(sent.any { it.role == "user" && it.content == "سوال اول" }).isTrue()
        assertThat(sent.any { it.role == "assistant" && it.content == "پاسخ اول" }).isTrue()
        assertThat(sent.last().content).isEqualTo("سوال دوم")
    }

    private fun requireSuccess(result: AppResult<AssistantTurn>): AssistantTurn {
        check(result is AppResult.Success) { "expected Success, was $result" }
        return result.data
    }

    /** Plain-JVM helper — no android Main dispatcher touched by these tests. */
    private fun runTestLike(block: suspend () -> Unit) {
        kotlinx.coroutines.runBlocking { block() }
    }
}
