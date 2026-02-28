package com.lhzkml.jasmine.core.agent.runtime

import com.lhzkml.jasmine.core.agent.tools.*
import com.lhzkml.jasmine.core.config.ConfigRepository

/**
 * 工具注册表构建器
 *
 * 根据 ConfigRepository 中的配置，构建 ToolRegistry。
 * 将 MainActivity.buildToolRegistry() 中的业务逻辑迁移到 core 层。
 *
 * 平台相关的回调（如 Shell 命令确认对话框）通过参数传入，
 * core 层不依赖 Android UI。
 */
class ToolRegistryBuilder(private val configRepo: ConfigRepository) {

    /**
     * Shell 命令确认处理器
     * 由 app 层提供实现（如弹出 AlertDialog）
     */
    var shellConfirmationHandler: (suspend (command: String, workingDir: String?) -> Boolean)? = null

    /**
     * 工作区路径（Agent 模式下由用户选择）
     * 为空时使用 fallbackBasePath
     */
    var workspacePath: String = ""

    /**
     * 回退基础路径（非 Agent 模式下使用，如 APP 沙箱路径）
     */
    var fallbackBasePath: String? = null

    /**
     * BrightData API Key（网络搜索工具需要）
     */
    var brightDataKey: String = ""

    /**
     * 构建工具注册表
     *
     * @param isAgentMode 是否为 Agent 模式
     * @return 构建好的 ToolRegistry
     */
    fun build(isAgentMode: Boolean): ToolRegistry {
        val enabledTools = if (isAgentMode) {
            configRepo.getAgentToolPreset()
        } else {
            configRepo.getEnabledTools()
        }
        fun isEnabled(name: String) = enabledTools.isEmpty() || name in enabledTools

        val basePath = if (isAgentMode && workspacePath.isNotEmpty()) {
            workspacePath
        } else {
            fallbackBasePath
        }

        return ToolRegistry.build {
            // 计算器
            if (isEnabled("calculator")) {
                CalculatorTool.allTools().forEach { register(it) }
            }

            // 获取当前时间
            if (isEnabled("get_current_time")) {
                register(GetCurrentTimeTool)
            }

            // 文件工具
            if (isEnabled("file_tools")) {
                register(ReadFileTool(basePath))
                register(WriteFileTool(basePath))
                register(EditFileTool(basePath))
                register(ListDirectoryTool(basePath))
                register(RegexSearchTool(basePath))
                register(FindFilesTool(basePath))
                register(DeleteFileTool(basePath))
                register(MoveFileTool(basePath))
                register(CopyFileTool(basePath))
                register(AppendFileTool(basePath))
                register(FileInfoTool(basePath))
                register(CreateDirectoryTool(basePath))
                register(CompressFilesTool(basePath))
                register(InsertContentTool(basePath))
                register(RenameFileTool(basePath))
                register(ReplaceInFileTool(basePath))
                register(CreateFileTool(basePath))
            }

            // Shell 命令
            if (isEnabled("execute_shell_command")) {
                val shellPolicy = configRepo.getShellPolicy()
                val blacklist = configRepo.getShellBlacklist()
                val whitelist = configRepo.getShellWhitelist()

                val policyConfig = ShellPolicyConfig(
                    policy = shellPolicy,
                    blacklist = blacklist,
                    whitelist = whitelist
                )

                register(ExecuteShellCommandTool(
                    confirmationHandler = shellConfirmationHandler ?: { _, _ -> true },
                    policyConfig = policyConfig,
                    basePath = basePath
                ))
            }

            // 网络搜索（BrightData API）
            if (isEnabled("web_search")) {
                val key = brightDataKey.ifEmpty { configRepo.getBrightDataKey() }
                if (key.isNotEmpty()) {
                    val wst = WebSearchTool(key)
                    register(wst.search)
                    register(wst.scrape)
                }
            }

            // URL 抓取
            if (isEnabled("fetch_url")) {
                val ft = FetchUrlTool()
                register(ft.fetchHtml)
                register(ft.fetchText)
                register(ft.fetchJson)
            }

            // Agent 显式完成工具
            if (isEnabled("attempt_completion")) register(AttemptCompletionTool)
        }
    }
}
