package com.lhzkml.jasmine.core.prompt.llm

import com.lhzkml.jasmine.core.prompt.model.Concept
import com.lhzkml.jasmine.core.prompt.model.Fact
import com.lhzkml.jasmine.core.prompt.model.MemoryScope
import com.lhzkml.jasmine.core.prompt.model.MemorySubject

/**
 * 记忆存储提供者接口
 * 参考 koog 的 AgentMemoryProvider
 *
 * 定义记忆的 CRUD 操作，支持按 subject + scope 组织存储。
 */
interface MemoryProvider {

    /**
     * 保存事实到记忆
     * @param fact 要保存的事实（SingleFact 或 MultipleFacts）
     * @param subject 上下文主题（如 User, Project）
     * @param scope 可见性作用域（如 Agent, Product）
     */
    suspend fun save(fact: Fact, subject: MemorySubject, scope: MemoryScope)

    /**
     * 按概念加载事实
     * @param concept 要检索的概念
     * @param subject 上下文主题
     * @param scope 可见性作用域
     * @return 匹配的事实列表，按时间排序
     */
    suspend fun load(concept: Concept, subject: MemorySubject, scope: MemoryScope): List<Fact>

    /**
     * 加载指定上下文中的所有事实
     * @param subject 上下文主题
     * @param scope 可见性作用域
     * @return 所有事实
     */
    suspend fun loadAll(subject: MemorySubject, scope: MemoryScope): List<Fact>

    /**
     * 按描述语义搜索事实
     * @param description 自然语言查询
     * @param subject 上下文主题
     * @param scope 可见性作用域
     * @return 匹配的事实列表
     */
    suspend fun loadByDescription(description: String, subject: MemorySubject, scope: MemoryScope): List<Fact>
}

/**
 * 空记忆提供者 — 不存储任何内容，仅记录日志
 * 参考 koog 的 NoMemory
 */
object NoMemory : MemoryProvider {
    override suspend fun save(fact: Fact, subject: MemorySubject, scope: MemoryScope) {
        // Memory feature is not enabled. Skipping saving fact for concept '${fact.concept.keyword}'
    }

    override suspend fun load(concept: Concept, subject: MemorySubject, scope: MemoryScope): List<Fact> {
        // Memory feature is not enabled. No facts will be loaded for concept '${concept.keyword}'
        return emptyList()
    }

    override suspend fun loadAll(subject: MemorySubject, scope: MemoryScope): List<Fact> {
        // Memory feature is not enabled. No facts will be loaded
        return emptyList()
    }

    override suspend fun loadByDescription(
        description: String,
        subject: MemorySubject,
        scope: MemoryScope
    ): List<Fact> {
        // Memory feature is not enabled. No facts will be loaded for question: '$description'
        return emptyList()
    }
}
