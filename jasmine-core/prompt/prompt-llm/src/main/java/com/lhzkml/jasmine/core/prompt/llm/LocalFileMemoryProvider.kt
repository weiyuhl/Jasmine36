package com.lhzkml.jasmine.core.prompt.llm

import com.lhzkml.jasmine.core.prompt.model.Concept
import com.lhzkml.jasmine.core.prompt.model.Fact
import com.lhzkml.jasmine.core.prompt.model.MemoryScope
import com.lhzkml.jasmine.core.prompt.model.MemorySubject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File

/**
 * 本地文件记忆存储配置
 * 参考 koog 的 LocalMemoryConfig
 *
 * @param storageDirectory 存储子目录名
 * @param defaultScope 默认作用域
 */
data class LocalMemoryConfig(
    val storageDirectory: String,
    val defaultScope: MemoryScope = MemoryScope.CrossProduct
)

/**
 * 基于本地文件的记忆存储提供者
 * 参考 koog 的 LocalFileMemoryProvider，使用层级目录结构 + JSON 文件存储。
 *
 * 存储结构：
 * ```
 * rootDir/
 *   storageDirectory/
 *     agent/
 *       [agent-name]/
 *         subject/
 *           [subject-name]/
 *             facts.json
 *     feature/
 *       [feature-id]/
 *         subject/
 *           [subject-name]/
 *             facts.json
 *     product/
 *       [product-name]/
 *         subject/
 *           [subject-name]/
 *             facts.json
 *     organization/
 *       subject/
 *         [subject-name]/
 *           facts.json
 * ```
 *
 * @param config 存储配置
 * @param storage 存储后端（SimpleMemoryStorage 或 EncryptedMemoryStorage）
 * @param rootDir 存储根目录
 */
class LocalFileMemoryProvider(
    private val config: LocalMemoryConfig,
    private val storage: MemoryStorage,
    private val rootDir: File
) : MemoryProvider {

    /**
     * 便捷构造函数 — 使用 SimpleMemoryStorage
     */
    constructor(rootDir: File) : this(
        config = LocalMemoryConfig("memory"),
        storage = SimpleMemoryStorage(),
        rootDir = rootDir
    )

    /**
     * 便捷构造函数 — 指定存储目录名
     */
    constructor(rootDir: File, storageDirectory: String) : this(
        config = LocalMemoryConfig(storageDirectory),
        storage = SimpleMemoryStorage(),
        rootDir = rootDir
    )

    /** 线程安全锁 */
    private val mutex = Mutex()

    /** JSON 序列化配置 — prettyPrint 方便人工阅读 */
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /**
     * 构建存储路径
     * 参考 koog 的 getStoragePath，按 scope + subject 组织层级目录
     */
    private fun getStoragePath(subject: MemorySubject, scope: MemoryScope): File {
        val segments = listOf(config.storageDirectory) + when (scope) {
            is MemoryScope.Agent -> listOf("agent", scope.name, "subject", subject.name)
            is MemoryScope.Feature -> listOf("feature", scope.id, "subject", subject.name)
            is MemoryScope.Product -> listOf("product", scope.name, "subject", subject.name)
            MemoryScope.CrossProduct -> listOf("organization", "subject", subject.name)
        }
        val dir = segments.fold(rootDir) { acc, seg -> File(acc, seg) }
        return File(dir, "facts.json")
    }

    /**
     * 从存储加载事实（线程安全）
     * 返回 Map<概念keyword, List<Fact>>
     */
    private suspend fun loadFacts(path: File): Map<String, List<Fact>> = mutex.withLock {
        val content = storage.read(path) ?: return emptyMap()
        return try {
            json.decodeFromString<Map<String, List<Fact>>>(content)
        } catch (e: SerializationException) {
            emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * 保存事实到存储（线程安全）
     */
    private suspend fun saveFacts(path: File, facts: Map<String, List<Fact>>) = mutex.withLock {
        storage.createDirectories(File(rootDir, config.storageDirectory))
        storage.write(path, json.encodeToString(facts))
    }

    override suspend fun save(fact: Fact, subject: MemorySubject, scope: MemoryScope) {
        val path = getStoragePath(subject, scope)
        val facts = loadFacts(path).toMutableMap()
        val key = fact.concept.keyword
        facts[key] = (facts[key] ?: emptyList()) + fact
        saveFacts(path, facts)
    }

    override suspend fun load(concept: Concept, subject: MemorySubject, scope: MemoryScope): List<Fact> {
        val path = getStoragePath(subject, scope)
        return loadFacts(path)[concept.keyword] ?: emptyList()
    }

    override suspend fun loadAll(subject: MemorySubject, scope: MemoryScope): List<Fact> {
        val path = getStoragePath(subject, scope)
        return loadFacts(path).values.flatten()
    }

    override suspend fun loadByDescription(
        description: String,
        subject: MemorySubject,
        scope: MemoryScope
    ): List<Fact> {
        val path = getStoragePath(subject, scope)
        return loadFacts(path).values.flatten().filter { fact ->
            fact.concept.description.contains(description, ignoreCase = true)
        }
    }
}
