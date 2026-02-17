package com.lhzkml.jasmine.core.prompt.llm

import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.Concept
import com.lhzkml.jasmine.core.prompt.model.Fact
import com.lhzkml.jasmine.core.prompt.model.FactType
import com.lhzkml.jasmine.core.prompt.model.MemoryScope
import com.lhzkml.jasmine.core.prompt.model.MemoryScopeType
import com.lhzkml.jasmine.core.prompt.model.MemoryScopesProfile
import com.lhzkml.jasmine.core.prompt.model.MemorySubject
import com.lhzkml.jasmine.core.prompt.model.MultipleFacts
import com.lhzkml.jasmine.core.prompt.model.SingleFact
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Agent 记忆系统
 * 参考 koog 的 AgentMemory，提供从对话历史中提取事实并持久化存储的能力。
 *
 * 核心功能：
 * - saveFactsFromHistory: 从对话历史中提取指定概念的事实并保存
 * - saveAutoDetectedFacts: 自动检测并提取对话中的所有重要事实
 * - loadFactsToAgent: 从记忆中加载指定概念的事实并注入到 LLM 上下文
 * - loadAllFactsToAgent: 从记忆中加载所有事实并注入到 LLM 上下文
 * - save/load/loadAll: 直接操作记忆存储
 *
 * @param provider 记忆存储提供者
 * @param scopesProfile 作用域配置文件，维护 MemoryScopeType → 名称 的映射
 */
class AgentMemory(
    val provider: MemoryProvider,
    val scopesProfile: MemoryScopesProfile = MemoryScopesProfile()
) {
    /**
     * 从对话历史中提取事实并保存到记忆
     *
     * 流程：
     * 1. 把当前对话历史包装成 XML 格式
     * 2. 用提取提示词让 LLM 从历史中提取事实
     * 3. 解析 LLM 回复为 Fact 对象
     * 4. 保存到记忆存储
     * 5. 恢复原始 prompt
     *
     * @param session 当前 LLM 会话
     * @param concept 要提取的概念
     * @param subject 记忆主题
     * @param scope 记忆作用域
     * @param retrievalModel 用于事实提取的模型名称（可选，默认使用当前 session 的模型）
     * @param retrievalClient 用于事实提取的 ChatClient（可选，默认使用当前 session 的 client）
     */
    suspend fun saveFactsFromHistory(
        session: LLMSession,
        concept: Concept,
        subject: MemorySubject,
        scope: MemoryScope,
        retrievalModel: String? = null,
        retrievalClient: ChatClient? = null
    ) {
        if (retrievalModel != null || retrievalClient != null) {
            // 使用指定的模型/客户端创建临时 session 进行提取
            val tempSession = LLMSession(
                client = retrievalClient ?: session.currentClient,
                model = retrievalModel ?: session.model,
                initialPrompt = session.prompt.copy()
            )
            val fact = retrieveFactsFromHistory(tempSession, concept)
            provider.save(fact, subject, scope)
        } else {
            val fact = retrieveFactsFromHistory(session, concept)
            provider.save(fact, subject, scope)
        }
    }

    /**
     * 自动检测并提取对话中的所有重要事实
     * 参考 koog 的 nodeSaveToMemoryAutoDetectFacts
     *
     * 流程：
     * 1. 让 LLM 分析对话历史，自动识别重要事实
     * 2. 解析 LLM 回复为 subject + fact 列表
     * 3. 保存到指定作用域
     * 4. 恢复原始 prompt
     *
     * @param session 当前 LLM 会话
     * @param scopes 要保存到的作用域类型列表
     * @param subjects 要检测的主题列表
     * @param retrievalModel 用于事实提取的模型名称（可选）
     * @param retrievalClient 用于事实提取的 ChatClient（可选）
     */
    suspend fun saveAutoDetectedFacts(
        session: LLMSession,
        scopes: List<MemoryScopeType> = listOf(MemoryScopeType.AGENT),
        subjects: List<MemorySubject> = MemorySubject.registeredSubjects,
        retrievalModel: String? = null,
        retrievalClient: ChatClient? = null
    ) {
        val initialPrompt = session.prompt.copy()

        // 如果指定了 retrievalModel，创建临时 session
        val workSession = if (retrievalModel != null || retrievalClient != null) {
            LLMSession(
                client = retrievalClient ?: session.currentClient,
                model = retrievalModel ?: session.model,
                initialPrompt = initialPrompt.copy()
            )
        } else {
            session
        }

        workSession.appendPrompt {
            user(MemoryPrompts.autoDetectFactsPrompt(subjects, HISTORY_WRAPPER_TAG))
        }

        val response = workSession.requestLLMWithoutTools()

        // 解析并保存
        scopes.mapNotNull(scopesProfile::getScope).forEach { scope ->
            val facts = parseFactsFromResponse(response.content)
            facts.forEach { (subject, fact) ->
                provider.save(fact, subject, scope)
            }
        }

        // 恢复原始 prompt（只恢复原始 session）
        session.rewritePrompt { initialPrompt }
    }

    /**
     * 从记忆中加载指定概念的事实并注入到 LLM 会话上下文
     *
     * 按 subject 优先级排序，SingleFact 只保留最高优先级的。
     * 加载的事实以 user 消息形式追加到 session.prompt。
     *
     * @param session 当前 LLM 会话
     * @param concept 要加载的概念
     * @param scopes 要搜索的作用域类型列表
     * @param subjects 要搜索的主题列表
     */
    suspend fun loadFactsToAgent(
        session: LLMSession,
        concept: Concept,
        scopes: List<MemoryScopeType> = MemoryScopeType.entries,
        subjects: List<MemorySubject> = MemorySubject.registeredSubjects
    ) {
        loadFactsToAgentImpl(session, scopes, subjects) { subject, scope ->
            provider.load(concept, subject, scope)
        }
    }

    /**
     * 从记忆中加载所有事实并注入到 LLM 会话上下文
     * 参考 koog 的 loadAllFactsToAgent
     *
     * @param session 当前 LLM 会话
     * @param scopes 要搜索的作用域类型列表
     * @param subjects 要搜索的主题列表
     */
    suspend fun loadAllFactsToAgent(
        session: LLMSession,
        scopes: List<MemoryScopeType> = MemoryScopeType.entries,
        subjects: List<MemorySubject> = MemorySubject.registeredSubjects
    ) {
        loadFactsToAgentImpl(session, scopes, subjects, provider::loadAll)
    }

    /**
     * 加载事实的内部实现
     * 参考 koog 的 loadFactsToAgentImpl
     *
     * 处理逻辑：
     * 1. 按 subject 优先级排序
     * 2. SingleFact 只保留最高优先级的（同一 keyword）
     * 3. MultipleFacts 全部保留
     * 4. 按 concept 分组，每组生成一条 user 消息
     */
    private suspend fun loadFactsToAgentImpl(
        session: LLMSession,
        scopes: List<MemoryScopeType>,
        subjects: List<MemorySubject>,
        loadFacts: suspend (subject: MemorySubject, scope: MemoryScope) -> List<Fact>
    ) {
        val allFacts = mutableListOf<Fact>()
        val singleFactsByKeyword = mutableMapOf<String, Pair<MemorySubject, SingleFact>>()

        val sortedSubjects = subjects.sortedBy { it.priorityLevel }

        for (scopeType in scopes) {
            val scope = scopesProfile.getScope(scopeType) ?: continue
            for (subject in sortedSubjects) {
                val subjectFacts = loadFacts(subject, scope)

                for (fact in subjectFacts) {
                    when (fact) {
                        is SingleFact -> {
                            val existing = singleFactsByKeyword[fact.concept.keyword]
                            if (existing == null || subject.priorityLevel < existing.first.priorityLevel) {
                                singleFactsByKeyword[fact.concept.keyword] = subject to fact
                            }
                        }
                        is MultipleFacts -> allFacts.add(fact)
                    }
                }
            }
        }

        allFacts.addAll(singleFactsByKeyword.values.map { it.second })

        val factsByConcept = allFacts.groupBy { it.concept }
        if (factsByConcept.isEmpty()) return

        for ((concept, facts) in factsByConcept) {
            val message = buildString {
                appendLine("Here are the relevant facts from memory about [${concept.keyword}](${concept.description.shortened()}):")
                for (fact in facts) {
                    when (fact) {
                        is SingleFact -> appendLine("- [${fact.concept.keyword}]: ${fact.value}")
                        is MultipleFacts -> {
                            appendLine("- [${fact.concept.keyword}]:")
                            fact.values.forEach { appendLine("  - $it") }
                        }
                    }
                }
            }
            session.appendPrompt { user(message) }
        }
    }

    /**
     * 直接保存事实
     */
    suspend fun save(fact: Fact, subject: MemorySubject, scope: MemoryScope) {
        provider.save(fact, subject, scope)
    }

    /**
     * 直接加载事实
     */
    suspend fun load(concept: Concept, subject: MemorySubject, scope: MemoryScope): List<Fact> {
        return provider.load(concept, subject, scope)
    }

    /**
     * 加载全部事实
     */
    suspend fun loadAll(subject: MemorySubject, scope: MemoryScope): List<Fact> {
        return provider.loadAll(subject, scope)
    }

    /**
     * 按描述搜索事实
     */
    suspend fun loadByDescription(description: String, subject: MemorySubject, scope: MemoryScope): List<Fact> {
        return provider.loadByDescription(description, subject, scope)
    }

    companion object {
        /** 历史包装 XML 标签 */
        const val HISTORY_WRAPPER_TAG = "conversation_to_extract_facts"
    }
}

// ========== 事实提取 ==========

/**
 * 从对话历史中提取事实
 * 参考 koog 的 retrieveFactsFromHistory
 *
 * 流程：
 * 1. 保存原始 prompt
 * 2. 把历史消息包装成 XML
 * 3. 用提取提示词重写 prompt
 * 4. 请求 LLM（不带工具）
 * 5. 解析回复为 Fact
 * 6. 恢复原始 prompt
 */
suspend fun retrieveFactsFromHistory(
    session: LLMSession,
    concept: Concept
): Fact {
    val tag = AgentMemory.HISTORY_WRAPPER_TAG
    val oldPrompt = session.prompt

    // 去掉末尾工具调用
    session.dropTrailingToolCalls()

    // 把历史包装成 XML
    val combinedMessage = buildHistoryXml(oldPrompt.messages)

    // 构建提取提示词
    val extractionPrompt = when (concept.factType) {
        FactType.SINGLE -> MemoryPrompts.singleFactPrompt(concept, tag)
        FactType.MULTIPLE -> MemoryPrompts.multipleFactsPrompt(concept, tag)
    }

    // 重写 prompt 为提取模式
    session.rewritePrompt { prompt ->
        com.lhzkml.jasmine.core.prompt.model.Prompt.build(prompt.id) {
            system(extractionPrompt)
            user(combinedMessage)
        }
    }

    val timestamp = System.currentTimeMillis()

    // 请求 LLM 提取事实
    val result = session.requestLLMWithoutTools()
    val responseText = result.content.trim()

    // 解析回复
    val fact: Fact = when (concept.factType) {
        FactType.SINGLE -> SingleFact(
            concept = concept,
            timestamp = timestamp,
            value = responseText.ifEmpty { "No facts extracted" }
        )
        FactType.MULTIPLE -> {
            val lines = responseText.lines()
                .map { it.trimStart('-', ' ', '*', '•') }
                .filter { it.isNotBlank() }
            MultipleFacts(
                concept = concept,
                timestamp = timestamp,
                values = lines.ifEmpty { listOf("No facts extracted") }
            )
        }
    }

    // 恢复原始 prompt
    session.rewritePrompt { oldPrompt }

    return fact
}

/**
 * 解析自动检测事实的 LLM 回复
 * 参考 koog 的 parseFactsFromResponse
 *
 * 支持两种格式：
 * 1. JSON 格式（koog 原始格式）
 * 2. 管道分隔格式（subject|keyword|description|value）
 */
fun parseFactsFromResponse(content: String): List<Pair<MemorySubject, Fact>> {
    val timestamp = System.currentTimeMillis()

    // 尝试 JSON 格式
    try {
        @Serializable
        data class FactEntry(
            val subject: String,
            val keyword: String,
            val description: String,
            val value: String
        )

        val json = Json { ignoreUnknownKeys = true }
        val entries = json.decodeFromString<List<FactEntry>>(content)
        val grouped = entries.groupBy { it.subject to it.keyword }

        return grouped.map { (key, facts) ->
            val subject = MemorySubject.findByName(key.first)
                ?: MemorySubject.Everything

            if (facts.size == 1) {
                val entry = facts.single()
                subject to SingleFact(
                    concept = Concept(entry.keyword, entry.description, FactType.SINGLE),
                    timestamp = timestamp,
                    value = entry.value
                )
            } else {
                subject to MultipleFacts(
                    concept = Concept(key.second, facts.first().description, FactType.MULTIPLE),
                    timestamp = timestamp,
                    values = facts.map { it.value }
                )
            }
        }
    } catch (_: Exception) {
        // JSON 解析失败，尝试管道分隔格式
    }

    // 管道分隔格式: subject|keyword|description|value
    return content.lines()
        .filter { it.contains('|') }
        .mapNotNull { line ->
            val parts = line.split('|').map { it.trim() }
            if (parts.size < 4) return@mapNotNull null

            val subject = MemorySubject.findByName(parts[0])
                ?: MemorySubject.Everything

            subject to SingleFact(
                concept = Concept(parts[1], parts[2], FactType.SINGLE),
                timestamp = timestamp,
                value = parts[3]
            )
        }
}

// ========== 内部辅助 ==========

/**
 * 把消息列表包装成 XML 格式
 * 参考 koog 的 retrieveFactsFromHistory 中的 XML 包装逻辑
 *
 * 完整处理所有消息类型，包括 assistant 的工具调用。
 */
internal fun buildHistoryXml(
    messages: List<com.lhzkml.jasmine.core.prompt.model.ChatMessage>
): String {
    val tag = AgentMemory.HISTORY_WRAPPER_TAG
    return buildString {
        appendLine("<$tag>")
        messages.forEach { msg ->
            when {
                msg.role == "system" -> appendLine("<system>\n${msg.content}\n</system>")
                msg.role == "user" -> appendLine("<user>\n${msg.content}\n</user>")
                msg.role == "assistant" && msg.toolCalls != null -> {
                    // assistant 消息带工具调用
                    if (msg.content.isNotBlank()) {
                        appendLine("<assistant>\n${msg.content}\n</assistant>")
                    }
                    val calls = msg.toolCalls ?: emptyList()
                    calls.forEach { call ->
                        appendLine("<tool_call tool=${call.name}>\n${call.arguments}\n</tool_call>")
                    }
                }
                msg.role == "assistant" -> appendLine("<assistant>\n${msg.content}\n</assistant>")
                msg.role == "tool" -> appendLine("<tool_result tool=${msg.toolName}>\n${msg.content}\n</tool_result>")
            }
        }
        appendLine("</$tag>")
    }
}

/** 截断字符串用于显示 */
private fun String.shortened() = lines().first().take(100) + "..."
