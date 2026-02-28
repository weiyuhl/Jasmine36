package com.lhzkml.jasmine.core.agent.observe.trace

import android.util.Log

/**
 * Android Log 追踪写入器
 * 参考 koog 的 TraceFeatureMessageLogWriter，将事件写入 Android Logcat。
 *
 * @param tag Logcat 标签
 * @param level Log 级别（Log.DEBUG / Log.INFO / Log.WARN 等）
 * @param format 自定义格式化函数（可选）
 * @param filter 事件过滤器（可选），返回 true 的事件才会被写入
 */
class LogTraceWriter(
    private val tag: String = "JasmineTrace",
    private val level: Int = Log.INFO,
    private val format: ((TraceEvent) -> String)? = null,
    private val filter: TraceEventFilter? = null
) : TraceWriter {

    override suspend fun write(event: TraceEvent) {
        if (filter != null && !filter.invoke(event)) return

        val message = format?.invoke(event) ?: TraceMessageFormat.format(event)

        when (level) {
            Log.VERBOSE -> Log.v(tag, message)
            Log.DEBUG -> Log.d(tag, message)
            Log.INFO -> Log.i(tag, message)
            Log.WARN -> Log.w(tag, message)
            Log.ERROR -> Log.e(tag, message)
            else -> Log.i(tag, message)
        }
    }
}
