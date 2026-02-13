package com.lhzkml.jasmine.core.prompt.llm

import org.junit.Assert.*
import org.junit.Test

class SystemPromptManagerTest {

    @Test
    fun `default prompt is used when no custom prompt`() {
        val manager = SystemPromptManager()
        assertEquals("You are a helpful assistant.", manager.resolvePrompt())
        assertEquals("You are a helpful assistant.", manager.resolvePrompt(null))
        assertEquals("You are a helpful assistant.", manager.resolvePrompt(""))
        assertEquals("You are a helpful assistant.", manager.resolvePrompt("  "))
    }

    @Test
    fun `custom prompt overrides default`() {
        val manager = SystemPromptManager()
        assertEquals("自定义提示", manager.resolvePrompt("自定义提示"))
    }

    @Test
    fun `createSystemMessage uses correct role and content`() {
        val manager = SystemPromptManager()
        val msg = manager.createSystemMessage()
        assertEquals("system", msg.role)
        assertEquals("You are a helpful assistant.", msg.content)

        val custom = manager.createSystemMessage("你是翻译")
        assertEquals("system", custom.role)
        assertEquals("你是翻译", custom.content)
    }

    @Test
    fun `custom default prompt is respected`() {
        val manager = SystemPromptManager(defaultPrompt = "自定义默认")
        assertEquals("自定义默认", manager.resolvePrompt())
        assertEquals("自定义默认", manager.createSystemMessage().content)
    }

    @Test
    fun `defaultPrompt can be changed at runtime`() {
        val manager = SystemPromptManager()
        manager.defaultPrompt = "新的默认"
        assertEquals("新的默认", manager.resolvePrompt())
    }

    @Test
    fun `presets are available and non-empty`() {
        assertTrue(SystemPromptManager.presets.isNotEmpty())
        val default = SystemPromptManager.presets.find { it.id == "default" }
        assertNotNull(default)
        assertEquals(SystemPromptManager.DEFAULT_PROMPT, default!!.prompt)
    }
}
