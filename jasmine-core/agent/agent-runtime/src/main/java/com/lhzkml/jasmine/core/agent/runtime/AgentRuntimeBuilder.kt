package com.lhzkml.jasmine.core.agent.runtime

import com.lhzkml.jasmine.core.agent.observe.event.EventCategory
import com.lhzkml.jasmine.core.agent.observe.event.EventHandler
import com.lhzkml.jasmine.core.agent.observe.snapshot.InMemoryPersistenceStorageProvider
import com.lhzkml.jasmine.core.agent.observe.snapshot.FilePersistenceStorageProvider
import com.lhzkml.jasmine.core.agent.observe.snapshot.Persistence
import com.lhzkml.jasmine.core.agent.observe.trace.LogTraceWriter
import com.lhzkml.jasmine.core.agent.observe.trace.FileTraceWriter
import com.lhzkml.jasmine.core.agent.observe.trace.Tracing
import com.lhzkml.jasmine.core.config.ConfigRepository
import com.lhzkml.jasmine.core.config.SnapshotStorageType
import com.lhzkml.jasmine.core.prompt.llm.AgentPromptContextProvider
import com.lhzkml.jasmine.core.prompt.llm.CurrentTimeContextProvider
import com.lhzkml.jasmine.core.prompt.llm.SystemContextCollector
import com.lhzkml.jasmine.core.prompt.llm.SystemInfoContextProvider
import com.lhzkml.jasmine.core.prompt.llm.WorkspaceContextProvider
import java.io.File

/**
 * Agent 运行时构建器
 *
 * 封装追踪系统、事件处理器、持久化系统、系统上下文的构建逻辑。
 * 将 MainActivity 中的 buildTracing/buildEventHandler/buildPersistence/refreshContextCollector
 * 迁移到 core 层。
 */
class AgentRuntimeBuilder(private val configRepo: ConfigRepository) {

    // ========== Tracing ==========

    /**
     * 构建追踪系统
     *
     * @param traceDir 追踪日志目录（如 `getExternalFilesDir("traces")`），null 表示不写文件
     * @return Tracing 实例，如果追踪未启用则返回 null
     */
    fun buildTracing(traceDir: File? = null): Tracing? {
        if (!configRepo.isTraceEnabled()) return null

        return Tracing.build {
            addWriter(LogTraceWriter())
            if (configRepo.isTraceFileEnabled() && traceDir != null) {
                traceDir.mkdirs()
                val traceFile = File(traceDir, "trace_${System.currentTimeMillis()}.log")
                addWriter(FileTraceWriter(traceFile))
            }
        }
    }

    // ========== EventHandler ==========

    /**
     * 事件输出回调
     * app 层提供实现，用于将事件文本显示到 UI 或收集到日志
     */
    fun interface EventEmitter {
        suspend fun emit(line: String)
    }

    /**
     * 构建事件处理器
     *
     * @param emitter 事件输出回调（app 层提供，用于 UI 显示）
     * @return EventHandler 实例，如果事件处理器未启用则返回 null
     */
    fun buildEventHandler(emitter: EventEmitter? = null): EventHandler? {
        if (!configRepo.isEventHandlerEnabled()) return null

        val filter = configRepo.getEventHandlerFilter()
        fun isEnabled(cat: EventCategory) = filter.isEmpty() || cat in filter

        val emit: suspend (String) -> Unit = { line ->
            emitter?.emit(line)
        }

        return EventHandler.build {
            if (isEnabled(EventCategory.AGENT)) {
                onAgentExecutionFailed { ctx -> emit("[EVENT] Agent 失败: ${ctx.throwable.message}\n") }
            }
            if (isEnabled(EventCategory.TOOL)) {
                onToolCallStarting { ctx -> emit("[EVENT] 工具调用: ${ctx.toolName}(${ctx.toolArgs.take(60)})\n") }
                onToolCallCompleted { ctx -> emit("[EVENT] 工具完成: ${ctx.toolName} -> ${(ctx.result ?: "").take(100)}\n") }
                onToolCallFailed { ctx -> emit("[EVENT] 工具失败: ${ctx.toolName} - ${ctx.throwable.message}\n") }
                onToolValidationFailed { ctx -> emit("[EVENT] 工具验证失败: ${ctx.toolName} - ${ctx.validationError}\n") }
            }
            if (isEnabled(EventCategory.LLM)) {
                onLLMCallStarting { ctx -> emit("[EVENT] LLM 请求 [消息: ${ctx.messageCount}, 工具: ${ctx.tools.size}]\n") }
                onLLMCallCompleted { ctx -> emit("[EVENT] LLM 回复 [${ctx.totalTokens} tokens]\n") }
            }
            if (isEnabled(EventCategory.STRATEGY)) {
                onStrategyStarting { ctx -> emit("[EVENT] 策略开始: ${ctx.strategyName}\n") }
                onStrategyCompleted { ctx -> emit("[EVENT] 策略完成: ${ctx.strategyName} -> ${ctx.result?.take(80) ?: ""}\n") }
            }
            if (isEnabled(EventCategory.NODE)) {
                onNodeExecutionStarting { ctx -> emit("[EVENT] 节点开始: ${ctx.nodeName}\n") }
                onNodeExecutionCompleted { ctx -> emit("[EVENT] 节点完成: ${ctx.nodeName}\n") }
                onNodeExecutionFailed { ctx -> emit("[EVENT] 节点失败: ${ctx.nodeName} - ${ctx.throwable.message}\n") }
            }
            if (isEnabled(EventCategory.SUBGRAPH)) {
                onSubgraphExecutionStarting { ctx -> emit("[EVENT] 子图开始: ${ctx.subgraphName}\n") }
                onSubgraphExecutionCompleted { ctx -> emit("[EVENT] 子图完成: ${ctx.subgraphName}\n") }
                onSubgraphExecutionFailed { ctx -> emit("[EVENT] 子图失败: ${ctx.subgraphName} - ${ctx.throwable.message}\n") }
            }
            if (isEnabled(EventCategory.STREAMING)) {
                onLLMStreamingStarting { ctx -> emit("[EVENT] LLM 流式开始 [模型: ${ctx.model}]\n") }
                onLLMStreamingCompleted { ctx -> emit("[EVENT] LLM 流式完成 [${ctx.totalTokens} tokens]\n") }
                onLLMStreamingFailed { ctx -> emit("[EVENT] LLM 流式失败: ${ctx.throwable.message}\n") }
            }
        }
    }

    // ========== Persistence ==========

    /**
     * 构建持久化系统
     *
     * @param snapshotDir 快照存储目录（如 `getExternalFilesDir("snapshots")`），null 时使用内存存储
     * @return Persistence 实例，如果快照未启用则返回 null
     */
    fun buildPersistence(snapshotDir: File? = null): Persistence? {
        if (!configRepo.isSnapshotEnabled()) return null

        val provider = when (configRepo.getSnapshotStorage()) {
            SnapshotStorageType.MEMORY -> InMemoryPersistenceStorageProvider()
            SnapshotStorageType.FILE -> {
                if (snapshotDir != null) {
                    FilePersistenceStorageProvider(snapshotDir)
                } else {
                    InMemoryPersistenceStorageProvider()
                }
            }
        }

        val autoCheckpoint = configRepo.isSnapshotAutoCheckpoint()
        val persistence = Persistence(
            provider = provider,
            autoCheckpoint = autoCheckpoint
        )
        persistence.rollbackStrategy = configRepo.getSnapshotRollbackStrategy()
        return persistence
    }

    // ========== SystemContext ==========

    /**
     * 构建系统上下文收集器
     *
     * @param isAgentMode 是否为 Agent 模式
     * @param workspacePath 工作区路径
     * @param agentName Agent 名称
     * @return 配置好的 SystemContextCollector
     */
    fun buildSystemContext(
        isAgentMode: Boolean,
        workspacePath: String = "",
        agentName: String = "Jasmine"
    ): SystemContextCollector {
        val collector = SystemContextCollector()

        // Agent 模式：注入结构化行为指引（包含系统信息、时间、工作区）
        if (isAgentMode) {
            collector.register(AgentPromptContextProvider(
                agentName = agentName,
                workspacePath = workspacePath
            ))
        }

        // Agent 模式：注入工作区路径说明
        if (isAgentMode && workspacePath.isNotEmpty()) {
            collector.register(WorkspaceContextProvider(workspacePath))
        }

        // 非 Agent 模式：单独注入系统信息和时间
        if (!isAgentMode) {
            collector.register(SystemInfoContextProvider())
            collector.register(CurrentTimeContextProvider())
        }

        return collector
    }
}
