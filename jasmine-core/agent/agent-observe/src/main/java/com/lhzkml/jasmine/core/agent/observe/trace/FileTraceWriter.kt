package com.lhzkml.jasmine.core.agent.observe.trace

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

/**
 * 文件追踪写入器
 * 参考 koog 的 TraceFeatureMessageFileWriter，将事件写入本地文件。
 *
 * @param file 输出文件
 * @param format 自定义格式化函数（可选）
 * @param filter 事件过滤器（可选）
 * @param append 是否追加模式，默认 true
 */
class FileTraceWriter(
    private val file: File,
    private val format: ((TraceEvent) -> String)? = null,
    private val filter: TraceEventFilter? = null,
    append: Boolean = true
) : TraceWriter {

    private val writer: BufferedWriter = BufferedWriter(FileWriter(file, append))

    override suspend fun write(event: TraceEvent) {
        if (filter != null && !filter.invoke(event)) return

        val message = format?.invoke(event) ?: TraceMessageFormat.format(event)

        withContext(Dispatchers.IO) {
            writer.write(message)
            writer.newLine()
            writer.flush()
        }
    }

    override fun close() {
        try {
            writer.close()
        } catch (_: Exception) {
        }
    }
}
