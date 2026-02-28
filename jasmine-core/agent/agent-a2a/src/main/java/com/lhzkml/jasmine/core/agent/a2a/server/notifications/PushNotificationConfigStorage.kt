package com.lhzkml.jasmine.core.agent.a2a.server.notifications

import com.lhzkml.jasmine.core.agent.a2a.PushNotificationException
import com.lhzkml.jasmine.core.agent.a2a.model.PushNotificationConfig

/**
 * 推送通知配置存储接口
 * 完整移植 koog �?PushNotificationConfigStorage
 *
 * 管理与任务更新关联的推送通知配置的存储�?
 * 实现必须保证并发安全�?
 */
interface PushNotificationConfigStorage {
    /**
     * 保存指定任务 ID 的推送通知配置�?
     *
     * @param taskId 任务 ID
     * @param pushNotificationConfig 推送通知配置
     * @throws PushNotificationException 如果配置无法保存
     */
    suspend fun save(taskId: String, pushNotificationConfig: PushNotificationConfig)

    /**
     * 获取指定任务 ID 和配�?ID 的推送通知配置�?
     *
     * @param taskId 任务 ID
     * @param configId 配置 ID
     */
    suspend fun get(taskId: String, configId: String?): PushNotificationConfig?

    /**
     * 获取指定任务 ID 的所有推送通知配置�?
     *
     * @param taskId 任务 ID
     */
    suspend fun getAll(taskId: String): List<PushNotificationConfig>

    /**
     * 删除指定任务 ID 的推送通知配置�?
     *
     * @param taskId 任务 ID
     * @param configId 可选的配置 ID。为 null 时删除该任务的所有配置�?
     * @throws PushNotificationException 如果配置无法删除
     */
    suspend fun delete(taskId: String, configId: String? = null)
}
