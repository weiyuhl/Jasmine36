package com.lhzkml.jasmine.core.agent.tools.snapshot

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 持久化存储提供者接口
 * 完整移植 koog 的 PersistenceStorageProvider，定义检查点的存取操作。
 *
 * @param Filter 过滤器类型，用于筛选检查点
 *
 * 不同实现可以使用不同的存储后端：
 * - [InMemoryPersistenceStorageProvider] — 内存存储（测试用）
 * - [FilePersistenceStorageProvider] — 文件存储（Android 本地文件）
 * - [NoPersistenceStorageProvider] — 空实现（禁用持久化）
 */
interface PersistenceStorageProvider<Filter> {
    /** 获取指定 Agent 的所有检查点，可选过滤 */
    suspend fun getCheckpoints(agentId: String, filter: Filter? = null): List<AgentCheckpoint>

    /** 保存检查点 */
    suspend fun saveCheckpoint(agentId: String, checkpoint: AgentCheckpoint)

    /** 获取最新的检查点，可选过滤 */
    suspend fun getLatestCheckpoint(agentId: String, filter: Filter? = null): AgentCheckpoint?

    /** 删除指定 Agent 的所有检查点 */
    suspend fun deleteCheckpoints(agentId: String)

    /** 删除指定检查点 */
    suspend fun deleteCheckpoint(agentId: String, checkpointId: String)
}

/**
 * 空持久化提供者 — 不保存任何检查点
 * 完整移植 koog 的 NoPersistencyStorageProvider
 */
class NoPersistenceStorageProvider : PersistenceStorageProvider<Unit> {
    override suspend fun getCheckpoints(agentId: String, filter: Unit?): List<AgentCheckpoint> =
        emptyList()

    override suspend fun saveCheckpoint(agentId: String, checkpoint: AgentCheckpoint) {
        Log.i("Persistence", "Snapshot feature is not enabled. Snapshot will not be saved: ${checkpoint.checkpointId}")
    }

    override suspend fun getLatestCheckpoint(agentId: String, filter: Unit?): AgentCheckpoint? =
        null

    override suspend fun deleteCheckpoints(agentId: String) {}

    override suspend fun deleteCheckpoint(agentId: String, checkpointId: String) {}
}

/**
 * 内存持久化提供者 — 检查点保存在内存中
 * 完整移植 koog 的 InMemoryPersistencyStorageProvider，包含 Mutex 线程安全。
 */
class InMemoryPersistenceStorageProvider :
    PersistenceStorageProvider<AgentCheckpointPredicateFilter> {

    private val mutex = Mutex()
    private val snapshotMap = mutableMapOf<String, List<AgentCheckpoint>>()

    override suspend fun getCheckpoints(
        agentId: String,
        filter: AgentCheckpointPredicateFilter?
    ): List<AgentCheckpoint> {
        mutex.withLock {
            val allCheckpoints = snapshotMap[agentId] ?: emptyList()
            if (filter != null) {
                return allCheckpoints.filter { filter.check(it) }
            }
            return allCheckpoints
        }
    }

    override suspend fun saveCheckpoint(agentId: String, checkpoint: AgentCheckpoint) {
        mutex.withLock {
            snapshotMap[agentId] = (snapshotMap[agentId] ?: emptyList()) + checkpoint
        }
    }

    override suspend fun getLatestCheckpoint(
        agentId: String,
        filter: AgentCheckpointPredicateFilter?
    ): AgentCheckpoint? {
        mutex.withLock {
            if (filter != null) {
                return snapshotMap[agentId]?.filter { filter.check(it) }
                    ?.maxByOrNull { it.createdAt }
            }
            return snapshotMap[agentId]?.maxByOrNull { it.version }
        }
    }

    override suspend fun deleteCheckpoints(agentId: String) {
        mutex.withLock {
            snapshotMap.remove(agentId)
        }
    }

    override suspend fun deleteCheckpoint(agentId: String, checkpointId: String) {
        mutex.withLock {
            snapshotMap[agentId] = (snapshotMap[agentId] ?: emptyList())
                .filter { it.checkpointId != checkpointId }
        }
    }
}

/**
 * 文件持久化提供者 — 检查点保存到本地文件
 * 参考 koog 的 FilePersistencyStorageProvider
 *
 * 使用 JSON 序列化，每个 Agent 一个目录，每个检查点一个文件。
 *
 * @param baseDir 基础目录路径
 */
class FilePersistenceStorageProvider(
    private val baseDir: java.io.File
) : PersistenceStorageProvider<AgentCheckpointPredicateFilter> {

    init {
        baseDir.mkdirs()
    }

    private fun agentDir(agentId: String): java.io.File =
        java.io.File(baseDir, agentId).also { it.mkdirs() }

    override suspend fun getCheckpoints(agentId: String, filter: AgentCheckpointPredicateFilter?): List<AgentCheckpoint> {
        val dir = agentDir(agentId)
        return dir.listFiles()?.filter { it.extension == "json" }?.mapNotNull { file ->
            try {
                deserializeCheckpoint(file.readText())
            } catch (e: Exception) {
                null
            }
        }?.let { checkpoints ->
            if (filter != null) checkpoints.filter { filter.check(it) }
            else checkpoints
        }?.sortedBy { it.createdAt } ?: emptyList()
    }

    override suspend fun saveCheckpoint(agentId: String, checkpoint: AgentCheckpoint) {
        val file = java.io.File(agentDir(agentId), "${checkpoint.checkpointId}.json")
        file.writeText(serializeCheckpoint(checkpoint))
    }

    override suspend fun getLatestCheckpoint(agentId: String, filter: AgentCheckpointPredicateFilter?): AgentCheckpoint? {
        val checkpoints = getCheckpoints(agentId, filter)
        return checkpoints.maxByOrNull { it.createdAt }
    }

    override suspend fun deleteCheckpoints(agentId: String) {
        agentDir(agentId).deleteRecursively()
    }

    override suspend fun deleteCheckpoint(agentId: String, checkpointId: String) {
        val file = java.io.File(agentDir(agentId), "${checkpointId}.json")
        if (file.exists()) file.delete()
    }

    // 简单的 JSON 序列化（不依赖 kotlinx.serialization，保持轻量）
    private fun serializeCheckpoint(cp: AgentCheckpoint): String {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"checkpointId\":\"${cp.checkpointId}\",")
        sb.append("\"createdAt\":${cp.createdAt},")
        sb.append("\"nodePath\":\"${cp.nodePath}\",")
        sb.append("\"lastInput\":${cp.lastInput?.let { "\"${it.replace("\"", "\\\"")}\"" } ?: "null"},")
        sb.append("\"version\":${cp.version},")
        sb.append("\"messageCount\":${cp.messageHistory.size}")
        // 消息历史序列化为简化格式
        sb.append(",\"messages\":[")
        cp.messageHistory.forEachIndexed { i, msg ->
            if (i > 0) sb.append(",")
            sb.append("{\"role\":\"${msg.role}\",\"content\":\"${msg.content.replace("\"", "\\\"")}\"}")
        }
        sb.append("]")
        cp.properties?.let { props ->
            sb.append(",\"properties\":{")
            props.entries.forEachIndexed { i, (k, v) ->
                if (i > 0) sb.append(",")
                sb.append("\"$k\":\"$v\"")
            }
            sb.append("}")
        }
        sb.append("}")
        return sb.toString()
    }

    private fun deserializeCheckpoint(json: String): AgentCheckpoint {
        val checkpointId = extractString(json, "checkpointId")
        val createdAt = extractLong(json, "createdAt")
        val nodePath = extractString(json, "nodePath")
        val lastInput = extractNullableString(json, "lastInput")
        val version = extractLong(json, "version")

        val messages = mutableListOf<com.lhzkml.jasmine.core.prompt.model.ChatMessage>()
        val messagesStart = json.indexOf("\"messages\":[")
        if (messagesStart >= 0) {
            val arrayStart = json.indexOf("[", messagesStart)
            val arrayEnd = json.indexOf("]", arrayStart)
            if (arrayStart >= 0 && arrayEnd >= 0) {
                val arrayContent = json.substring(arrayStart + 1, arrayEnd)
                val msgPattern = Regex("\\{\"role\":\"([^\"]+)\",\"content\":\"([^\"]*)\"\\}")
                msgPattern.findAll(arrayContent).forEach { match ->
                    val role = match.groupValues[1]
                    val content = match.groupValues[2].replace("\\\"", "\"")
                    messages.add(com.lhzkml.jasmine.core.prompt.model.ChatMessage(role = role, content = content))
                }
            }
        }

        return AgentCheckpoint(
            checkpointId = checkpointId,
            createdAt = createdAt,
            nodePath = nodePath,
            lastInput = lastInput,
            messageHistory = messages,
            version = version
        )
    }

    private fun extractString(json: String, key: String): String {
        val pattern = Regex("\"$key\":\"([^\"]+)\"")
        return pattern.find(json)?.groupValues?.get(1) ?: ""
    }

    private fun extractNullableString(json: String, key: String): String? {
        val nullPattern = Regex("\"$key\":null")
        if (nullPattern.containsMatchIn(json)) return null
        return extractString(json, key).ifEmpty { null }
    }

    private fun extractLong(json: String, key: String): Long {
        val pattern = Regex("\"$key\":(\\d+)")
        return pattern.find(json)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    }
}
