package com.lhzkml.jasmine.core.agent.tools.a2a.server.notifications

import com.lhzkml.jasmine.core.agent.tools.a2a.model.PushNotificationConfig
import com.lhzkml.jasmine.core.agent.tools.a2a.model.Task
import java.io.Closeable
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 简单推送通知发送器
 * 完整移植 koog 的 SimplePushNotificationSender
 *
 * 不执行任何配置验证。
 * 始终使用 [PushNotificationConfig.authentication] 中提供的第一个认证方案。
 *
 * 注意：koog 使用 Ktor HttpClient，jasmine 使用 HttpURLConnection 以避免额外依赖。
 * 生产环境建议替换为 OkHttp 实现。
 *
 * @param json JSON 序列化器
 */
class SimplePushNotificationSender(
    private val json: Json = Json { ignoreUnknownKeys = true }
) : PushNotificationSender, Closeable {

    override suspend fun send(config: PushNotificationConfig, task: Task) {
        try {
            val url = URL(config.url)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            // 认证：使用第一个方案
            config.authentication?.let { auth ->
                val schema = auth.schemes.firstOrNull()
                val credentials = auth.credentials
                if (schema != null && credentials != null) {
                    connection.setRequestProperty("Authorization", "$schema $credentials")
                }
            }

            // 通知令牌
            config.token?.let { token ->
                connection.setRequestProperty(
                    PushNotificationSender.A2A_NOTIFICATION_TOKEN_HEADER,
                    token
                )
            }

            // 发送任务 JSON
            val body = json.encodeToString(task)
            connection.outputStream.use { it.write(body.toByteArray()) }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                System.err.println(
                    "Push notification failed: configId='${config.id}' taskId='${task.id}' " +
                        "responseCode=$responseCode"
                )
            }

            connection.disconnect()
        } catch (e: Exception) {
            System.err.println(
                "Failed to send push notification: configId='${config.id}' taskId='${task.id}' " +
                    "error=${e.message}"
            )
        }
    }

    override fun close() {
        // HttpURLConnection 不需要全局关闭
    }
}
