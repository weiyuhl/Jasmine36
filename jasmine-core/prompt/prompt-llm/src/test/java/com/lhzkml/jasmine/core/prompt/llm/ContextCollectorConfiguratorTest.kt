package com.lhzkml.jasmine.core.prompt.llm

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ContextCollectorConfiguratorTest {

    private lateinit var collector: SystemContextCollector
    private lateinit var configurator: ContextCollectorConfigurator

    @Before
    fun setup() {
        collector = SystemContextCollector()
        configurator = ContextCollectorConfigurator()
    }

    @Test
    fun `test chat mode only registers system info and time`() {
        val config = ContextCollectorConfigurator.Config(isAgentMode = false)
        configurator.configure(collector, config)

        // system_info + current_time = 2
        assertEquals(2, collector.size)

        val prompt = collector.buildSystemPrompt("Hello")
        assertTrue(prompt.contains("Hello"))
        assertTrue(prompt.contains("<system_information>"))
        assertTrue(prompt.contains("<current_date_and_time>"))
        assertFalse(prompt.contains("<identity>"))
        assertFalse(prompt.contains("<workspace>"))
    }

    @Test
    fun `test agent mode registers agent prompt and workspace`() {
        val config = ContextCollectorConfigurator.Config(
            isAgentMode = true,
            workspacePath = "/test/project"
        )
        configurator.configure(collector, config)

        // agent_prompt + workspace + system_info + current_time = 4
        assertEquals(4, collector.size)

        val prompt = collector.buildSystemPrompt("Base")
        assertTrue(prompt.contains("<identity>"))
        assertTrue(prompt.contains("Jasmine"))
        assertTrue(prompt.contains("<workspace>"))
        assertTrue(prompt.contains("/test/project"))
    }

    @Test
    fun `test agent mode without workspace skips workspace provider`() {
        val config = ContextCollectorConfigurator.Config(
            isAgentMode = true,
            workspacePath = ""
        )
        configurator.configure(collector, config)

        // agent_prompt + system_info + current_time = 3 (no workspace)
        assertEquals(3, collector.size)

        val prompt = collector.buildSystemPrompt("Base")
        assertTrue(prompt.contains("<identity>"))
        assertFalse(prompt.contains("<workspace>"))
    }

    @Test
    fun `test personal rules injected in both modes`() {
        val config = ContextCollectorConfigurator.Config(
            isAgentMode = false,
            personalRules = "Always respond in Chinese"
        )
        configurator.configure(collector, config)

        // personal_rules + system_info + current_time = 3
        assertEquals(3, collector.size)

        val prompt = collector.buildSystemPrompt("Base")
        assertTrue(prompt.contains("<user_rules"))
        assertTrue(prompt.contains("Always respond in Chinese"))
    }

    @Test
    fun `test blank personal rules not injected`() {
        val config = ContextCollectorConfigurator.Config(
            isAgentMode = false,
            personalRules = "   "
        )
        configurator.configure(collector, config)

        assertEquals(2, collector.size)
    }

    @Test
    fun `test project rules require workspace path`() {
        val config = ContextCollectorConfigurator.Config(
            isAgentMode = false,
            workspacePath = "",
            projectRules = "Use Kotlin"
        )
        configurator.configure(collector, config)

        // project rules skipped because workspacePath is empty
        assertEquals(2, collector.size)
        val prompt = collector.buildSystemPrompt("Base")
        assertFalse(prompt.contains("<project_rules"))
    }

    @Test
    fun `test project rules injected when workspace present`() {
        val config = ContextCollectorConfigurator.Config(
            isAgentMode = false,
            workspacePath = "/test/project",
            projectRules = "Use MVVM\nPrefer Coroutines"
        )
        configurator.configure(collector, config)

        // project_rules + system_info + current_time = 3
        assertEquals(3, collector.size)

        val prompt = collector.buildSystemPrompt("Base")
        assertTrue(prompt.contains("<project_rules"))
        assertTrue(prompt.contains("Use MVVM"))
        assertTrue(prompt.contains("Prefer Coroutines"))
    }

    @Test
    fun `test full agent mode with all rules`() {
        val config = ContextCollectorConfigurator.Config(
            isAgentMode = true,
            agentName = "TestBot",
            workspacePath = "/home/user/project",
            personalRules = "Respond in Chinese",
            projectRules = "Use Kotlin only"
        )
        configurator.configure(collector, config)

        // agent_prompt + workspace + personal_rules + project_rules + system_info + current_time = 6
        assertEquals(6, collector.size)

        val prompt = collector.buildSystemPrompt("Base")
        assertTrue(prompt.contains("TestBot"))
        assertTrue(prompt.contains("/home/user/project"))
        assertTrue(prompt.contains("Respond in Chinese"))
        assertTrue(prompt.contains("Use Kotlin only"))
        assertTrue(prompt.contains("<identity>"))
        assertTrue(prompt.contains("<workspace>"))
        assertTrue(prompt.contains("<user_rules"))
        assertTrue(prompt.contains("<project_rules"))
        assertTrue(prompt.contains("<system_information>"))
        assertTrue(prompt.contains("<current_date_and_time>"))
    }

    @Test
    fun `test configure clears previous providers`() {
        val config1 = ContextCollectorConfigurator.Config(
            isAgentMode = true,
            workspacePath = "/project1",
            personalRules = "Rule1"
        )
        configurator.configure(collector, config1)
        assertEquals(5, collector.size)

        val config2 = ContextCollectorConfigurator.Config(isAgentMode = false)
        configurator.configure(collector, config2)
        assertEquals(2, collector.size)

        val prompt = collector.buildSystemPrompt("Base")
        assertFalse(prompt.contains("<identity>"))
        assertFalse(prompt.contains("Rule1"))
    }

    @Test
    fun `test custom agent name`() {
        val config = ContextCollectorConfigurator.Config(
            isAgentMode = true,
            agentName = "MyAssistant"
        )
        configurator.configure(collector, config)

        val prompt = collector.buildSystemPrompt("Base")
        assertTrue(prompt.contains("MyAssistant"))
        assertFalse(prompt.contains("Jasmine"))
    }
}
