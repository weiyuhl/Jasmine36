package com.lhzkml.jasmine.core.agent.tools.graph

import com.lhzkml.jasmine.core.agent.tools.ToolRegistry
import com.lhzkml.jasmine.core.agent.tools.trace.CallbackTraceWriter
import com.lhzkml.jasmine.core.agent.tools.trace.TraceEvent
import com.lhzkml.jasmine.core.agent.tools.trace.Tracing
import com.lhzkml.jasmine.core.prompt.llm.ChatClient
import com.lhzkml.jasmine.core.prompt.llm.LLMProvider
import com.lhzkml.jasmine.core.prompt.llm.LLMReadSession
import com.lhzkml.jasmine.core.prompt.llm.LLMWriteSession
import com.lhzkml.jasmine.core.prompt.llm.StreamResult
import com.lhzkml.jasmine.core.prompt.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GraphAgentTest {

    private fun createTestContext(
        client: ChatClient = FakeChatClient(),
        registry: ToolRegistry = ToolRegistry(),
        tracing: Tracing? = null
    ): AgentGraphContext {
        val p = prompt("t") { system("hi") }
        val session = LLMWriteSession(client, "test", p, emptyList())
        val readSession = LLMReadSession(client, "test", p, emptyList())
        return AgentGraphContext(
            agentId = "test", runId = "r1",
            client = client, model = "test",
            session = session, readSession = readSession,
            toolRegistry = registry,
            environment = GenericAgentEnvironment("test", registry),
            tracing = tracing
        )
    }

    // ========== AgentSubgraph ==========

    @Test
    fun `subgraph executes start to finish`() = runBlocking {
        val events = mutableListOf<TraceEvent>()
        val tracing = Tracing.build {
            addWriter(CallbackTraceWriter(callback = { events.add(it) }))
        }

        val strategy = graphStrategy<String, String>("simple") {
            val upper = node<String, String>("upper") { it.uppercase() }
            edge(nodeStart, upper)
            edge(upper, nodeFinish)
        }

        val ctx = createTestContext(tracing = tracing)
        val result = strategy.subgraph.execute(ctx, "hello")
        assertEquals("HELLO", result)

        val nodeStarts = events.filterIsInstance<TraceEvent.NodeExecutionStarting>()
        val nodeCompletes = events.filterIsInstance<TraceEvent.NodeExecutionCompleted>()
        assertTrue("Should have node start events", nodeStarts.isNotEmpty())
        assertTrue("Should have node complete events", nodeCompletes.isNotEmpty())
        assertTrue("Should have subgraph start", events.any { it is TraceEvent.SubgraphStarting })
        assertTrue("Should have subgraph complete", events.any { it is TraceEvent.SubgraphCompleted })
    }

    @Test
    fun `subgraph multi-node chain`() = runBlocking {
        val strategy = graphStrategy<Int, Int>("chain") {
            val double = node<Int, Int>("double") { it * 2 }
            val addTen = node<Int, Int>("addTen") { it + 10 }
            edge(nodeStart, double)
            edge(double, addTen)
            edge(addTen, nodeFinish)
        }

        val ctx = createTestContext()
        val result = strategy.subgraph.execute(ctx, 5)
        assertEquals(20, result) // (5 * 2) + 10
    }

    @Test(expected = IllegalStateException::class)
    fun `subgraph throws on no matching edge`() = runBlocking {
        val strategy = graphStrategy<String, String>("broken") {
            val orphan = node<String, String>("orphan") { it }
            edge(nodeStart, orphan)
        }

        val ctx = createTestContext()
        strategy.subgraph.execute(ctx, "input")
        Unit
    }

    // ========== AgentStrategy ==========

    @Test
    fun `strategy emits strategy-level trace events`() = runBlocking {
        val events = mutableListOf<TraceEvent>()
        val tracing = Tracing.build {
            addWriter(CallbackTraceWriter(callback = { events.add(it) }))
        }

        val strategy = graphStrategy<String, String>("traced") {
            val echo = node<String, String>("echo") { it }
            edge(nodeStart, echo)
            edge(echo, nodeFinish)
        }

        val ctx = createTestContext(tracing = tracing)
        strategy.execute(ctx, "hello")

        assertTrue("Should have StrategyStarting", events.any { it is TraceEvent.StrategyStarting })
        assertTrue("Should have StrategyCompleted", events.any { it is TraceEvent.StrategyCompleted })

        val startEvent = events.filterIsInstance<TraceEvent.StrategyStarting>().first()
        assertEquals("traced", startEvent.strategyName)
        assertNotNull(startEvent.graph)
    }

    // ========== conditionalEdge ==========

    @Test
    fun `conditional edge routes based on output`() = runBlocking {
        val strategy = graphStrategy<Int, String>("conditional") {
            val classify = node<Int, Int>("classify") { it }
            val positive = node<Int, String>("positive") { "positive: $it" }
            val negative = node<Int, String>("negative") { "negative: $it" }

            edge(nodeStart, classify)
            conditionalEdge(classify, positive) { output -> if (output > 0) output else null }
            conditionalEdge(classify, negative) { output -> if (output <= 0) output else null }
            edge(positive, nodeFinish)
            edge(negative, nodeFinish)
        }

        val ctx = createTestContext()
        assertEquals("positive: 5", strategy.subgraph.execute(ctx, 5))
        assertEquals("negative: -3", strategy.subgraph.execute(ctx, -3))
    }

    // ========== graphMetadata ==========

    @Test
    fun `strategy graphMetadata returns correct structure`() {
        val strategy = graphStrategy<String, String>("meta") {
            val a = node<String, String>("nodeA") { it }
            val b = node<String, String>("nodeB") { it }
            edge(nodeStart, a)
            edge(a, b)
            edge(b, nodeFinish)
        }

        val meta = strategy.graphMetadata()
        assertEquals(4, meta.nodes.size)
        assertEquals(3, meta.edges.size)
    }

    // ========== context storage ==========

    @Test
    fun `context storage shared between nodes`() = runBlocking {
        val testKey = AgentStorageKey<String>("key")
        val strategy = graphStrategy<String, String>("storage") {
            val writer = node<String, String>("writer") { input ->
                storage.set(testKey, input.uppercase())
                input
            }
            val reader = node<String, String>("reader") {
                storage.get(testKey) ?: "not found"
            }
            edge(nodeStart, writer)
            edge(writer, reader)
            edge(reader, nodeFinish)
        }

        val ctx = createTestContext()
        val result = strategy.subgraph.execute(ctx, "hello")
        assertEquals("HELLO", result)
    }

    // ========== Fake ChatClient ==========

    private class FakeChatClient : ChatClient {
        override val provider = LLMProvider.OpenAI

        override suspend fun chat(
            messages: List<ChatMessage>,
            model: String,
            maxTokens: Int?,
            samplingParams: SamplingParams?,
            tools: List<ToolDescriptor>,
            toolChoice: ToolChoice?
        ): String = "fake"

        override suspend fun chatWithUsage(
            messages: List<ChatMessage>,
            model: String,
            maxTokens: Int?,
            samplingParams: SamplingParams?,
            tools: List<ToolDescriptor>,
            toolChoice: ToolChoice?
        ): ChatResult = ChatResult(content = "fake", usage = Usage(0, 0, 0))

        override fun chatStream(
            messages: List<ChatMessage>,
            model: String,
            maxTokens: Int?,
            samplingParams: SamplingParams?,
            tools: List<ToolDescriptor>,
            toolChoice: ToolChoice?
        ): Flow<String> = flowOf("fake")

        override suspend fun chatStreamWithUsage(
            messages: List<ChatMessage>,
            model: String,
            maxTokens: Int?,
            samplingParams: SamplingParams?,
            tools: List<ToolDescriptor>,
            toolChoice: ToolChoice?,
            onChunk: suspend (String) -> Unit
        ): StreamResult = StreamResult(content = "fake", usage = Usage(0, 0, 0))

        override suspend fun listModels(): List<ModelInfo> = emptyList()

        override fun close() {}
    }
}
