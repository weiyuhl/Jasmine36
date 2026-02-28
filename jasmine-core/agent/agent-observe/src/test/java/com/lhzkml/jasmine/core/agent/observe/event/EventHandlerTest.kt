package com.lhzkml.jasmine.core.agent.observe.event

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EventHandlerTest {

    @Test
    fun `NOOP handler does not throw`() = runBlocking {
        EventHandler.NOOP.fireAgentStarting(
            AgentStartingContext("r1", "a1", "gpt-4", 3)
        )
        EventHandler.NOOP.fireToolCallCompleted(
            ToolCallCompletedContext("r1", "tc1", "calc", "{}", "42")
        )
    }

    @Test
    fun `build registers and fires agent starting callback`() = runBlocking {
        var captured: AgentStartingContext? = null
        val handler = EventHandler.build {
            onAgentStarting { captured = it }
        }
        val ctx = AgentStartingContext("r1", "a1", "gpt-4", 5)
        handler.fireAgentStarting(ctx)
        assertEquals(ctx, captured)
    }

    @Test
    fun `multiple callbacks are chained in order`() = runBlocking {
        val log = mutableListOf<String>()
        val handler = EventHandler.build {
            onToolCallStarting { log.add("first:${it.toolName}") }
            onToolCallStarting { log.add("second:${it.toolName}") }
        }
        handler.fireToolCallStarting(
            ToolCallStartingContext("r1", "tc1", "calc", "{}")
        )
        assertEquals(listOf("first:calc", "second:calc"), log)
    }

    @Test
    fun `all event types fire correctly`() = runBlocking {
        var agentCompleted = false
        var agentFailed = false
        var agentClosing = false
        var strategyStarting = false
        var strategyCompleted = false
        var nodeStarting = false
        var nodeCompleted = false
        var nodeFailed = false
        var subgraphStarting = false
        var subgraphCompleted = false
        var subgraphFailed = false
        var llmStarting = false
        var llmCompleted = false
        var toolValidationFailed = false
        var toolFailed = false
        var toolCompleted = false
        var streamStarting = false
        var streamFrame = false
        var streamFailed = false
        var streamCompleted = false

        val handler = EventHandler.build {
            onAgentCompleted { agentCompleted = true }
            onAgentExecutionFailed { agentFailed = true }
            onAgentClosing { agentClosing = true }
            onStrategyStarting { strategyStarting = true }
            onStrategyCompleted { strategyCompleted = true }
            onNodeExecutionStarting { nodeStarting = true }
            onNodeExecutionCompleted { nodeCompleted = true }
            onNodeExecutionFailed { nodeFailed = true }
            onSubgraphExecutionStarting { subgraphStarting = true }
            onSubgraphExecutionCompleted { subgraphCompleted = true }
            onSubgraphExecutionFailed { subgraphFailed = true }
            onLLMCallStarting { llmStarting = true }
            onLLMCallCompleted { llmCompleted = true }
            onToolValidationFailed { toolValidationFailed = true }
            onToolCallFailed { toolFailed = true }
            onToolCallCompleted { toolCompleted = true }
            onLLMStreamingStarting { streamStarting = true }
            onLLMStreamingFrameReceived { streamFrame = true }
            onLLMStreamingFailed { streamFailed = true }
            onLLMStreamingCompleted { streamCompleted = true }
        }

        handler.fireAgentCompleted(AgentCompletedContext("r1", "a1", "done", 1))
        handler.fireAgentExecutionFailed(AgentExecutionFailedContext("r1", "a1", RuntimeException()))
        handler.fireAgentClosing(AgentClosingContext("r1", "a1"))
        handler.fireStrategyStarting(StrategyStartingContext("r1", "s1"))
        handler.fireStrategyCompleted(StrategyCompletedContext("r1", "s1", "ok"))
        handler.fireNodeExecutionStarting(NodeExecutionStartingContext("r1", "n1", null))
        handler.fireNodeExecutionCompleted(NodeExecutionCompletedContext("r1", "n1", null, "out"))
        handler.fireNodeExecutionFailed(NodeExecutionFailedContext("r1", "n1", null, RuntimeException()))
        handler.fireSubgraphExecutionStarting(SubgraphExecutionStartingContext("r1", "sg1", null))
        handler.fireSubgraphExecutionCompleted(SubgraphExecutionCompletedContext("r1", "sg1", "ok"))
        handler.fireSubgraphExecutionFailed(SubgraphExecutionFailedContext("r1", "sg1", RuntimeException()))
        handler.fireLLMCallStarting(LLMCallStartingContext("r1", "gpt-4", 5, listOf("calc")))
        handler.fireLLMCallCompleted(LLMCallCompletedContext("r1", "gpt-4", "resp", false, 0, 10, 20, 30))
        handler.fireToolValidationFailed(ToolValidationFailedContext("r1", "calc", "bad args"))
        handler.fireToolCallFailed(ToolCallFailedContext("r1", "tc1", "calc", "{}", RuntimeException()))
        handler.fireToolCallCompleted(ToolCallCompletedContext("r1", "tc1", "calc", "{}", "42"))
        handler.fireLLMStreamingStarting(LLMStreamingStartingContext("r1", "gpt-4", 5, listOf()))
        handler.fireLLMStreamingFrameReceived(LLMStreamingFrameReceivedContext("r1", "chunk"))
        handler.fireLLMStreamingFailed(LLMStreamingFailedContext("r1", "gpt-4", RuntimeException()))
        handler.fireLLMStreamingCompleted(LLMStreamingCompletedContext("r1", "gpt-4", "resp", false, 0, 10, 20, 30))

        assertTrue(agentCompleted)
        assertTrue(agentFailed)
        assertTrue(agentClosing)
        assertTrue(strategyStarting)
        assertTrue(strategyCompleted)
        assertTrue(nodeStarting)
        assertTrue(nodeCompleted)
        assertTrue(nodeFailed)
        assertTrue(subgraphStarting)
        assertTrue(subgraphCompleted)
        assertTrue(subgraphFailed)
        assertTrue(llmStarting)
        assertTrue(llmCompleted)
        assertTrue(toolValidationFailed)
        assertTrue(toolFailed)
        assertTrue(toolCompleted)
        assertTrue(streamStarting)
        assertTrue(streamFrame)
        assertTrue(streamFailed)
        assertTrue(streamCompleted)
    }
}
