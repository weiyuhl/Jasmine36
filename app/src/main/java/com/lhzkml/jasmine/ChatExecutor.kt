package com.lhzkml.jasmine

import android.content.Context
import com.lhzkml.jasmine.core.agent.graph.graph.AgentGraphContext
import com.lhzkml.jasmine.core.agent.graph.graph.GenericAgentEnvironment
import com.lhzkml.jasmine.core.agent.graph.graph.GraphAgent
import com.lhzkml.jasmine.core.agent.graph.graph.PredefinedStrategies
import com.lhzkml.jasmine.core.agent.graph.graph.ToolCalls
import com.lhzkml.jasmine.core.agent.graph.graph.ToolSelectionStrategy
import com.lhzkml.jasmine.core.agent.observe.event.*
import com.lhzkml.jasmine.core.agent.observe.snapshot.Persistence
import com.lhzkml.jasmine.core.agent.observe.trace.Tracing
import com.lhzkml.jasmine.core.agent.planner.SimpleLLMPlanner
import com.lhzkml.jasmine.core.agent.planner.SimpleLLMWithCriticPlanner
import com.lhzkml.jasmine.core.agent.tools.AgentEventListener
import com.lhzkml.jasmine.core.agent.tools.ToolExecutor
import com.lhzkml.jasmine.core.agent.tools.ToolRegistry
import com.lhzkml.jasmine.core.config.ActiveProviderConfig
import com.lhzkml.jasmine.core.prompt.llm.ChatClient
import com.lhzkml.jasmine.core.prompt.llm.ChatClientException
import com.lhzkml.jasmine.core.prompt.llm.ContextManager
import com.lhzkml.jasmine.core.prompt.llm.ErrorType
import com.lhzkml.jasmine.core.prompt.llm.LLMWriteSession
import com.lhzkml.jasmine.core.prompt.llm.StreamResumeHelper
import com.lhzkml.jasmine.core.prompt.llm.chatStreamWithUsageAndThinking
import com.lhzkml.jasmine.core.prompt.llm.SystemContextCollector
import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.Prompt
import com.lhzkml.jasmine.core.prompt.model.Usage
import com.lhzkml.jasmine.core.conversation.storage.ConversationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 聊天执行器：封装 sendMessage 的后台 IO 逻辑。
 * 从 MainActivity 中提取，降低 Activity 复杂度。
 */
class ChatExecutor(
    private val context: Context,
    private val chatStateManager: ChatStateManager,
    private val conversationRepo: ConversationRepository,
    private val contextCollector: () -> SystemContextCollector,
    private val contextManager: () -> ContextManager,
    private val currentConversationId: () -> String?,
    private val setConversationId: (String) -> Unit,
    private val messageHistory: MutableList<ChatMessage>,
    private val buildToolRegistry: suspend () -> ToolRegistry,
    private val loadMcpTools: suspend (ToolRegistry) -> Unit,
    private val refreshContextCollector: () -> Unit,
    private val buildTracing: () -> Tracing?,
    private val setTracing: (Tracing?) -> Unit,
    private val getTracing: () -> Tracing?,
    private val buildEventHandler: () -> EventHandler?,
    private val buildPersistence: () -> Persistence?,
    private val getPersistence: () -> Persistence?,
    private val setPersistence: (Persistence?) -> Unit,
    private val tryOfferCheckpointRecovery: suspend (Exception, String) -> Unit,
    private val tryCompressHistory: suspend (ChatClient, String) -> Unit,
    private val onUpdateButtonState: suspend (Boolean) -> Unit
) {

    /**
     * 在 IO 线程执行聊天逻辑（Agent 模式或普通模式）。
     * 调用方应在协程中调用此方法。
     */
    suspend fun execute(
        message: String,
        userMsg: ChatMessage,
        client: ChatClient,
        config: ActiveProviderConfig
    ) {
        try {
            val toolsEnabled = ProviderManager.isToolsEnabled(context)

            val registry = if (toolsEnabled) {
                val r = buildToolRegistry()
                loadMcpTools(r)
                r
            } else null

            refreshContextCollector()

            if (currentConversationId() == null) {
                val title = if (message.length > 20) message.substring(0, 20) + "..." else message
                val basePrompt = ProviderManager.getDefaultSystemPrompt(context)
                val systemPrompt = contextCollector().buildSystemPrompt(basePrompt)
                val convId = conversationRepo.createConversation(
                    title = title,
                    providerId = config.providerId,
                    model = config.model,
                    systemPrompt = systemPrompt,
                    workspacePath = if (ProviderManager.isAgentMode(context))
                        ProviderManager.getWorkspacePath(context) else ""
                )
                setConversationId(convId)
                val systemMsg = ChatMessage.system(systemPrompt)
                messageHistory.add(systemMsg)
                conversationRepo.addMessage(convId, systemMsg)
            } else {
                val basePrompt = ProviderManager.getDefaultSystemPrompt(context)
                val updatedSystemPrompt = contextCollector().buildSystemPrompt(basePrompt)
                if (messageHistory.isNotEmpty() && messageHistory[0].role == "system") {
                    messageHistory[0] = ChatMessage.system(updatedSystemPrompt)
                }
            }

            messageHistory.add(userMsg)
            conversationRepo.addMessage(currentConversationId()!!, userMsg)

            getTracing()?.close()
            setTracing(buildTracing())

            val trimmedMessages = contextManager().trimMessages(messageHistory.toList())

            val maxTokensVal = ProviderManager.getMaxTokens(context)
            val maxTokens = if (maxTokensVal > 0) maxTokensVal else 8192

            val tempVal = ProviderManager.getTemperature(context)
            val topPVal = ProviderManager.getTopP(context)
            val topKVal = ProviderManager.getTopK(context)
            val samplingParams = com.lhzkml.jasmine.core.prompt.model.SamplingParams(
                temperature = if (tempVal >= 0f) tempVal.toDouble() else null,
                topP = if (topPVal >= 0f) topPVal.toDouble() else null,
                topK = if (topKVal >= 0) topKVal else null
            )

            var result = ""
            var usage: Usage? = null

            withContext(Dispatchers.Main) {
                chatStateManager.startStreaming()
            }

            setPersistence(buildPersistence())

            if (toolsEnabled && registry != null) {
                val agentResult = executeAgentMode(
                    message, client, config, registry, trimmedMessages,
                    maxTokens, samplingParams
                )
                result = agentResult.first
                usage = agentResult.second
            } else {
                val chatResult = executeChatMode(
                    client, config, trimmedMessages, maxTokens, samplingParams
                )
                result = chatResult.first
                usage = chatResult.second
            }

            val assistantMsg = ChatMessage.assistant(result)
            messageHistory.add(assistantMsg)

            getPersistence()?.createCheckpoint(
                agentId = currentConversationId() ?: "unknown",
                nodePath = "turn:${messageHistory.count { it.role == "user" }}",
                lastInput = message,
                messageHistory = listOf(userMsg, assistantMsg)
            )

            val logContent = chatStateManager.getLogContent()
            if (logContent.isNotBlank()) {
                conversationRepo.addMessage(currentConversationId()!!, ChatMessage("agent_log", logContent))
            }

            conversationRepo.addMessage(currentConversationId()!!, assistantMsg)

            if (usage != null) {
                conversationRepo.recordUsage(
                    conversationId = currentConversationId()!!,
                    providerId = config.providerId,
                    model = config.model,
                    usage = usage
                )
            }

            if (ProviderManager.isCompressionEnabled(context)) {
                onUpdateButtonState(true)
                tryCompressHistory(client, config.model)
            }
        } catch (_: CancellationException) {
            // 用户主动停止
        } catch (e: ChatClientException) {
            val errorMsg = when (e.errorType) {
                ErrorType.NETWORK -> "网络错误: ${e.message}\n请检查网络连接后重试"
                ErrorType.AUTHENTICATION -> "认证失败: ${e.message}\n请检查 API Key 是否正确"
                ErrorType.RATE_LIMIT -> "请求过于频繁: ${e.message}\n请稍后再试"
                ErrorType.MODEL_UNAVAILABLE -> "模型不可用: ${e.message}\n请检查模型名称或稍后重试"
                ErrorType.INVALID_REQUEST -> "请求参数错误: ${e.message}"
                ErrorType.SERVER_ERROR -> "服务器错误: ${e.message}\n请稍后重试"
                else -> "错误: ${e.message}"
            }
            val eventHandler = buildEventHandler()
            eventHandler?.fireAgentExecutionFailed(AgentExecutionFailedContext(
                runId = getTracing()?.newRunId() ?: "",
                agentId = currentConversationId() ?: "unknown",
                throwable = e
            ))
            withContext(Dispatchers.Main) {
                chatStateManager.handleError("\n$errorMsg\n\n")
            }
            tryOfferCheckpointRecovery(e, message)
        } catch (e: Exception) {
            val eventHandler = buildEventHandler()
            eventHandler?.fireAgentExecutionFailed(AgentExecutionFailedContext(
                runId = getTracing()?.newRunId() ?: "",
                agentId = currentConversationId() ?: "unknown",
                throwable = e
            ))
            withContext(Dispatchers.Main) {
                chatStateManager.handleError("\n未知错误: ${e.message}\n\n")
            }
            tryOfferCheckpointRecovery(e, message)
        } finally {
            withContext(Dispatchers.Main + kotlinx.coroutines.NonCancellable) {
                chatStateManager.cancelStream()
                onUpdateButtonState(false)
            }
        }
    }

    private suspend fun executeAgentMode(
        message: String,
        client: ChatClient,
        config: ActiveProviderConfig,
        registry: ToolRegistry,
        trimmedMessages: List<ChatMessage>,
        maxTokens: Int,
        samplingParams: com.lhzkml.jasmine.core.prompt.model.SamplingParams
    ): Pair<String, Usage?> {
        var result = ""
        var usage: Usage? = null

        val listener = object : AgentEventListener {
            override suspend fun onToolCallStart(toolName: String, arguments: String) {
                withContext(Dispatchers.Main) {
                    chatStateManager.handleToolCall(toolName, arguments)
                }
            }
            override suspend fun onToolCallResult(toolName: String, result: String) {
                withContext(Dispatchers.Main) {
                    chatStateManager.handleToolResult(toolName, result)
                }
            }
            override suspend fun onThinking(content: String) {
                withContext(Dispatchers.Main) {
                    chatStateManager.handleThinking(content)
                }
            }
        }
        val executor = ToolExecutor(client, registry, eventListener = listener, tracing = getTracing())

        val eventHandler = buildEventHandler()
        val agentRunId = getTracing()?.newRunId() ?: java.util.UUID.randomUUID().toString()
        eventHandler?.fireAgentStarting(AgentStartingContext(
            runId = agentRunId,
            agentId = currentConversationId() ?: "unknown",
            model = config.model,
            toolCount = registry.descriptors().size
        ))

        if (ProviderManager.isPlannerEnabled(context)) {
            try {
                val planPrompt = Prompt.build("planner") {
                    for (msg in trimmedMessages) {
                        when (msg.role) {
                            "system" -> system(msg.content)
                            "user" -> user(msg.content)
                            "assistant" -> assistant(msg.content)
                        }
                    }
                }
                val planSession = LLMWriteSession(client, config.model, planPrompt)
                val planReadSession = com.lhzkml.jasmine.core.prompt.llm.LLMReadSession(client, config.model, planPrompt)
                val planContext = AgentGraphContext(
                    agentId = currentConversationId() ?: "planner",
                    runId = agentRunId,
                    client = client,
                    model = config.model,
                    session = planSession,
                    readSession = planReadSession,
                    toolRegistry = registry,
                    environment = GenericAgentEnvironment(
                        currentConversationId() ?: "planner", registry
                    ),
                    tracing = getTracing()
                )

                val maxIter = ProviderManager.getPlannerMaxIterations(context)
                val planner = if (ProviderManager.isPlannerCriticEnabled(context)) {
                    SimpleLLMWithCriticPlanner(maxIterations = maxIter)
                } else {
                    SimpleLLMPlanner(maxIterations = maxIter)
                }
                val plan = planner.buildPlanPublic(planContext, message, null)
                planSession.close()
                planReadSession.close()

                withContext(Dispatchers.Main) {
                    chatStateManager.handlePlan(
                        plan.goal,
                        plan.steps.map { it.description }
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    chatStateManager.handleSystemLog("[Plan] [规划跳过: ${e.message}]\n\n")
                }
            }
        }

        val agentStrategy = ProviderManager.getAgentStrategy(context)

        when (agentStrategy) {
            com.lhzkml.jasmine.core.config.AgentStrategyType.SIMPLE_LOOP -> {
                val agentPrompt = Prompt.build("agent") {
                    for (msg in trimmedMessages) {
                        when (msg.role) {
                            "system" -> system(msg.content)
                            "user" -> user(msg.content)
                            "assistant" -> assistant(msg.content)
                        }
                    }
                }.copy(maxTokens = maxTokens, samplingParams = samplingParams)
                val streamResult = executor.executeStream(
                    agentPrompt, config.model
                ) { chunk ->
                    withContext(Dispatchers.Main) {
                        chatStateManager.handleChunk(chunk)
                    }
                }
                result = streamResult.content
                usage = streamResult.usage
                withContext(Dispatchers.Main) {
                    chatStateManager.finalizeStream(
                        formatUsageShort(usage),
                        formatTime(System.currentTimeMillis())
                    )
                }
            }

            com.lhzkml.jasmine.core.config.AgentStrategyType.SINGLE_RUN_GRAPH -> {
                val graphResult = executeGraphStrategy(
                    message, client, config, registry, trimmedMessages,
                    maxTokens, samplingParams, agentRunId
                )
                result = graphResult ?: ""

                withContext(Dispatchers.Main) {
                    chatStateManager.finalizeStream(
                        "",
                        formatTime(System.currentTimeMillis())
                    )
                }
            }
        }

        eventHandler?.fireAgentCompleted(AgentCompletedContext(
            runId = agentRunId,
            agentId = currentConversationId() ?: "unknown",
            result = result.take(200),
            totalIterations = 0
        ))

        return result to usage
    }

    private suspend fun executeGraphStrategy(
        message: String,
        client: ChatClient,
        config: ActiveProviderConfig,
        registry: ToolRegistry,
        trimmedMessages: List<ChatMessage>,
        maxTokens: Int,
        samplingParams: com.lhzkml.jasmine.core.prompt.model.SamplingParams,
        agentRunId: String
    ): String? {
        val toolCallMode = when (ProviderManager.getGraphToolCallMode(context)) {
            com.lhzkml.jasmine.core.config.GraphToolCallMode.SEQUENTIAL -> ToolCalls.SEQUENTIAL
            com.lhzkml.jasmine.core.config.GraphToolCallMode.PARALLEL -> ToolCalls.PARALLEL
            com.lhzkml.jasmine.core.config.GraphToolCallMode.SINGLE_RUN_SEQUENTIAL -> ToolCalls.SINGLE_RUN_SEQUENTIAL
        }

        val toolSelStrategy = when (ProviderManager.getToolSelectionStrategy(context)) {
            com.lhzkml.jasmine.core.config.ToolSelectionStrategyType.ALL -> ToolSelectionStrategy.ALL
            com.lhzkml.jasmine.core.config.ToolSelectionStrategyType.NONE -> ToolSelectionStrategy.NONE
            com.lhzkml.jasmine.core.config.ToolSelectionStrategyType.BY_NAME -> {
                val names = ProviderManager.getToolSelectionNames(context)
                if (names.isNotEmpty()) ToolSelectionStrategy.ByName(names) else ToolSelectionStrategy.ALL
            }
            com.lhzkml.jasmine.core.config.ToolSelectionStrategyType.AUTO_SELECT_FOR_TASK -> {
                val desc = ProviderManager.getToolSelectionTaskDesc(context)
                if (desc.isNotEmpty()) ToolSelectionStrategy.AutoSelectForTask(desc) else ToolSelectionStrategy.ALL
            }
        }

        val strategy = PredefinedStrategies.singleRunStreamStrategy(toolCallMode, toolSelStrategy)

        val graphAgent = GraphAgent(
            client = client,
            model = config.model,
            strategy = strategy,
            toolRegistry = registry,
            tracing = getTracing(),
            agentId = currentConversationId() ?: "graph-agent"
        )

        val graphPrompt = Prompt.build("graph-agent") {
            for (msg in trimmedMessages.dropLast(1)) {
                when (msg.role) {
                    "system" -> system(msg.content)
                    "user" -> user(msg.content)
                    "assistant" -> assistant(msg.content)
                }
            }
        }.copy(maxTokens = maxTokens, samplingParams = samplingParams)

        val toolChoiceMode = ProviderManager.getToolChoiceMode(context)
        val toolChoice: com.lhzkml.jasmine.core.prompt.model.ToolChoice? = when (toolChoiceMode) {
            com.lhzkml.jasmine.core.config.ToolChoiceMode.DEFAULT -> null
            com.lhzkml.jasmine.core.config.ToolChoiceMode.AUTO -> com.lhzkml.jasmine.core.prompt.model.ToolChoice.Auto
            com.lhzkml.jasmine.core.config.ToolChoiceMode.REQUIRED -> com.lhzkml.jasmine.core.prompt.model.ToolChoice.Required
            com.lhzkml.jasmine.core.config.ToolChoiceMode.NONE -> com.lhzkml.jasmine.core.prompt.model.ToolChoice.None
            com.lhzkml.jasmine.core.config.ToolChoiceMode.NAMED -> {
                val name = ProviderManager.getToolChoiceNamedTool(context)
                if (name.isNotEmpty()) com.lhzkml.jasmine.core.prompt.model.ToolChoice.Named(name) else null
            }
        }
        val finalGraphPrompt = if (toolChoice != null) graphPrompt.withToolChoice(toolChoice) else graphPrompt

        withContext(Dispatchers.Main) {
            chatStateManager.handleGraphLog("┌─ [Graph] 图策略执行 ─────────────\n│ [>] Start\n")
            chatStateManager.handleGraphLog("└─────────────────────────\n")
        }

        val chunkCallback: (suspend (String) -> Unit) = { chunk: String ->
            withContext(Dispatchers.Main) {
                chatStateManager.handleChunk(chunk)
            }
        }

        val thinkingCallback: (suspend (String) -> Unit) = { text: String ->
            withContext(Dispatchers.Main) {
                chatStateManager.handleThinking(text)
            }
        }

        val nodeEnterCallback: suspend (String) -> Unit = { nodeName ->
            val icon = when {
                nodeName.contains("LLM", true) -> "[LLM]"
                nodeName.contains("Tool", true) -> "[Tool]"
                nodeName.contains("Send", true) -> "[Send]"
                else -> "[Node]"
            }
            withContext(Dispatchers.Main) {
                chatStateManager.handleGraphLog("│ $icon $nodeName ...\n")
            }
        }

        val nodeExitCallback: suspend (String, Boolean) -> Unit = { nodeName, success ->
            val status = if (success) "[OK]" else "[FAIL]"
            withContext(Dispatchers.Main) {
                chatStateManager.handleGraphLog("│ $status $nodeName 完成\n")
            }
        }

        val edgeCallback: suspend (String, String, String) -> Unit = { from, to, label ->
            val labelStr = if (label.isNotEmpty()) " ($label)" else ""
            withContext(Dispatchers.Main) {
                chatStateManager.handleGraphLog("│  ↓ $from → $to$labelStr\n")
            }
        }

        return graphAgent.runWithCallbacks(
            prompt = finalGraphPrompt,
            input = message,
            onChunk = chunkCallback,
            onThinking = thinkingCallback,
            onToolCallStart = { toolName, args ->
                withContext(Dispatchers.Main) {
                    chatStateManager.handleToolCall(toolName, args)
                }
            },
            onToolCallResult = { toolName, toolResult ->
                withContext(Dispatchers.Main) {
                    chatStateManager.handleToolResult(toolName, toolResult)
                }
            },
            onNodeEnter = nodeEnterCallback,
            onNodeExit = nodeExitCallback,
            onEdge = edgeCallback
        )
    }

    private suspend fun executeChatMode(
        client: ChatClient,
        config: ActiveProviderConfig,
        trimmedMessages: List<ChatMessage>,
        maxTokens: Int,
        samplingParams: com.lhzkml.jasmine.core.prompt.model.SamplingParams
    ): Pair<String, Usage?> {
        val resumeEnabled = ProviderManager.isStreamResumeEnabled(context)

        val streamResult = if (resumeEnabled) {
            val helper = StreamResumeHelper(
                maxResumes = ProviderManager.getStreamResumeMaxRetries(context)
            )
            helper.streamWithResume(
                client = client,
                messages = trimmedMessages,
                model = config.model,
                maxTokens = maxTokens,
                samplingParams = samplingParams,
                onChunk = { chunk ->
                    withContext(Dispatchers.Main) {
                        chatStateManager.handleChunk(chunk)
                    }
                },
                onThinking = { text ->
                    withContext(Dispatchers.Main) {
                        chatStateManager.handleThinking(text)
                    }
                },
                onResumeAttempt = { attempt ->
                    withContext(Dispatchers.Main) {
                        chatStateManager.handleSystemLog("\n[网络超时，正在续传... 第${attempt}次]\n")
                    }
                }
            )
        } else {
            client.chatStreamWithUsageAndThinking(
                trimmedMessages, config.model, maxTokens, samplingParams,
                onChunk = { chunk ->
                    withContext(Dispatchers.Main) {
                        chatStateManager.handleChunk(chunk)
                    }
                },
                onThinking = { text ->
                    withContext(Dispatchers.Main) {
                        chatStateManager.handleThinking(text)
                    }
                }
            )
        }

        val result = streamResult.content
        val usage = streamResult.usage

        withContext(Dispatchers.Main) {
            chatStateManager.finalizeStream(
                formatUsageShort(usage),
                formatTime(System.currentTimeMillis())
            )
        }

        return result to usage
    }

    companion object {
        fun formatUsageShort(usage: Usage?): String {
            if (usage == null) return ""
            return "[提示: ${usage.promptTokens} | 回复: ${usage.completionTokens} | 总计: ${usage.totalTokens}]"
        }

        fun formatTime(timestamp: Long): String {
            return SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
    }
}
