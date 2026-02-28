package com.lhzkml.jasmine.core.agent.observe.trace

/**
 * 追踪事件写入器接口
 * 参考 koog 的 FeatureMessageProcessor / FeatureMessageLogWriter / FeatureMessageFileWriter 体系。
 *
 * koog 有三种 writer：Log、File、Remote。
 * jasmine 移植 Log（Android Log）、File（java.io）、Callback（UI 回调），
 * 去掉 Remote（jasmine 是 Android 本地应用，暂不需要远程上报）。
 */
interface TraceWriter {
    /** 处理一个追踪事件 */
    suspend fun write(event: TraceEvent)

    /** 关闭写入器，释放资源 */
    fun close() {}
}

/**
 * 事件过滤器
 * 参考 koog 的 setMessageFilter
 */
typealias TraceEventFilter = (TraceEvent) -> Boolean
