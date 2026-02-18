package com.lhzkml.jasmine.core.agent.tools.a2a.server.notifications

import com.lhzkml.jasmine.core.agent.tools.a2a.model.PushNotificationConfig
import com.lhzkml.jasmine.core.agent.tools.a2a.model.Task

/**
 * 推送通知发送器接口
 * 完整移植 koog 的 PushNotificationSender
 *
 * [A2A 规范中的推送通知](https://a2a-protocol.org/latest/specification/#95-push-notification-setup-and-usage)
 */
interface PushNotificationSender {
    companion object {
        /**
         * 自定义可选 HTTP 头，用于包含认证 A2A 通知的令牌。
         */
        const val A2A_NOTIFICATION_TOKEN_HEADER: String = "X-A2A-Notification-Token"
    }

    /**
     * 发送推送通知。
     *
     * @param config 推送通知配置
     * @param task 要在通知中发送的任务对象
     */
    suspend fun send(config: PushNotificationConfig, task: Task)
}
