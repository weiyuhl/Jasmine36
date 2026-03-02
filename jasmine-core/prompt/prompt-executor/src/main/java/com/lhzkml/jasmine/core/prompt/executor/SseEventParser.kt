package com.lhzkml.jasmine.core.prompt.executor

import io.ktor.utils.io.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * 独立的 SSE (Server-Sent Events) 协议解析器。
 *
 * 从 Ktor [ByteReadChannel] 读取 SSE 流，解析 `data:` 行，
 * 并通过 Kotlin [SendChannel] 将数据载荷分发给消费者，
 * 实现 SSE 协议解析与业务事件处理的解耦。
 */
object SseEventParser {

    /**
     * 从 [byteChannel] 持续读取 SSE 行，将 `data:` 载荷发送到 [output]。
     *
     * - 自动跳过空行和非 `data:` 开头的行（如 `event:`, `:` 注释等）
     * - 遇到 [doneSignals] 中的标记时终止解析（如 OpenAI 的 `[DONE]`）
     * - 流结束或异常时自动关闭 [output] channel
     * - 支持协程取消
     *
     * @param byteChannel Ktor HTTP 响应的字节读取通道
     * @param output 用于分发解析后数据载荷的发送通道
     * @param doneSignals 终止标记集合，默认为空（不检查终止标记）
     */
    suspend fun parse(
        byteChannel: ByteReadChannel,
        output: SendChannel<String>,
        doneSignals: Set<String> = emptySet()
    ) {
        try {
            while (!byteChannel.isClosedForRead) {
                coroutineContext.ensureActive()
                val line = try {
                    byteChannel.readUTF8Line()
                } catch (_: Exception) {
                    break
                } ?: break

                if (line.isEmpty()) continue

                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data.isEmpty()) continue
                    if (doneSignals.isNotEmpty() && data in doneSignals) break
                    output.send(data)
                }
            }
        } finally {
            output.close()
        }
    }
}
