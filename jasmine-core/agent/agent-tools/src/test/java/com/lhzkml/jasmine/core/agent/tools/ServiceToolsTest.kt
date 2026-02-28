package com.lhzkml.jasmine.core.agent.tools

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ServiceToolsTest {

    @Test
    fun `AskUser returns user response`() = runBlocking {
        val tool = AskUserTool { "user reply" }
        assertEquals("user reply", tool.execute("""{"message": "question?"}"""))
    }

    @Test
    fun `AskUser descriptor`() {
        assertEquals("ask_user", AskUserTool { "" }.name)
    }

    @Test
    fun `SingleSelect returns selected option`() = runBlocking {
        val tool = SingleSelectTool { _, _ -> "Option A" }
        val result = tool.execute("""{"question": "Choose one", "options": ["Option A", "Option B"]}""")
        assertEquals("Option A", result)
    }

    @Test
    fun `SingleSelect descriptor`() {
        assertEquals("single_select", SingleSelectTool { _, _ -> "" }.name)
    }

    @Test
    fun `MultiSelect returns selected options`() = runBlocking {
        val tool = MultiSelectTool { _, _ -> listOf("A", "B") }
        val result = tool.execute("""{"question": "Choose multiple", "options": ["A", "B", "C"]}""")
        assertEquals("A, B", result)
    }

    @Test
    fun `MultiSelect descriptor`() {
        assertEquals("multi_select", MultiSelectTool { _, _ -> emptyList() }.name)
    }

    @Test
    fun `RankPriorities returns ranked list`() = runBlocking {
        val tool = RankPrioritiesTool { _, items -> items.reversed() }
        val result = tool.execute("""{"question": "Rank these", "items": ["Low", "Medium", "High"]}""")
        assertTrue(result.contains("1. High"))
        assertTrue(result.contains("2. Medium"))
        assertTrue(result.contains("3. Low"))
    }

    @Test
    fun `RankPriorities descriptor`() {
        assertEquals("rank_priorities", RankPrioritiesTool { _, items -> items }.name)
    }

    @Test
    fun `AskMultipleQuestions returns all answers`() = runBlocking {
        val tool = AskMultipleQuestionsTool { listOf("Answer 1", "Answer 2") }
        val result = tool.execute("""{"questions": ["Q1?", "Q2?"]}""")
        assertTrue(result.contains("Q1: Q1?"))
        assertTrue(result.contains("A1: Answer 1"))
        assertTrue(result.contains("Q2: Q2?"))
        assertTrue(result.contains("A2: Answer 2"))
    }

    @Test
    fun `AskMultipleQuestions descriptor`() {
        assertEquals("ask_multiple_questions", AskMultipleQuestionsTool { emptyList() }.name)
    }
}
