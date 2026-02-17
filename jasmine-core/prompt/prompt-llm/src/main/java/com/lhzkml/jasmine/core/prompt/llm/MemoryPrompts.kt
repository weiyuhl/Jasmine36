package com.lhzkml.jasmine.core.prompt.llm

import com.lhzkml.jasmine.core.prompt.model.Concept
import com.lhzkml.jasmine.core.prompt.model.MemorySubject

/**
 * 记忆系统提示词集合
 * 参考 koog 的 MemoryPrompts
 */
object MemoryPrompts {

    /**
     * 单值事实提取提示词
     */
    fun singleFactPrompt(concept: Concept, tag: String): String =
        """You are a specialized information extractor for compressing agent conversation histories.

You will receive a conversation history enclosed in <$tag> tags. Your task is to extract THE SINGLE MOST IMPORTANT fact about "${concept.keyword}" (${concept.description}).

Critical extraction rules:
1. Focus on THE MOST ESSENTIAL OUTCOME or ESTABLISHED INFORMATION
2. When you see tool calls/observations, extract only the most crucial discovered fact
3. The fact must be self-contained - assume it will be the only available context later
4. Choose the fact with the broadest impact on understanding this concept

Output constraints:
- Exactly one fact
- No explanations, formatting, or preamble
- Must be a complete, self-contained statement

Output only the fact."""

    /**
     * 多值事实提取提示词
     */
    fun multipleFactsPrompt(concept: Concept, tag: String): String =
        """You are a specialized information extractor for compressing agent conversation histories.

You will receive a conversation history enclosed in <$tag> tags. Your task is to extract ONLY the essential facts about "${concept.keyword}" (${concept.description}).

Critical extraction rules:
1. Focus on OUTCOMES and ESTABLISHED INFORMATION, not actions taken
2. When you see tool calls/observations, extract only the discovered facts, not the process
3. Each fact must be self-contained - assume it will be the only available context later
4. Combine related information into single, comprehensive facts when possible

Output constraints:
- One fact per line
- No explanations, headers, numbering, or formatting
- Facts must be complete statements that stand alone
- Skip any fact that just describes what was attempted or checked

Output only the facts, nothing else."""

    /**
     * 自动检测事实提示词
     * 参考 koog 的 autoDetectFacts
     */
    fun autoDetectFactsPrompt(subjects: List<MemorySubject>, tag: String): String =
        """Analyze the conversation history enclosed in <$tag> tags and identify important facts about:
${subjects.joinToString("\n") { "- [subject: \"${it.name}\"] ${it.promptDescription}" }}

For each fact:
1. Provide a relevant subject (USE SAME SUBJECTS AS DESCRIBED ABOVE!)
2. Provide a keyword (e.g., 'user-preference', 'project-requirement')
3. Write a description that helps identify similar information
4. Provide the actual fact value

Format your response as a JSON array:
[
    {
        "subject": "string",
        "keyword": "string",
        "description": "string",
        "value": "string"
    }
]"""
}
