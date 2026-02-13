package com.lhzkml.jasmine.core.prompt.llm

import org.junit.Assert.*
import org.junit.Test

class LLMProviderTest {

    @Test
    fun `DeepSeek provider name`() {
        assertEquals("DeepSeek", LLMProvider.DeepSeek.name)
    }

    @Test
    fun `SiliconFlow provider name`() {
        assertEquals("SiliconFlow", LLMProvider.SiliconFlow.name)
    }

    @Test
    fun `providers are distinct`() {
        assertNotEquals(LLMProvider.DeepSeek, LLMProvider.SiliconFlow)
    }
}
