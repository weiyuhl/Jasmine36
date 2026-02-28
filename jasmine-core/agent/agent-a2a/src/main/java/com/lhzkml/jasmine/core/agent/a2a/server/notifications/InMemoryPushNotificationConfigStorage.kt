package com.lhzkml.jasmine.core.agent.a2a.server.notifications

import com.lhzkml.jasmine.core.agent.a2a.model.PushNotificationConfig
import com.lhzkml.jasmine.core.agent.a2a.utils.RWLock

/**
 * 内存推送通知配置存储
 * 完整移植 koog �?InMemoryPushNotificationConfigStorage
 *
 * 使用线程安全�?map 在内存中存储推送通知配置�?
 * 按任�?ID 分组，通过读写锁保证并发安全�?
 */
class InMemoryPushNotificationConfigStorage : PushNotificationConfigStorage {
    private val configsByTaskId = mutableMapOf<String, MutableMap<String?, PushNotificationConfig>>()
    private val rwLock = RWLock()

    override suspend fun save(taskId: String, pushNotificationConfig: PushNotificationConfig): Unit =
        rwLock.withWriteLock {
            val configId = pushNotificationConfig.id
            val taskConfigs = configsByTaskId.getOrPut(taskId) { mutableMapOf() }
            taskConfigs[configId] = pushNotificationConfig
        }

    override suspend fun getAll(taskId: String): List<PushNotificationConfig> = rwLock.withReadLock {
        configsByTaskId[taskId]?.values?.toList() ?: emptyList()
    }

    override suspend fun get(taskId: String, configId: String?): PushNotificationConfig? = rwLock.withReadLock {
        configsByTaskId[taskId]?.get(configId)
    }

    override suspend fun delete(taskId: String, configId: String?): Unit = rwLock.withWriteLock {
        if (configId == null) {
            configsByTaskId.remove(taskId)
        } else {
            configsByTaskId[taskId]?.let { taskConfigs ->
                taskConfigs.remove(configId)
                if (taskConfigs.isEmpty()) {
                    configsByTaskId.remove(taskId)
                }
            }
        }
    }
}
