package com.lhzkml.jasmine.core.prompt.llm

/**
 * 系统上下文提供者接口
 *
 * 类似 IDE（如 Kiro）在每次发送消息前自动收集环境信息并注入到 system prompt 中，
 * Jasmine 通过 SystemContextProvider 机制实现同样的自动拼接功能。
 *
 * 每个 Provider 负责提供一段上下文信息（如工作区路径、系统信息、当前时间等），
 * 由 SystemContextCollector 统一收集并拼接到 system prompt 末尾。
 */
interface SystemContextProvider {
    /** 上下文段落的标识名，用于去重和调试 */
    val name: String

    /**
     * 返回要注入到 system prompt 的上下文内容。
     * 返回 null 表示当前条件下不需要注入。
     */
    fun getContextSection(): String?
}

/**
 * 系统上下文收集器
 *
 * 统一管理所有 SystemContextProvider，在构建 system prompt 时自动收集并拼接。
 * 调用方只需注册 Provider，不需要手动拼接字符串。
 */
class SystemContextCollector {
    private val providers = mutableListOf<SystemContextProvider>()

    /** 注册一个上下文提供者 */
    fun register(provider: SystemContextProvider) {
        // 去重：同名 provider 只保留最新的
        providers.removeAll { it.name == provider.name }
        providers.add(provider)
    }

    /** 移除指定名称的提供者 */
    fun unregister(name: String) {
        providers.removeAll { it.name == name }
    }

    /** 清空所有提供者 */
    fun clear() {
        providers.clear()
    }

    /**
     * 收集所有上下文并拼接到基础 system prompt 后面。
     * 如果没有任何上下文，返回原始 prompt 不变。
     */
    fun buildSystemPrompt(basePrompt: String): String {
        val sections = providers.mapNotNull { it.getContextSection() }
        if (sections.isEmpty()) return basePrompt
        return basePrompt + "\n\n" + sections.joinToString("\n\n")
    }

    /** 当前注册的 provider 数量 */
    val size: Int get() = providers.size
}

// ========== 内置 Provider 实现 ==========

/**
 * 工作区上下文 — 注入当前工作区路径和文件工具使用说明
 */
class WorkspaceContextProvider(private val workspacePath: String) : SystemContextProvider {
    override val name = "workspace"
    override fun getContextSection(): String {
        if (workspacePath.isBlank()) return ""
        return "<workspace>\n" +
            "当前工作区路径: $workspacePath\n" +
            "你可以使用文件工具（read_file, write_file, edit_file, list_directory, search_by_regex, execute_shell_command）来操作该工作区内的文件。\n" +
            "所有路径使用相对路径即可（相对于工作区根目录），例如用 \".\" 列出根目录，用 \"file.txt\" 读取文件。也支持绝对路径。\n" +
            "</workspace>"
    }
}

/**
 * 系统信息上下文 — 注入设备和系统信息
 */
class SystemInfoContextProvider : SystemContextProvider {
    override val name = "system_info"
    override fun getContextSection(): String {
        val os = System.getProperty("os.name") ?: "Unknown"
        val arch = System.getProperty("os.arch") ?: "Unknown"
        val sdkInt = try {
            android.os.Build.VERSION.SDK_INT
        } catch (_: Exception) { -1 }
        val device = try {
            "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        } catch (_: Exception) { "Unknown" }
        return "<system_information>\n" +
            "OS: Android $sdkInt ($os $arch)\n" +
            "设备: $device\n" +
            "</system_information>"
    }
}

/**
 * 当前时间上下文 — 注入当前日期和时间
 */
class CurrentTimeContextProvider : SystemContextProvider {
    override val name = "current_time"
    override fun getContextSection(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm (EEEE)", java.util.Locale.getDefault())
        return "<current_date_and_time>\n${sdf.format(java.util.Date())}\n</current_date_and_time>"
    }
}

/**
 * 可用工具列表上下文 — 注入当前启用的工具名称
 */
class AvailableToolsContextProvider(private val toolNames: List<String>) : SystemContextProvider {
    override val name = "available_tools"
    override fun getContextSection(): String {
        if (toolNames.isEmpty()) return ""
        return "<available_tools>\n${toolNames.joinToString(", ")}\n</available_tools>"
    }
}

/**
 * Agent 模式行为指引 — 注入结构化的 Agent 提示词
 *
 * 参考 IDE Agent 的提示词结构，为 LLM 提供身份、能力、规则、工具使用指南等。
 * 只在 Agent 模式下注入。
 */
class AgentPromptContextProvider(
    private val agentName: String = "Jasmine",
    private val workspacePath: String = ""
) : SystemContextProvider {
    override val name = "agent_prompt"
    override fun getContextSection(): String = buildString {
        appendLine("<identity>")
        appendLine("你是 $agentName，一个运行在 Android 设备上的 AI Agent 助手。")
        appendLine("你可以通过工具来读写文件、执行命令、搜索内容，帮助用户完成编程和文件管理任务。")
        appendLine("</identity>")
        appendLine()
        appendLine("<capabilities>")
        appendLine("- 读取、创建、编辑文件")
        appendLine("- 列出目录内容，支持深度遍历和 glob 过滤")
        appendLine("- 正则搜索文件内容")
        appendLine("- 执行 shell 命令")
        appendLine("- 网络搜索和网页抓取（如果已启用）")
        appendLine("- 数学计算")
        appendLine("- 获取当前时间")
        appendLine("</capabilities>")
        appendLine()
        appendLine("<rules>")
        appendLine("- 使用工具时，路径参数使用相对路径（相对于工作区根目录）")
        appendLine("- 用 \".\" 表示工作区根目录")
        appendLine("- 不要猜测文件内容，先用 read_file 或 list_directory 查看")
        appendLine("- 执行操作前先确认目标文件/目录存在")
        appendLine("- 一次工具调用失败时，分析错误原因，尝试换一种方式")
        appendLine("- 回复用户时使用简洁清晰的语言")
        appendLine("- 如果用户使用中文提问，用中文回复")
        appendLine("</rules>")
        appendLine()
        appendLine("<tool_usage_guide>")
        appendLine("- list_directory: path 参数用 \".\" 列出根目录，depth 控制深度，filter 用 glob 模式如 \"*.kt\"")
        appendLine("- read_file: path 参数直接用文件名如 \"README.md\"，支持 startLine/endLine 行范围")
        appendLine("- write_file: 写入或覆盖文件，path + content 参数")
        appendLine("- edit_file: 精确替换文件内容，需要 path + oldContent + newContent")
        appendLine("- search_by_regex: 正则搜索，pattern 参数为正则表达式，path 为搜索目录")
        appendLine("- execute_shell_command: 执行 shell 命令，需要用户确认")
        appendLine("</tool_usage_guide>")
    }.trimEnd()
}

/**
 * 自定义上下文 — 用于注入任意自定义信息
 */
class CustomContextProvider(
    override val name: String,
    private val content: String
) : SystemContextProvider {
    override fun getContextSection(): String? {
        return content.ifBlank { null }
    }
}
