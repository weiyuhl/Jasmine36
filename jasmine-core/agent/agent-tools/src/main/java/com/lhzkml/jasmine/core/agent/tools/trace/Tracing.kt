package com.lhzkml.jasmine.core.agent.tools.trace

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * 端到端追踪系统
 * 参考 koog 的 Tracing feature，记录 Agent 运行、LLM 调用、工具调用的完整 span。
 *
 * koog 通过 pipeline interceptor 机制自动拦截事件，
 * jasmine 没有 pipeline，改为手动调用 emit 方法发射事件，
 * 由 ToolExecutor 在关键节点调用。
 *
 * 使用方式：
 * ```kotlin
 * val tracing = Tracing.build {
 *     addWriter(LogTraceWriter())
 *     addWriter(FileTraceWriter(file))
 *     addWriter(CallbackTraceWriter { event -> updateUI(event) })
 * }
 *
 * val executor = ToolExecutor(client, registry, tracing = tracing)
 * ```
 *
 * @param writers 追踪事件写入器列表
 */
class Tracing private constructor(
    private val writers: List<TraceWriter>
) {
    private val eventCounter = AtomicInteger(0)

    /** 生成唯一运行 ID */
    fun newRunId(): String = UUID.randomUUID().toString().take(8)

    /** 生成唯一事件 ID */
    fun newEventId(): String = "evt-${eventCounter.incrementAndGet()}"

    /** 发射事件到所有 writer */
    suspend fun emit(event: TraceEvent) {
        for (writer in writers) {
            try {
                writer.write(event)
            } catch (_: Exception) {
                // 追踪不应影响主流程
            }
        }
    }

    /** 关闭所有 writer */
    fun close() {
        for (writer in writers) {
            try {
                writer.close()
            } catch (_: Exception) {
            }
        }
    }

    /** 构建器 */
    class Builder {
        private val writers = mutableListOf<TraceWriter>()

        fun addWriter(writer: TraceWriter): Builder {
            writers.add(writer)
            return this
        }

        fun build(): Tracing = Tracing(writers.toList())
    }

    companion object {
        /** DSL 构建 */
        fun build(block: Builder.() -> Unit): Tracing {
            return Builder().apply(block).build()
        }

        /** 空实现，不记录任何事件 */
        val NOOP = Tracing(emptyList())
    }
}
