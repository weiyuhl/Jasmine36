package com.lhzkml.jasmine

import com.lhzkml.jasmine.core.config.ProviderConfig
import com.lhzkml.jasmine.core.config.ProviderRegistry
import com.lhzkml.jasmine.core.config.ConfigRepository
import com.lhzkml.jasmine.core.config.McpServerConfig
import com.lhzkml.jasmine.core.config.AgentStrategyType
import com.lhzkml.jasmine.core.config.GraphToolCallMode
import com.lhzkml.jasmine.core.config.ToolSelectionStrategyType
import com.lhzkml.jasmine.core.config.ToolChoiceMode
import com.lhzkml.jasmine.core.config.SnapshotStorageType
import com.lhzkml.jasmine.core.agent.tools.ShellPolicy
import com.lhzkml.jasmine.core.agent.tools.snapshot.RollbackStrategy
import com.lhzkml.jasmine.core.agent.tools.trace.TraceEventCategory
import com.lhzkml.jasmine.core.agent.tools.event.EventCategory
import com.lhzkml.jasmine.core.prompt.llm.CompressionStrategyType
import org.junit.Assert.*
import org.junit.Test

/**
 * ProviderRegistry 单元测试
 *
 * ProviderManager 依赖 Android Context 初始化，无法在纯 JVM 测试中使用。
 * 因此直接测试 ProviderRegistry（ProviderManager 的核心逻辑委托对象）。
 */
class ProviderManagerTest {

    private fun createRegistry(): ProviderRegistry {
        val repo = StubConfigRepository()
        val registry = ProviderRegistry(repo)
        registry.initialize()
        return registry
    }

    @Test
    fun `providers list is not empty`() {
        assertTrue(createRegistry().providers.isNotEmpty())
    }

    @Test
    fun `deepseek provider exists with correct defaults`() {
        val provider = createRegistry().providers.find { it.id == "deepseek" }
        assertNotNull(provider)
        assertEquals("DeepSeek", provider!!.name)
        assertEquals("https://api.deepseek.com", provider.defaultBaseUrl)
        assertEquals("", provider.defaultModel)
    }

    @Test
    fun `siliconflow provider exists with correct defaults`() {
        val provider = createRegistry().providers.find { it.id == "siliconflow" }
        assertNotNull(provider)
        assertEquals("硅基流动", provider!!.name)
        assertEquals("https://api.siliconflow.cn", provider.defaultBaseUrl)
        assertEquals("", provider.defaultModel)
    }

    @Test
    fun `all providers have unique ids`() {
        val ids = createRegistry().providers.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `all providers have non-empty fields`() {
        for (provider in createRegistry().providers) {
            assertTrue("id should not be empty", provider.id.isNotEmpty())
            assertTrue("name should not be empty", provider.name.isNotEmpty())
            assertTrue("defaultBaseUrl should not be empty", provider.defaultBaseUrl.isNotEmpty())
        }
    }

    @Test
    fun `all provider base urls are valid https`() {
        for (provider in createRegistry().providers) {
            assertTrue(
                "${provider.id} base url should start with https://",
                provider.defaultBaseUrl.startsWith("https://")
            )
        }
    }
}
