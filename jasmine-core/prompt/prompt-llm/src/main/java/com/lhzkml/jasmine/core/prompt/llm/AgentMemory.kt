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
 * Memory implementation for AI agents that provides persistent storage and retrieval of facts.
 * 完整移植 koog 的 AgentMemory
 *
 * 核心功能：
 * - saveFactsFromHistory: 从对话历史中提取指定概念的事实并保存
 * - loadFactsToAgent: 从记忆中加载指定概念的事实并注入到 LLM 上下文
 * - loadAllFactsToAgent: 从记忆中加载所有事实并注入到 LLM 上下文
 * - save/load/loadAll/loadByDescription: 直接操作记忆存储
 *
 * @param provider 记忆存储提供者
 * @param scopesProfile 作用域配置文件，维护 MemoryScopeType → 名称 的映射
 */
class AgentMemory(
    val provider: MemoryProvider,
    val scopesProfile: MemoryScopesProfile = MemoryScopesProfile()
) {
    /**
     * Extracts and saves facts from the LLM chat history based on the provided concept.
     *
     * @param session 当前 LLM 会话
     * @param concept 要提取的概念
     * @param subject 记忆主题
     * @param scope 记忆作用域
     * @param retrievalModel 用于事实提取的模型名称（可选）
     * @param retrievalClient 用于事实提取的 ChatClient（可选）
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
     * Loads facts about a specific concept from memory and adds them to the LLM chat history.
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
     * Loads all available facts from memory and adds them to the LLM chat history.
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
     * Implementation method for loading facts from memory and adding them to the LLM chat history.
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
}


// ========== 事实提取 ==========

/**
 * Extracts facts about a specific concept from the LLM chat history.
 * 完整移植 koog 的 retrieveFactsFromHistory
 *
 * 流程：
 * 1. 保存原始 prompt
 * 2. 删除末尾工具调用
 * 3. 把历史消息包装成 XML
 * 4. 用提取提示词重写 prompt
 * 5. 请求 LLM（不带工具）
 * 6. 解析回复为 Fact
 * 7. 恢复原始 prompt
 */
suspend fun retrieveFactsFromHistory(
    session: LLMSession,
    concept: Concept
): Fact {
    val promptForCompression = when (concept.factType) {
        FactType.SINGLE -> MemoryPrompts.singleFactPrompt(concept)
        FactType.MULTIPLE -> MemoryPrompts.multipleFactsPrompt(concept)
    }

    // remove trailing tool calls as we didn't provide any result for them
    session.dropTrailingToolCalls()

    val oldPrompt = session.prompt

    session.rewritePrompt {
        // Combine all history into one message with XML tags
        // to prevent LLM from continuing answering in a tool_call -> tool_result pattern
        val combinedMessage = buildString {
            append("<${MemoryPrompts.historyWrapperTag}>\n")
            oldPrompt.messages.forEach { message ->
                when {
                    message.role == "system" -> append("<system>\n${message.content}\n</system>\n")
                    message.role == "user" -> append("<user>\n${message.content}\n</user>\n")
                    message.role == "assistant" && message.toolCalls != null -> {
                        if (message.content.isNotBlank()) {
                            append("<assistant>\n${message.content}\n</assistant>\n")
                        }
                        val calls = message.toolCalls ?: emptyList()
                        calls.forEach { call ->
                            append("<tool_call tool=${call.name}>\n${call.arguments}\n</tool_call>\n")
                        }
                    }
                    message.role == "assistant" -> append("<assistant>\n${message.content}\n</assistant>\n")
                    message.role == "tool" -> append("<tool_result tool=${message.toolName}>\n${message.content}\n</tool_result>\n")
                }
            }
            append("</${MemoryPrompts.historyWrapperTag}>\n")
        }

        // Put compression prompt as a System instruction
        com.lhzkml.jasmine.core.prompt.model.Prompt.build(oldPrompt.id) {
            system(promptForCompression)
            user(combinedMessage)
        }
    }

    val timestamp = System.currentTimeMillis()

    val result = session.requestLLMWithoutTools()
    val responseText = result.content.trim()

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

    // Restore the original prompt
    session.rewritePrompt { oldPrompt }

    return fact
}

// ========== 自动检测事实（移植自 koog MemoryNodes） ==========

@Serializable
internal data class SubjectWithFact(
    val subject: MemorySubject,
    val keyword: String,
    val description: String,
    val value: String
)

/**
 * Parsing facts from response.
 * 完整移植 koog 的 parseFactsFromResponse（MemoryNodes.kt）
 *
 * 只支持 JSON 格式（与 koog 一致）。
 */
fun parseFactsFromResponse(content: String): List<Pair<MemorySubject, Fact>> {
    val timestamp = System.currentTimeMillis()
    val parsedFacts = Json.decodeFromString<List<SubjectWithFact>>(content)
    val groupedFacts = parsedFacts.groupBy { it.subject to it.keyword }

    return groupedFacts.map { (subjectWithKeyword, facts) ->
        when (facts.size) {
            1 -> {
                val singleFact = facts.single()
                subjectWithKeyword.first to SingleFact(
                    concept = Concept(
                        keyword = singleFact.keyword,
                        description = singleFact.description,
                        factType = FactType.SINGLE
                    ),
                    value = singleFact.value,
                    timestamp = timestamp
                )
            }
            else -> {
                subjectWithKeyword.first to MultipleFacts(
                    concept = Concept(
                        keyword = subjectWithKeyword.second,
                        description = facts.first().description,
                        factType = FactType.MULTIPLE
                    ),
                    values = facts.map { it.value },
                    timestamp = timestamp
                )
            }
        }
    }
}

/**
 * 自动检测并保存对话中的事实
 * 完整移植 koog 的 nodeSaveToMemoryAutoDetectFacts 逻辑
 *
 * @param session 当前 LLM 会话
 * @param memory AgentMemory 实例
 * @param scopes 要保存到的作用域类型列表
 * @param subjects 要检测的主题列表
 * @param retrievalModel 用于事实提取的模型名称（可选）
 * @param retrievalClient 用于事实提取的 ChatClient（可选）
 */
suspend fun saveToMemoryAutoDetectFacts(
    session: LLMSession,
    memory: AgentMemory,
    scopes: List<MemoryScopeType> = listOf(MemoryScopeType.AGENT),
    subjects: List<MemorySubject> = MemorySubject.registeredSubjects,
    retrievalModel: String? = null,
    retrievalClient: ChatClient? = null
) {
    val initialPrompt = session.prompt.copy()

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
        user(MemoryPrompts.autoDetectFacts(subjects))
    }

    val response = workSession.requestLLMWithoutTools()

    scopes.mapNotNull(memory.scopesProfile::getScope).forEach { scope ->
        val facts = parseFactsFromResponse(response.content)
        facts.forEach { (subject, fact) ->
            memory.provider.save(fact, subject, scope)
        }
    }

    // Revert the prompt to the original one
    session.rewritePrompt { initialPrompt }
}

// ========== 内部辅助 ==========

/**
 * 把消息列表包装成 XML 格式
 * 用于 buildHistoryXml 的独立辅助函数（供测试使用）
 */
internal fun buildHistoryXml(
    messages: List<ChatMessage>
): String {
    val tag = MemoryPrompts.historyWrapperTag
    return buildString {
        appendLine("<$tag>")
        messages.forEach { msg ->
            when {
                msg.role == "system" -> appendLine("<system>\n${msg.content}\n</system>")
                msg.role == "user" -> appendLine("<user>\n${msg.content}\n</user>")
                msg.role == "assistant" && msg.toolCalls != null -> {
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

/** Utility function to shorten a string for display purposes. */
private fun String.shortened() = lines().first().take(100) + "..."
