package com.lhzkml.jasmine.core.agent.observe.trace

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TracingTest {

    @Test
    fun `Tracing build creates instance with writers`() {
        val events = mutableListOf<TraceEvent>()
        val tracing = Tracing.build {
            addWriter(CallbackTraceWriter(callback = { events.add(it) }))
        }

        runBlocking {
            tracing.emit(TraceEvent.AgentStarting(
                eventId = "e1", runId = "r1",
                agentId = "test-agent", model = "gpt-4", toolCount = 3
            ))
        }

        assertEquals(1, events.size)
        assertTrue(events[0] is TraceEvent.AgentStarting)
        assertEquals("test-agent", (events[0] as TraceEvent.AgentStarting).agentId)
    }

    @Test
    fun `Tracing NOOP does not emit`() {
        // 不应抛异常
        runBlocking {
            Tracing.NOOP.emit(TraceEvent.AgentStarting(
                eventId = "e1", runId = "r1",
                agentId = "test", model = "m", toolCount = 0
            ))
        }
    }

    @Test
    fun `newRunId generates unique IDs`() {
        val tracing = Tracing.build {}
        val ids = (1..100).map { tracing.newRunId() }.toSet()
        assertEquals(100, ids.size)
    }

    @Test
    fun `newEventId generates sequential IDs`() {
        val tracing = Tracing.build {}
        val id1 = tracing.newEventId()
        val id2 = tracing.newEventId()
        assertEquals("evt-1", id1)
        assertEquals("evt-2", id2)
    }

    @Test
    fun `multiple writers all receive events`() {
        val events1 = mutableListOf<TraceEvent>()
        val events2 = mutableListOf<TraceEvent>()

        val tracing = Tracing.build {
            addWriter(CallbackTraceWriter(callback = { events1.add(it) }))
            addWriter(CallbackTraceWriter(callback = { events2.add(it) }))
        }

        runBlocking {
            tracing.emit(TraceEvent.LLMCallStarting(
                eventId = "e1", runId = "r1",
                model = "gpt-4", messageCount = 5, tools = listOf("calc")
            ))
        }

        assertEquals(1, events1.size)
        assertEquals(1, events2.size)
    }

    @Test
    fun `writer exception does not propagate`() {
        val events = mutableListOf<TraceEvent>()
        val tracing = Tracing.build {
            addWriter(CallbackTraceWriter(callback = { throw RuntimeException("boom") }))
            addWriter(CallbackTraceWriter(callback = { events.add(it) }))
        }

        runBlocking {
            tracing.emit(TraceEvent.AgentCompleted(
                eventId = "e1", runId = "r1",
                agentId = "a", result = "ok", totalIterations = 1
            ))
        }

        // 第二个 writer 仍然收到事件
        assertEquals(1, events.size)
    }

    @Test
    fun `CallbackTraceWriter filter works`() {
        val events = mutableListOf<TraceEvent>()
        val writer = CallbackTraceWriter(
            callback = { events.add(it) },
            filter = { it is TraceEvent.ToolCallStarting }
        )

        runBlocking {
            writer.write(TraceEvent.AgentStarting(
                eventId = "e1", runId = "r1",
                agentId = "a", model = "m", toolCount = 0
            ))
            writer.write(TraceEvent.ToolCallStarting(
                eventId = "e2", runId = "r1",
                toolCallId = "tc1", toolName = "calc", toolArgs = "{}"
            ))
        }

        assertEquals(1, events.size)
        assertTrue(events[0] is TraceEvent.ToolCallStarting)
    }

    @Test
    fun `TraceMessageFormat formats all event types`() {
        val events = listOf(
            TraceEvent.AgentStarting("e1", "r1", "a1", "gpt-4", 3),
            TraceEvent.AgentCompleted("e2", "r1", "a1", "done", 2),
            TraceEvent.AgentFailed("e3", "r1", "a1", TraceError("err")),
            TraceEvent.LLMCallStarting("e4", "r1", "gpt-4", 5, listOf("calc")),
            TraceEvent.LLMCallCompleted("e5", "r1", "gpt-4", "resp", false, 0, 10, 20, 30),
            TraceEvent.LLMStreamStarting("e6", "r1", "gpt-4", 5, listOf("calc")),
            TraceEvent.LLMStreamFrame("e7", "r1", "hello"),
            TraceEvent.LLMStreamCompleted("e8", "r1", "gpt-4", "resp", false, 0, 10, 20, 30),
            TraceEvent.LLMStreamFailed("e9", "r1", "gpt-4", TraceError("err")),
            TraceEvent.ToolCallStarting("e10", "r1", "tc1", "calc", "{}"),
            TraceEvent.ToolCallCompleted("e11", "r1", "tc1", "calc", "{}", "42"),
            TraceEvent.ToolCallFailed("e12", "r1", "tc1", "calc", "{}", TraceError("err")),
            TraceEvent.CompressionStarting("e13", "r1", "WholeHistory", 20),
            TraceEvent.CompressionCompleted("e14", "r1", "WholeHistory", 5),
            // Strategy/Node/Subgraph 事件
            TraceEvent.StrategyStarting("e15", "r1", "my-strategy"),
            TraceEvent.StrategyCompleted("e16", "r1", "my-strategy", "success"),
            TraceEvent.NodeExecutionStarting("e17", "r1", "processInput", "hello"),
            TraceEvent.NodeExecutionCompleted("e18", "r1", "processInput", "hello", "processed"),
            TraceEvent.NodeExecutionFailed("e19", "r1", "processInput", "hello", TraceError("err")),
            TraceEvent.SubgraphStarting("e20", "r1", "sub1", "input"),
            TraceEvent.SubgraphCompleted("e21", "r1", "sub1", "result"),
            TraceEvent.SubgraphFailed("e22", "r1", "sub1", TraceError("err"))
        )

        for (event in events) {
            val formatted = TraceMessageFormat.format(event)
            assertTrue("Format should not be empty for ${event::class.simpleName}", formatted.isNotEmpty())
            assertTrue("Format should contain time bracket for ${event::class.simpleName}", formatted.startsWith("["))
        }
    }

    @Test
    fun `TraceError from throwable`() {
        val error = TraceError.from(RuntimeException("test error", IllegalStateException("cause")))
        assertEquals("test error", error.message)
        assertNotNull(error.stackTrace)
        assertEquals("cause", error.cause)
    }
}
