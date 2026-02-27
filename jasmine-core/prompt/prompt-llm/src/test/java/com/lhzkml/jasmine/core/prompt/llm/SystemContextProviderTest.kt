package com.lhzkml.jasmine.core.prompt.llm

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SystemContextProviderTest {

    private lateinit var collector: SystemContextCollector

    @Before
    fun setup() {
        collector = SystemContextCollector()
    }

    @Test
    fun `test SystemContextCollector register and build`() {
        val provider = CustomContextProvider("test", "Test content")
        collector.register(provider)
        
        assertEquals(1, collector.size)
        
        val result = collector.buildSystemPrompt("Base prompt")
        assertTrue(result.contains("Base prompt"))
        assertTrue(result.contains("Test content"))
    }

    @Test
    fun `test SystemContextCollector with empty base prompt`() {
        val provider = CustomContextProvider("test", "Test content")
        collector.register(provider)
        
        val result = collector.buildSystemPrompt("")
        assertTrue(result.contains("Test content"))
    }

    @Test
    fun `test SystemContextCollector with null content provider`() {
        val provider = CustomContextProvider("test", "")
        collector.register(provider)
        
        val result = collector.buildSystemPrompt("Base prompt")
        assertEquals("Base prompt", result)
    }

    @Test
    fun `test SystemContextCollector duplicate name replacement`() {
        collector.register(CustomContextProvider("test", "First"))
        collector.register(CustomContextProvider("test", "Second"))
        
        assertEquals(1, collector.size)
        
        val result = collector.buildSystemPrompt("Base")
        assertTrue(result.contains("Second"))
        assertFalse(result.contains("First"))
    }

    @Test
    fun `test SystemContextCollector unregister`() {
        collector.register(CustomContextProvider("test1", "Content1"))
        collector.register(CustomContextProvider("test2", "Content2"))
        
        assertEquals(2, collector.size)
        
        collector.unregister("test1")
        assertEquals(1, collector.size)
        
        val result = collector.buildSystemPrompt("Base")
        assertFalse(result.contains("Content1"))
        assertTrue(result.contains("Content2"))
    }

    @Test
    fun `test SystemContextCollector clear`() {
        collector.register(CustomContextProvider("test1", "Content1"))
        collector.register(CustomContextProvider("test2", "Content2"))
        
        collector.clear()
        assertEquals(0, collector.size)
        
        val result = collector.buildSystemPrompt("Base")
        assertEquals("Base", result)
    }

    @Test
    fun `test SystemContextCollector multiple providers`() {
        collector.register(CustomContextProvider("test1", "Content1"))
        collector.register(CustomContextProvider("test2", "Content2"))
        collector.register(CustomContextProvider("test3", "Content3"))
        
        val result = collector.buildSystemPrompt("Base")
        assertTrue(result.contains("Base"))
        assertTrue(result.contains("Content1"))
        assertTrue(result.contains("Content2"))
        assertTrue(result.contains("Content3"))
    }

    @Test
    fun `test WorkspaceContextProvider with valid path`() {
        val provider = WorkspaceContextProvider("/test/workspace")
        
        assertEquals("workspace", provider.name)
        
        val content = provider.getContextSection()
        assertNotNull(content)
        assertTrue(content!!.contains("/test/workspace"))
        assertTrue(content.contains("<workspace>"))
        assertTrue(content.contains("</workspace>"))
    }

    @Test
    fun `test WorkspaceContextProvider with blank path`() {
        val provider = WorkspaceContextProvider("")
        val content = provider.getContextSection()
        assertEquals("", content)
    }

    @Test
    fun `test CurrentTimeContextProvider`() {
        val provider = CurrentTimeContextProvider()
        
        assertEquals("current_time", provider.name)
        
        val content = provider.getContextSection()
        assertNotNull(content)
        assertTrue(content!!.contains("<current_date_and_time>"))
        assertTrue(content.contains("</current_date_and_time>"))
    }

    @Test
    fun `test AgentPromptContextProvider`() {
        val provider = AgentPromptContextProvider("TestAgent", "/workspace")
        
        assertEquals("agent_prompt", provider.name)
        
        val content = provider.getContextSection()
        assertNotNull(content)
        assertTrue(content!!.contains("TestAgent"))
        assertTrue(content.contains("<identity>"))
        assertTrue(content.contains("</identity>"))
        assertTrue(content.contains("<rules>"))
        assertTrue(content.contains("</rules>"))
    }

    @Test
    fun `test CustomContextProvider with blank content`() {
        val provider = CustomContextProvider("test", "   ")
        val content = provider.getContextSection()
        assertNull(content)
    }

    @Test
    fun `test integration scenario`() {
        // 模拟真实使用场景
        collector.register(WorkspaceContextProvider("/home/user/project"))
        collector.register(CurrentTimeContextProvider())
        collector.register(AgentPromptContextProvider("Jasmine", "/home/user/project"))
        
        val basePrompt = "You are an AI assistant."
        val fullPrompt = collector.buildSystemPrompt(basePrompt)
        
        assertTrue(fullPrompt.contains("You are an AI assistant."))
        assertTrue(fullPrompt.contains("/home/user/project"))
        assertTrue(fullPrompt.contains("Jasmine"))
        assertTrue(fullPrompt.contains("<workspace>"))
        assertTrue(fullPrompt.contains("<current_date_and_time>"))
        assertTrue(fullPrompt.contains("<identity>"))
    }
}
