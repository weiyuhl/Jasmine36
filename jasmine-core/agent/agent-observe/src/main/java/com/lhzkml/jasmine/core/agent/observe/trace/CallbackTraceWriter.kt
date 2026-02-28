package com.lhzkml.jasmine.core.agent.observe.trace

/**
 * 回调追踪写入器
 * koog 没有这个，这是 jasmine 新增的，方便 UI 层实时接收追踪事件。
 *
 * @param callback 事件回调
 * @param filter 事件过滤器（可选）
 */
class CallbackTraceWriter(
    private val callback: suspend (TraceEvent) -> Unit,
    private val filter: TraceEventFilter? = null
) : TraceWriter {

    override suspend fun write(event: TraceEvent) {
        if (filter != null && !filter.invoke(event)) return
        callback(event)
    }
}
