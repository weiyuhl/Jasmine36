package com.lhzkml.jasmine

import org.junit.Assert.*
import org.junit.Test

class ProviderManagerTest {

    @Test
    fun `providers list is not empty`() {
        assertTrue(ProviderManager.providers.isNotEmpty())
    }

    @Test
    fun `deepseek provider exists with correct defaults`() {
        val provider = ProviderManager.providers.find { it.id == "deepseek" }
        assertNotNull(provider)
        assertEquals("DeepSeek", provider!!.name)
        assertEquals("https://api.deepseek.com", provider.defaultBaseUrl)
        assertEquals("deepseek-chat", provider.defaultModel)
    }

    @Test
    fun `siliconflow provider exists with correct defaults`() {
        val provider = ProviderManager.providers.find { it.id == "siliconflow" }
        assertNotNull(provider)
        assertEquals("硅基流动", provider!!.name)
        assertEquals("https://api.siliconflow.cn", provider.defaultBaseUrl)
        assertEquals("deepseek-ai/DeepSeek-V3", provider.defaultModel)
    }

    @Test
    fun `all providers have unique ids`() {
        val ids = ProviderManager.providers.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `all providers have non-empty fields`() {
        for (provider in ProviderManager.providers) {
            assertTrue("id should not be empty", provider.id.isNotEmpty())
            assertTrue("name should not be empty", provider.name.isNotEmpty())
            assertTrue("defaultBaseUrl should not be empty", provider.defaultBaseUrl.isNotEmpty())
            assertTrue("defaultModel should not be empty", provider.defaultModel.isNotEmpty())
        }
    }

    @Test
    fun `all provider base urls are valid https`() {
        for (provider in ProviderManager.providers) {
            assertTrue(
                "${provider.id} base url should start with https://",
                provider.defaultBaseUrl.startsWith("https://")
            )
        }
    }
}
