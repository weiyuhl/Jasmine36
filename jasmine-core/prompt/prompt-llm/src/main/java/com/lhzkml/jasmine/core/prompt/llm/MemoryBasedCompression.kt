package com.lhzkml.jasmine.core.prompt.llm

import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.Concept
import com.lhzkml.jasmine.core.prompt.model.MultipleFacts
import com.lhzkml.jasmine.core.prompt.model.SingleFact

/**
 * 基于记忆的历史压缩策略
 * 完整移植 koog 的 RetrieveFactsFromHistory
 *
 * 与普通 TLDR 压缩不同，这个策略：
 * 1. 从历史中提取指定概念的事实
 * 2. 用结构化的"上下文恢复"消息替换历史
 * 3. 保留最后一轮工具调用
 *
 * @param concepts 要从历史中提取的概念列表
 */
class RetrieveFactsFromHistory(
    val concepts: List<Concept>
) : HistoryCompressionStrategy() {

    constructor(vararg concepts: Concept) : this(concepts.toList())

    override suspend fun compress(
        session: LLMSession,
        memoryMessages: List<ChatMessage>
    ) {
        // 统计工具交互次数
        val iterationsCount = session.prompt.messages.count { it.role == "tool" }

        // 提取每个概念的事实
        val factsString = concepts.map { concept ->
            val fact = retrieveFactsFromHistory(session, concept)
            buildString {
                appendLine("## KNOWN FACTS ABOUT `${concept.keyword}` (${concept.description})")
                when (fact) {
                    is MultipleFacts -> fact.values.forEach { appendLine("- $it") }
                    is SingleFact -> appendLine("- ${fact.value}")
                }
                appendLine()
            }
        }.joinToString("\n")

        // 构建上下文恢复消息（与 koog 原始格式一致）
        val assistantMessage = """[CONTEXT RESTORATION INITIATED]

I was in the middle of working on the task when I needed to compress the conversation history due to context limits. Here's my understanding of where we are:

**Compressed Working Memory:**
<compressed_facts>
${factsString.trimIndent()}
</compressed_facts>

**Current Status:**
I've been actively working on this task through approximately $iterationsCount tool interactions.
The above summary represents the key findings, attempted approaches, and intermediate results from my work so far.

I'm ready to continue from this point. Let me quickly orient myself to the current state and proceed with the next logical step based on my previous findings."""

        val userMessage = """Yes, that's correct. Your memory compression accurately captures your progress. Please continue your work from where you left off.
For context, you still have access to all the same tools, and the task requirements remain unchanged. Focus on building upon what you've already discovered rather than re-exploring completed paths.
Continue with your analysis and implementation."""

        val oldMessages = session.prompt.messages

        // 保留最后一轮工具调用（如果有）
        val lastResult = oldMessages.lastOrNull { it.role == "tool" }
        val lastToolCallMessages = if (lastResult != null) {
            val lastToolCallId = lastResult.toolCallId
            val matchingAssistant = oldMessages.lastOrNull { msg ->
                msg.role == "assistant" && msg.toolCalls?.any { it.id == lastToolCallId } == true
            }
            if (matchingAssistant != null) listOf(matchingAssistant, lastResult) else emptyList()
        } else {
            emptyList()
        }

        // 构建新消息（上下文恢复 + 最后一轮工具调用）
        val newMessages = mutableListOf(
            ChatMessage.assistant(assistantMessage),
            ChatMessage.user(userMessage)
        )
        newMessages.addAll(lastToolCallMessages)

        val compressed = composeMessageHistory(oldMessages, newMessages, memoryMessages)
        session.rewritePrompt { it.withMessages { compressed } }
    }
}
