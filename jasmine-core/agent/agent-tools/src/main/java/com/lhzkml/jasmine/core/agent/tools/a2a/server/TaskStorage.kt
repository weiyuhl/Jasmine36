package com.lhzkml.jasmine.core.agent.tools.a2a.server

import com.lhzkml.jasmine.core.agent.tools.a2a.TaskOperationException
import com.lhzkml.jasmine.core.agent.tools.a2a.model.*
import kotlinx.serialization.json.JsonObject

/**
 * 任务存储接口
 * 完整移植 koog 的 TaskStorage
 *
 * 管理任务及其生命周期事件的存储。实现必须保证并发安全。
 */
interface TaskStorage {
    suspend fun get(
        taskId: String,
        historyLength: Int? = 0,
        includeArtifacts: Boolean = false
    ): Task?

    suspend fun getAll(
        taskIds: List<String>,
        historyLength: Int? = 0,
        includeArtifacts: Boolean = false
    ): List<Task>

    suspend fun getByContext(
        contextId: String,
        historyLength: Int? = 0,
        includeArtifacts: Boolean = false
    ): List<Task>

    suspend fun update(event: TaskEvent)

    suspend fun delete(taskId: String)

    suspend fun deleteAll(taskIds: List<String>)
}

/**
 * 内存任务存储
 * 完整移植 koog 的 InMemoryTaskStorage
 */
class InMemoryTaskStorage : TaskStorage {
    private val tasks = mutableMapOf<String, Task>()
    private val tasksByContext = mutableMapOf<String, MutableSet<String>>()
    private val lock = Any()

    override suspend fun get(
        taskId: String,
        historyLength: Int?,
        includeArtifacts: Boolean
    ): Task? = synchronized(lock) {
        historyLength?.let { require(it >= 0) { "historyLength must be non-negative" } }

        val task = tasks[taskId] ?: return@synchronized null
        val needsModification = historyLength != null || !includeArtifacts

        if (needsModification) {
            task.copy(
                history = if (historyLength != null) task.history?.takeLast(historyLength) else task.history,
                artifacts = if (includeArtifacts) task.artifacts else null
            )
        } else {
            task
        }
    }

    override suspend fun getAll(
        taskIds: List<String>,
        historyLength: Int?,
        includeArtifacts: Boolean
    ): List<Task> = taskIds.mapNotNull { get(it, historyLength, includeArtifacts) }

    override suspend fun getByContext(
        contextId: String,
        historyLength: Int?,
        includeArtifacts: Boolean
    ): List<Task> = synchronized(lock) {
        val contextTaskIds = tasksByContext[contextId] ?: emptySet()
        contextTaskIds.toList()
    }.mapNotNull { get(it, historyLength, includeArtifacts) }

    override suspend fun update(event: TaskEvent): Unit = synchronized(lock) {
        when (event) {
            is Task -> {
                val oldTask = tasks[event.id]
                if (oldTask != null && event.contextId != oldTask.contextId) {
                    throw TaskOperationException("Cannot change context for existing task: ${event.id}")
                }
                tasks[event.id] = event
                tasksByContext.getOrPut(event.contextId) { mutableSetOf() }.add(event.id)
            }
            is TaskStatusUpdateEvent -> {
                val existing = tasks[event.taskId]
                    ?: throw TaskOperationException("Cannot update status for non-existing task: ${event.taskId}")
                tasks[event.taskId] = existing.copy(
                    status = event.status,
                    history = existing.status.message
                        ?.let { existing.history.orEmpty() + it }
                        ?: existing.history,
                    metadata = mergeMetadata(existing.metadata, event.metadata)
                )
            }
            is TaskArtifactUpdateEvent -> {
                val existing = tasks[event.taskId]
                    ?: throw TaskOperationException("Cannot update artifact for non-existing task: ${event.taskId}")
                val currentArtifacts = existing.artifacts?.toMutableList() ?: mutableListOf()
                val idx = currentArtifacts.indexOfFirst { it.artifactId == event.artifact.artifactId }
                if (idx >= 0) {
                    currentArtifacts[idx] = if (event.append == true) {
                        currentArtifacts[idx].copy(parts = currentArtifacts[idx].parts + event.artifact.parts)
                    } else {
                        event.artifact
                    }
                } else {
                    currentArtifacts.add(event.artifact)
                }
                tasks[event.taskId] = existing.copy(
                    artifacts = currentArtifacts,
                    metadata = mergeMetadata(existing.metadata, event.metadata)
                )
            }
        }
    }

    override suspend fun delete(taskId: String): Unit = synchronized(lock) {
        tasks.remove(taskId)?.let { task ->
            tasksByContext[task.contextId]?.remove(taskId)
            if (tasksByContext[task.contextId]?.isEmpty() == true) {
                tasksByContext.remove(task.contextId)
            }
        }
    }

    override suspend fun deleteAll(taskIds: List<String>): Unit = synchronized(lock) {
        taskIds.forEach { taskId ->
            tasks.remove(taskId)?.let { task ->
                tasksByContext[task.contextId]?.remove(taskId)
                if (tasksByContext[task.contextId]?.isEmpty() == true) {
                    tasksByContext.remove(task.contextId)
                }
            }
        }
    }

    private fun mergeMetadata(existing: JsonObject?, new: JsonObject?): JsonObject? {
        if (existing == null) return new
        if (new == null) return existing
        return JsonObject(existing + new)
    }
}

/**
 * 上下文限定的任务存储
 * 完整移植 koog 的 ContextTaskStorage
 */
class ContextTaskStorage(
    private val contextId: String,
    private val taskStorage: TaskStorage
) {
    suspend fun get(
        taskId: String,
        historyLength: Int? = 0,
        includeArtifacts: Boolean = false
    ): Task? = taskStorage.get(taskId, historyLength, includeArtifacts)

    suspend fun getAll(
        taskIds: List<String>,
        historyLength: Int? = 0,
        includeArtifacts: Boolean = false
    ): List<Task> = taskStorage.getAll(taskIds, historyLength, includeArtifacts)

    suspend fun getByContext(
        historyLength: Int? = 0,
        includeArtifacts: Boolean = false
    ): List<Task> = taskStorage.getByContext(contextId, historyLength, includeArtifacts)

    suspend fun delete(taskId: String) {
        get(taskId, historyLength = 0, includeArtifacts = false)?.let { task ->
            require(task.contextId == contextId) {
                "contextId of the task requested to be deleted must be same as current contextId"
            }
            taskStorage.delete(taskId)
        }
    }

    suspend fun deleteAll(taskIds: List<String>) {
        getAll(taskIds, historyLength = 0, includeArtifacts = false).let { tasks ->
            require(tasks.all { it.contextId == contextId }) {
                "contextId of the tasks requested to be deleted must be same as current contextId"
            }
            taskStorage.deleteAll(taskIds)
        }
    }
}
