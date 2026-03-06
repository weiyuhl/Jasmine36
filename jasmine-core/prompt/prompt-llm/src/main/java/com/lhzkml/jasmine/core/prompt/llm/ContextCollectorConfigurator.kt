package com.lhzkml.jasmine.core.prompt.llm

/**
 * 上下文收集器配置器
 *
 * 根据配置参数自动配置 SystemContextCollector，
 * 将 Provider 注册逻辑从应用层提取到框架层，方便测试和复用。
 */
class ContextCollectorConfigurator {

    /**
     * 配置参数，由应用层从 ConfigRepository/ProviderManager 读取后传入。
     */
    data class Config(
        val isAgentMode: Boolean = false,
        val agentName: String = "Jasmine",
        val workspacePath: String = "",
        val personalRules: String = "",
        val projectRules: String = ""
    )

    /**
     * 根据配置参数配置收集器。
     * 会先清空再重新注册所有 Provider。
     */
    fun configure(collector: SystemContextCollector, config: Config) {
        collector.clear()

        if (config.isAgentMode) {
            collector.register(AgentPromptContextProvider(
                agentName = config.agentName,
                workspacePath = config.workspacePath
            ))
        }

        if (config.isAgentMode && config.workspacePath.isNotEmpty()) {
            collector.register(WorkspaceContextProvider(config.workspacePath))
        }

        if (config.personalRules.isNotBlank()) {
            collector.register(PersonalRulesContextProvider(config.personalRules))
        }

        if (config.workspacePath.isNotEmpty() && config.projectRules.isNotBlank()) {
            collector.register(ProjectRulesContextProvider(config.projectRules))
        }

        collector.register(SystemInfoContextProvider())
        collector.register(CurrentTimeContextProvider())
    }
}
