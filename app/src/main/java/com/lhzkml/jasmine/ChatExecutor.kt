package com.lhzkml.jasmine

import android.content.Context
import com.lhzkml.jasmine.core.agent.graph.graph.AgentGraphContext
import com.lhzkml.jasmine.core.agent.graph.graph.GenericAgentEnvironment
import com.lhzkml.jasmine.core.agent.graph.graph.GraphAgent
import com.lhzkml.jasmine.core.agent.graph.graph.PredefinedStrategies
import com.lhzkml.jasmine.core.agent.graph.graph.ToolCalls
import com.lhzkml.jasmine.core.agent.graph.graph.ToolSelectionStrategy
import com.lhzkml.jasmine.core.agent.observe.event.*
import com.lhzkml.jasmine.core.agent.runtime.AgentRuntimeBuilder
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
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 聊天执行器：封装 sendMessage 的后台 IO 逻辑。
 * 遵循「I/O 线程分离」：StreamProcessor 在 IO 上处理，StreamUpdate 经 Channel 发往主线程。
 */
class ChatExecutor(
    private val context: Context,
    private val config: ChatExecutorConfig,
    private val chatStateManager: ChatStateManager,
    private val conversationRepo: ConversationRepository,
    /** 非 null 时使用 Channel 模式：在 IO 上处理，发送 StreamUpdate，减少 withContext(Main) 切换 */
    private val streamUpdateChannel: SendChannel<StreamUpdate>? = null,
    private val contextCollector: () -> SystemContextCollector,
    private val contextManager: () -> ContextManager,
    private val currentConversationId: () -> String?,
    private val setConversationId: (String) -> Unit,
    private val messageHistory: MutableList<ChatMessage>,
    private val buildToolRegistry: suspend (ChatClient, String) -> ToolRegistry,
    private val loadMcpTools: suspend (ToolRegistry) -> Unit,
    private val refreshContextCollector: () -> Unit,
    private val buildTracing: () -> Tracing?,
    private val setTracing: (Tracing?) -> Unit,
    private val getTracing: () -> Tracing?,
    private val buildEventHandler: (AgentRuntimeBuilder.EventEmitter) -> EventHandler?,
    private val buildPersistence: () -> Persistence?,
    private val getPersistence: () -> Persistence?,
    private val setPersistence: (Persistence?) -> Unit,
    private val tryOfferCheckpointRecovery: suspend (Exception, String) -> Unit,
    private val tryCompressHistory: suspend (ChatClient, String) -> Unit,
    private val onUpdateButtonState: suspend (Boolean) -> Unit
) {
    /** Channel 模式下在 IO 上处理流式数据，主线程仅负责 UI 更新 */
    private var streamProcessor: StreamProcessor? = null

    /** Channel 模式下获取日志内容（供 savePartial 使用） */
    fun getLogContent(): String = streamProcessor?.getLogContent() ?: ""
    fun getBufferedText(): String = streamProcessor?.getBufferedText() ?: ""

    /** I/O 线程处理结果发往主线程：Channel 模式直接 send，否则 withContext(Main) */
    private suspend fun sendUpdate(update: StreamUpdate) {
        if (streamUpdateChannel != null) {
            streamUpdateChannel.send(update)
        } else {
            withContext(Dispatchers.Main) { chatStateManager.processStreamUpdate(update) }
        }
    }

    private fun createEventEmitter() = AgentRuntimeBuilder.EventEmitter { line ->
        val proc = streamProcessor
        if (proc != null) sendUpdate(proc.onSystemLog(line))
        else withContext(Dispatchers.Main) { chatStateManager.handleSystemLog(line) }
    }

    /**
     * 在 IO 线程执行聊天逻辑（Agent 模式或普通模式）。
     * 调用方应在 Dispatchers.IO 上调用此方法。
     */
    suspend fun execute(
        message: String,
        userMsg: ChatMessage,
        client: ChatClient,
        providerConfig: ActiveProviderConfig
    ) {
        try {
            val toolsEnabled = config.toolsEnabled

            val registry = if (toolsEnabled) {
                val r = buildToolRegistry(client, providerConfig.model)
                loadMcpTools(r)
                r
            } else null

            refreshContextCollector()

            if (currentConversationId() == null) {
                val title = if (message.length > 20) message.substring(0, 20) + "..." else message
                val basePrompt = config.defaultSystemPrompt
                val systemPrompt = contextCollector().buildSystemPrompt(basePrompt, currentUserMessage = message)
                val convId = conversationRepo.createConversation(
                    title = title,
                    providerId = providerConfig.providerId,
                    model = providerConfig.model,
                    systemPrompt = systemPrompt,
                    workspacePath = if (config.isAgentMode) config.workspacePath else ""
                )
                setConversationId(convId)
                val systemMsg = ChatMessage.system(systemPrompt)
                messageHistory.add(systemMsg)
                conversationRepo.addMessage(convId, systemMsg)
            } else {
                val basePrompt = config.defaultSystemPrompt
                val updatedSystemPrompt = contextCollector().buildSystemPrompt(basePrompt, currentUserMessage = message)
                if (messageHistory.isNotEmpty() && messageHistory[0].role == "system") {
                    messageHistory[0] = ChatMessage.system(updatedSystemPrompt)
                }
            }

            messageHistory.add(userMsg)
            conversationRepo.addMessage(currentConversationId()!!, userMsg)

            getTracing()?.close()
            setTracing(buildTracing())

            val trimmedMessages = contextManager().trimMessages(messageHistory.toList())

            val maxTokensVal = config.maxTokens
            val maxTokens = if (maxTokensVal > 0) maxTokensVal else 8192

            val tempVal = config.temperature
            val topPVal = config.topP
            val topKVal = config.topK
            val samplingParams = com.lhzkml.jasmine.core.prompt.model.SamplingParams(
                temperature = if (tempVal >= 0.0) tempVal else null,
                topP = if (topPVal >= 0.0) topPVal else null,
                topK = if (topKVal >= 0) topKVal else null
            )

            var result = ""
            var usage: Usage? = null

            val useChannel = streamUpdateChannel != null
            withContext(Dispatchers.Main) {
                chatStateManager.startStreaming(useChannelMode = useChannel)
            }
            if (useChannel) streamProcessor = StreamProcessor()

            setPersistence(buildPersistence())

            val logContent: String
            if (toolsEnabled && registry != null) {
                val agentResult = executeAgentMode(
                    message, client, providerConfig, registry, trimmedMessages,
                    maxTokens, samplingParams
                )
                result = agentResult.first
                usage = agentResult.second
                logContent = agentResult.third
            } else {
                val chatResult = executeChatMode(
                    client, providerConfig, trimmedMessages, maxTokens, samplingParams
                )
                result = chatResult.first
                usage = chatResult.second
                logContent = chatResult.third
            }

            val contentToSave = if (logContent.isNotBlank()) {
                logContent + BLOCK_TEXT_SEPARATOR + result
            } else {
                result
            }
            conversationRepo.addMessage(currentConversationId()!!, ChatMessage.assistant(contentToSave))

            val assistantMsg = ChatMessage.assistant(result)
            messageHistory.add(assistantMsg)

            getPersistence()?.createCheckpoint(
                agentId = currentConversationId() ?: "unknown",
                nodePath = "turn:${messageHistory.count { it.role == "user" }}",
                lastInput = message,
                messageHistory = listOf(userMsg, assistantMsg)
            )

            if (usage != null) {
                conversationRepo.recordUsage(
                    conversationId = currentConversationId()!!,
                    providerId = providerConfig.providerId,
                    model = providerConfig.model,
                    usage = usage
                )
            }

            if (config.compressionEnabled) {
                tryCompressHistory(client, providerConfig.model)
            }
        } catch (_: CancellationException) {
            // 用户主动停止 — 保存已生成的部分内容
            withContext(Dispatchers.Main + kotlinx.coroutines.NonCancellable) {
                val partialText = chatStateManager.getPartialContent()
                val logContent = chatStateManager.getLogContent()
                val bufferedText = chatStateManager.getBufferedText()
                if (partialText.isNotEmpty()) {
                    val convId = currentConversationId()
                    if (convId != null) {
                        val contentToSave = if (logContent.isNotBlank()) {
                            logContent + BLOCK_TEXT_SEPARATOR + bufferedText
                        } else {
                            partialText
                        }
                        withContext(Dispatchers.IO) {
                            conversationRepo.addMessage(convId, ChatMessage.assistant(contentToSave))
                        }
                        messageHistory.add(ChatMessage.assistant(partialText))
                    }
                }
            }
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
            val eventHandler = buildEventHandler(createEventEmitter())
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
            val eventHandler = buildEventHandler(createEventEmitter())
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
            try { getTracing()?.close() } catch (_: Exception) {}
        }
    }

    private suspend fun executeAgentMode(
        message: String,
        client: ChatClient,
        providerConfig: ActiveProviderConfig,
        registry: ToolRegistry,
        trimmedMessages: List<ChatMessage>,
        maxTokens: Int,
        samplingParams: com.lhzkml.jasmine.core.prompt.model.SamplingParams
    ): Triple<String, Usage?, String> {
        var result = ""
        var usage: Usage? = null

        val proc = streamProcessor
        val listener = object : AgentEventListener {
            override suspend fun onToolCallStart(toolName: String, arguments: String) {
                if (proc != null) sendUpdate(proc.onToolCallStart(toolName, arguments))
                else withContext(Dispatchers.Main) { chatStateManager.handleToolCall(toolName, arguments) }
            }
            override suspend fun onToolCallResult(toolName: String, result: String) {
                if (proc != null) sendUpdate(proc.onToolCallResult(toolName, result))
                else withContext(Dispatchers.Main) { chatStateManager.handleToolResult(toolName, result) }
            }
            override suspend fun onThinking(content: String) {
                if (proc != null) sendUpdate(proc.onThinking(content))
                else withContext(Dispatchers.Main) { chatStateManager.handleThinking(content) }
            }
        }
        val eventHandler = buildEventHandler(createEventEmitter())
        val executor = ToolExecutor(
            client, registry,
            maxIterations = config.agentMaxIterations,
            eventListener = listener,
            eventHandler = eventHandler,
            tracing = getTracing(),
            maxToolResultLength = config.maxToolResultLength
        )
        val agentRunId = getTracing()?.newRunId() ?: java.util.UUID.randomUUID().toString()
        eventHandler?.fireAgentStarting(AgentStartingContext(
            runId = agentRunId,
            agentId = currentConversationId() ?: "unknown",
            model = providerConfig.model,
            toolCount = registry.descriptors().size
        ))

        var messagesWithPlan: List<ChatMessage>? = null
        if (config.plannerEnabled) {
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
                val planSession = LLMWriteSession(client, providerConfig.model, planPrompt)
                val planReadSession = com.lhzkml.jasmine.core.prompt.llm.LLMReadSession(client, providerConfig.model, planPrompt)
                val planContext = AgentGraphContext(
                    agentId = currentConversationId() ?: "planner",
                    runId = agentRunId,
                    client = client,
                    model = providerConfig.model,
                    session = planSession,
                    readSession = planReadSession,
                    toolRegistry = registry,
                    environment = GenericAgentEnvironment(
                        currentConversationId() ?: "planner", registry
                    ),
                    tracing = getTracing()
                )

                val maxIter = config.plannerMaxIterations
                val useCritic = config.plannerCriticEnabled

                val plan: com.lhzkml.jasmine.core.agent.planner.SimplePlan
                var criticInfo = ""

                if (useCritic) {
                    val criticPlanner = SimpleLLMWithCriticPlanner(
                        maxIterations = maxIter, maxCriticRetries = 2
                    )
                    val (validatedPlan, evaluation) = criticPlanner.buildAndValidatePlan(
                        planContext, message
                    )
                    plan = validatedPlan
                    if (evaluation != null) {
                        criticInfo = " (Critic: ${evaluation.score}/10)"
                    }
                } else {
                    val planner = SimpleLLMPlanner(maxIterations = maxIter)
                    plan = planner.buildPlanPublic(planContext, message, null)
                }

                planSession.close()
                planReadSession.close()

                val proc = streamProcessor
                if (proc != null) sendUpdate(proc.onPlan(plan.goal + criticInfo, plan.steps.map { "[${it.type}] ${it.description}" }))
                else withContext(Dispatchers.Main) {
                    chatStateManager.handlePlan(plan.goal + criticInfo, plan.steps.map { "[${it.type}] ${it.description}" })
                }

                val planPromptText = SimpleLLMPlanner.formatPlanForPrompt(plan)
                // 将计划合并到首条 system 消息，避免连续两条 system 导致部分 API 处理异常
                val first = trimmedMessages.first()
                messagesWithPlan = if (first.role == "system") {
                    listOf(ChatMessage.system(first.content + "\n\n" + planPromptText)) +
                        trimmedMessages.drop(1)
                } else {
                    listOf(ChatMessage.system(planPromptText)) + trimmedMessages
                }
            } catch (e: Exception) {
                val proc = streamProcessor
                if (proc != null) sendUpdate(proc.onSystemLog("[Plan] [规划跳过: ${e.message}]\n\n"))
                else withContext(Dispatchers.Main) { chatStateManager.handleSystemLog("[Plan] [规划跳过: ${e.message}]\n\n") }
            }
        }

        val effectiveMessages = messagesWithPlan ?: trimmedMessages
        val agentStrategy = config.agentStrategy

        when (agentStrategy) {
            com.lhzkml.jasmine.core.config.AgentStrategyType.SIMPLE_LOOP -> {
                var agentPrompt = Prompt.build("agent") {
                    for (msg in effectiveMessages) {
                        when (msg.role) {
                            "system" -> system(msg.content)
                            "user" -> user(msg.content)
                            "assistant" -> assistant(msg.content)
                        }
                    }
                }.copy(maxTokens = maxTokens, samplingParams = samplingParams)

                val simpleToolChoice = when (config.toolChoiceMode) {
                    com.lhzkml.jasmine.core.config.ToolChoiceMode.DEFAULT -> null
                    com.lhzkml.jasmine.core.config.ToolChoiceMode.AUTO -> com.lhzkml.jasmine.core.prompt.model.ToolChoice.Auto
                    com.lhzkml.jasmine.core.config.ToolChoiceMode.REQUIRED -> com.lhzkml.jasmine.core.prompt.model.ToolChoice.Required
                    com.lhzkml.jasmine.core.config.ToolChoiceMode.NONE -> com.lhzkml.jasmine.core.prompt.model.ToolChoice.None
                    com.lhzkml.jasmine.core.config.ToolChoiceMode.NAMED -> {
                        val name = config.toolChoiceNamedTool
                        if (name.isNotEmpty()) com.lhzkml.jasmine.core.prompt.model.ToolChoice.Named(name) else null
                    }
                }
                if (simpleToolChoice != null) {
                    agentPrompt = agentPrompt.withToolChoice(simpleToolChoice)
                }

                val proc = streamProcessor
                val streamResult = executor.executeStream(
                    agentPrompt, providerConfig.model
                ) { chunk ->
                    if (proc != null) sendUpdate(proc.onChunk(chunk))
                    else withContext(Dispatchers.Main) { chatStateManager.handleChunk(chunk) }
                }
                result = streamResult.content
                usage = streamResult.usage
                val usageLine = formatUsageShort(usage)
                val timeStr = formatTime(System.currentTimeMillis())
                val logContent = if (proc != null) {
                    val log = proc.getLogContent()
                    sendUpdate(StreamUpdate(proc.finalize().blocks, isComplete = true, usageLine = usageLine, time = timeStr))
                    log
                } else {
                    withContext(Dispatchers.Main) {
                        chatStateManager.getLogContent().also {
                            chatStateManager.finalizeStream(usageLine, timeStr)
                        }
                    }
                }
                eventHandler?.fireAgentCompleted(AgentCompletedContext(
                    runId = agentRunId,
                    agentId = currentConversationId() ?: "unknown",
                    result = result.take(200),
                    totalIterations = 0
                ))
                return Triple(result, usage, logContent)
            }

            com.lhzkml.jasmine.core.config.AgentStrategyType.SINGLE_RUN_GRAPH -> {
                val graphResult = executeGraphStrategy(
                    message, client, providerConfig, registry, effectiveMessages,
                    maxTokens, samplingParams, agentRunId
                )
                result = graphResult ?: ""

                val proc = streamProcessor
                val timeStr = formatTime(System.currentTimeMillis())
                val logContent = if (proc != null) {
                    val log = proc.getLogContent()
                    sendUpdate(StreamUpdate(proc.finalize().blocks, isComplete = true, usageLine = "", time = timeStr))
                    log
                } else {
                    withContext(Dispatchers.Main) {
                        chatStateManager.getLogContent().also {
                            chatStateManager.finalizeStream("", timeStr)
                        }
                    }
                }
                eventHandler?.fireAgentCompleted(AgentCompletedContext(
                    runId = agentRunId,
                    agentId = currentConversationId() ?: "unknown",
                    result = result.take(200),
                    totalIterations = 0
                ))
                return Triple(result, usage, logContent)
            }
        }
    }

    private suspend fun executeGraphStrategy(
        message: String,
        client: ChatClient,
        providerConfig: ActiveProviderConfig,
        registry: ToolRegistry,
        trimmedMessages: List<ChatMessage>,
        maxTokens: Int,
        samplingParams: com.lhzkml.jasmine.core.prompt.model.SamplingParams,
        agentRunId: String
    ): String? {
        val toolCallMode = when (config.graphToolCallMode) {
            com.lhzkml.jasmine.core.config.GraphToolCallMode.SEQUENTIAL -> ToolCalls.SEQUENTIAL
            com.lhzkml.jasmine.core.config.GraphToolCallMode.PARALLEL -> ToolCalls.PARALLEL
            com.lhzkml.jasmine.core.config.GraphToolCallMode.SINGLE_RUN_SEQUENTIAL -> ToolCalls.SINGLE_RUN_SEQUENTIAL
        }

        val toolSelStrategy = when (config.toolSelectionStrategy) {
            com.lhzkml.jasmine.core.config.ToolSelectionStrategyType.ALL -> ToolSelectionStrategy.ALL
            com.lhzkml.jasmine.core.config.ToolSelectionStrategyType.NONE -> ToolSelectionStrategy.NONE
            com.lhzkml.jasmine.core.config.ToolSelectionStrategyType.BY_NAME -> {
                val names = config.toolSelectionNames
                if (names.isNotEmpty()) ToolSelectionStrategy.ByName(names) else ToolSelectionStrategy.ALL
            }
            com.lhzkml.jasmine.core.config.ToolSelectionStrategyType.AUTO_SELECT_FOR_TASK -> {
                val desc = config.toolSelectionTaskDesc
                if (desc.isNotEmpty()) ToolSelectionStrategy.AutoSelectForTask(desc) else ToolSelectionStrategy.ALL
            }
        }

        val strategy = PredefinedStrategies.singleRunStreamStrategy(toolCallMode, toolSelStrategy)

        val graphAgent = GraphAgent(
            client = client,
            model = providerConfig.model,
            strategy = strategy,
            toolRegistry = registry,
            tracing = getTracing(),
            agentId = currentConversationId() ?: "graph-agent"
        )

        // 必须包含完整对话（含用户当前消息），否则模型无法看到用户刚发的问题，导致问答不一致
        val graphPrompt = Prompt.build("graph-agent") {
            for (msg in trimmedMessages) {
                when (msg.role) {
                    "system" -> system(msg.content)
                    "user" -> user(msg.content)
                    "assistant" -> assistant(msg.content)
                }
            }
        }.copy(maxTokens = maxTokens, samplingParams = samplingParams)

        val toolChoiceMode = config.toolChoiceMode
        val toolChoice: com.lhzkml.jasmine.core.prompt.model.ToolChoice? = when (toolChoiceMode) {
            com.lhzkml.jasmine.core.config.ToolChoiceMode.DEFAULT -> null
            com.lhzkml.jasmine.core.config.ToolChoiceMode.AUTO -> com.lhzkml.jasmine.core.prompt.model.ToolChoice.Auto
            com.lhzkml.jasmine.core.config.ToolChoiceMode.REQUIRED -> com.lhzkml.jasmine.core.prompt.model.ToolChoice.Required
            com.lhzkml.jasmine.core.config.ToolChoiceMode.NONE -> com.lhzkml.jasmine.core.prompt.model.ToolChoice.None
            com.lhzkml.jasmine.core.config.ToolChoiceMode.NAMED -> {
                val name = config.toolChoiceNamedTool
                if (name.isNotEmpty()) com.lhzkml.jasmine.core.prompt.model.ToolChoice.Named(name) else null
            }
        }
        val finalGraphPrompt = if (toolChoice != null) graphPrompt.withToolChoice(toolChoice) else graphPrompt

        val proc = streamProcessor
        val graphLog: suspend (String) -> Unit = { content ->
            if (proc != null) sendUpdate(proc.onGraphLog(content))
            else withContext(Dispatchers.Main) { chatStateManager.handleGraphLog(content) }
        }
        graphLog("┌─ [Graph] 图策略执行 ─────────────\n│ [>] Start\n")
        graphLog("└─────────────────────────\n")

        val chunkCallback: (suspend (String) -> Unit) = { chunk: String ->
            if (proc != null) sendUpdate(proc.onChunk(chunk))
            else withContext(Dispatchers.Main) { chatStateManager.handleChunk(chunk) }
        }

        val thinkingCallback: (suspend (String) -> Unit) = { text: String ->
            if (proc != null) sendUpdate(proc.onThinking(text))
            else withContext(Dispatchers.Main) { chatStateManager.handleThinking(text) }
        }

        val nodeEnterCallback: suspend (String) -> Unit = { nodeName ->
            val icon = when {
                nodeName.contains("LLM", true) -> "[LLM]"
                nodeName.contains("Tool", true) -> "[Tool]"
                nodeName.contains("Send", true) -> "[Send]"
                else -> "[Node]"
            }
            graphLog("│ $icon $nodeName ...\n")
        }

        val nodeExitCallback: suspend (String, Boolean) -> Unit = { nodeName, success ->
            val status = if (success) "[OK]" else "[FAIL]"
            graphLog("│ $status $nodeName 完成\n")
        }

        val edgeCallback: suspend (String, String, String) -> Unit = { from, to, label ->
            val labelStr = if (label.isNotEmpty()) " ($label)" else ""
            graphLog("│  ↓ $from → $to$labelStr\n")
        }

        return graphAgent.runWithCallbacks(
            prompt = finalGraphPrompt,
            input = message,
            onChunk = chunkCallback,
            onThinking = thinkingCallback,
            onToolCallStart = { toolName, args ->
                if (proc != null) sendUpdate(proc.onToolCallStart(toolName, args))
                else withContext(Dispatchers.Main) { chatStateManager.handleToolCall(toolName, args) }
            },
            onToolCallResult = { toolName, toolResult ->
                if (proc != null) sendUpdate(proc.onToolCallResult(toolName, toolResult))
                else withContext(Dispatchers.Main) { chatStateManager.handleToolResult(toolName, toolResult) }
            },
            onNodeEnter = nodeEnterCallback,
            onNodeExit = nodeExitCallback,
            onEdge = edgeCallback
        )
    }

    private suspend fun executeChatMode(
        client: ChatClient,
        providerConfig: ActiveProviderConfig,
        trimmedMessages: List<ChatMessage>,
        maxTokens: Int,
        samplingParams: com.lhzkml.jasmine.core.prompt.model.SamplingParams
    ): Triple<String, Usage?, String> {
        val resumeEnabled = config.streamResumeEnabled

        val proc = streamProcessor
        val streamResult = if (resumeEnabled) {
            val helper = StreamResumeHelper(
                maxResumes = config.streamResumeMaxRetries
            )
            helper.streamWithResume(
                client = client,
                messages = trimmedMessages,
                model = providerConfig.model,
                maxTokens = maxTokens,
                samplingParams = samplingParams,
                onChunk = { chunk ->
                    if (proc != null) sendUpdate(proc.onChunk(chunk))
                    else withContext(Dispatchers.Main) { chatStateManager.handleChunk(chunk) }
                },
                onThinking = { text ->
                    if (proc != null) sendUpdate(proc.onThinking(text))
                    else withContext(Dispatchers.Main) { chatStateManager.handleThinking(text) }
                },
                onResumeAttempt = { attempt ->
                    if (proc != null) sendUpdate(proc.onSystemLog("\n[网络超时，正在续传... 第${attempt}次]\n"))
                    else withContext(Dispatchers.Main) { chatStateManager.handleSystemLog("\n[网络超时，正在续传... 第${attempt}次]\n") }
                }
            )
        } else {
            client.chatStreamWithUsageAndThinking(
                trimmedMessages, providerConfig.model, maxTokens, samplingParams,
                onChunk = { chunk ->
                    if (proc != null) sendUpdate(proc.onChunk(chunk))
                    else withContext(Dispatchers.Main) { chatStateManager.handleChunk(chunk) }
                },
                onThinking = { text ->
                    if (proc != null) sendUpdate(proc.onThinking(text))
                    else withContext(Dispatchers.Main) { chatStateManager.handleThinking(text) }
                }
            )
        }

        val result = streamResult.content
        val usage = streamResult.usage
        val usageLine = formatUsageShort(usage)
        val timeStr = formatTime(System.currentTimeMillis())

        val logContent = if (proc != null) {
            val log = proc.getLogContent()
            sendUpdate(StreamUpdate(proc.finalize().blocks, isComplete = true, usageLine = usageLine, time = timeStr))
            log
        } else {
            withContext(Dispatchers.Main) {
                chatStateManager.getLogContent().also {
                    chatStateManager.finalizeStream(usageLine, timeStr)
                }
            }
        }

        return Triple(result, usage, logContent)
    }

    companion object {
        /** 合并保存时，日志块与最终回复的分隔符（U+001E Record Separator 双字符，避免与正文冲突） */
        const val BLOCK_TEXT_SEPARATOR = "\u001E\u001E"

        fun formatUsageShort(usage: Usage?): String {
            if (usage == null) return ""
            return "[提示: ${usage.promptTokens} | 回复: ${usage.completionTokens} | 总计: ${usage.totalTokens}]"
        }

        fun formatTime(timestamp: Long): String {
            return SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
    }
}
