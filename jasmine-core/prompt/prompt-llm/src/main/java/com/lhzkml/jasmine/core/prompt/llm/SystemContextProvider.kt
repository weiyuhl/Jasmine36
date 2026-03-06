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
            "你可以使用工具来操作该工作区内的文件、执行命令、搜索内容等。\n" +
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
 * Agent 模式行为指引 -- 注入结构化的 Agent 提示词
 *
 * 参考 IDE Agent 的提示词结构，为 LLM 提供身份、规则等。
 * 只在 Agent 模式下注入。
 *
 * 注意：工具列表不在系统提示词中列出，而是通过 API 的 tools 参数以结构化方式发送。
 * 在系统提示词中列出工具会导致某些模型（如 Kimi-K2）使用文本模式的 tool calling
 * 而非 API 的结构化 function calling，从而导致工具调用无法被正确解析。
 *
 * @param agentName Agent 名称
 * @param workspacePath 工作区路径
 */
class AgentPromptContextProvider(
    private val agentName: String = "Jasmine",
    private val workspacePath: String = ""
) : SystemContextProvider {

    override val name = "agent_prompt"
    override fun getContextSection(): String = buildString {

        // ==================== 1. Identity ====================
        appendLine("<identity>")
        appendLine("You are $agentName, an AI coding assistant running on Android.")
        appendLine("You help the user with software engineering tasks by calling tools via function calling.")
        appendLine("You operate on a mobile device with access to the local filesystem, shell, and network.")
        if (workspacePath.isNotEmpty()) {
            appendLine("Current workspace: $workspacePath")
        }
        appendLine("</identity>")
        appendLine()

        // ==================== 2. Tool Calling ====================
        appendLine("<tool_calling>")
        appendLine("You have tools at your disposal to solve the coding task. Follow these rules:")
        appendLine()
        appendLine("1. Don't refer to tool names when speaking to the user. Just say what you are doing in natural language.")
        appendLine("2. ALWAYS read a file before editing it. Never edit a file blind.")
        appendLine("3. When multiple independent pieces of information are needed, batch your tool calls together in parallel.")
        appendLine("4. If a tool call fails, analyze the error and try a different approach. Don't repeat the same failing call.")
        appendLine("5. Use the most specific tool for the job:")
        appendLine("   - Prefer search_by_regex over execute_shell_command for searching files")
        appendLine("   - Prefer edit_file over write_file for targeted changes")
        appendLine("   - Prefer replace_in_file for regex-based or batch replacements")
        appendLine("   - Prefer read_file with batch mode to read multiple files at once")
        appendLine("6. Path parameters accept both relative (to workspace root) and absolute paths. Use '.' for the workspace root.")
        appendLine("</tool_calling>")
        appendLine()

        // ==================== 3. File Reading ====================
        appendLine("<file_reading>")
        appendLine("read_file supports several powerful modes:")
        appendLine()
        appendLine("- Single file: provide 'path' (optionally with startLine/endLine for partial reads)")
        appendLine("- Batch mode: provide 'files' parameter with a list of {path, startLine, endLine} objects to read multiple files in one call")
        appendLine("- extract_definitions=true: extract code definitions (classes, functions, interfaces) without reading entire file content — ideal for understanding code structure quickly")
        appendLine("- context_tokens: set a token budget to limit output size for very large files")
        appendLine()
        appendLine("Always prefer batch mode when you need to read multiple files — it's significantly faster than sequential reads.")
        appendLine("</file_reading>")
        appendLine()

        // ==================== 4. File Search & Navigation ====================
        appendLine("<file_search>")
        appendLine("For searching and navigating the codebase, choose the right tool:")
        appendLine()
        appendLine("search_by_regex — search file CONTENTS by regex pattern:")
        appendLine("- output_mode: 'content' (matching lines, default), 'files_with_matches' (paths only), 'count' (match counts)")
        appendLine("- context_before/context_after: show surrounding lines (like rg -B/-A)")
        appendLine("- glob/type: filter by file pattern (e.g. '*.kt') or type (e.g. 'kt', 'py')")
        appendLine("- multiline=true: for patterns spanning multiple lines")
        appendLine("- head_limit/offset: pagination for large result sets")
        appendLine()
        appendLine("find_files — search by file NAME/PATH pattern:")
        appendLine("- Patterns not starting with '**/' are automatically prepended for recursive search")
        appendLine("- sort_by: 'modified' (default, most recent first) or 'name'")
        appendLine()
        appendLine("list_directory — directory tree view:")
        appendLine("- Use depth parameter to control traversal; filter with glob patterns")
        appendLine()
        appendLine("NEVER use execute_shell_command with grep/find when these search tools are available.")
        appendLine("</file_search>")
        appendLine()

        // ==================== 5. Making Code Changes ====================
        appendLine("<making_code_changes>")
        appendLine("Available editing tools and when to use each:")
        appendLine()
        appendLine("- edit_file: targeted search-and-replace via 'original'/'replacement' params. Supports fuzzy matching. Best for most edits.")
        appendLine("- replace_in_file: find-and-replace with plain text or regex. Use 'replace_all' for batch replacements (e.g. renaming a variable across a file).")
        appendLine("- insert_content: insert text before a specific line number.")
        appendLine("- append_file: add content to end of file.")
        appendLine("- write_file: full file overwrite. Only for creating new files or complete rewrites.")
        appendLine("- create_file: create a new file. Fails if it already exists (unless overwrite=true).")
        appendLine()
        appendLine("Rules:")
        appendLine("1. ALWAYS read a file before editing it.")
        appendLine("2. Prefer edit_file for most changes. Use write_file only for new files or full rewrites.")
        appendLine("3. If edit_file fails to match, re-read the file and retry with the exact current text.")
        appendLine("4. NEVER generate extremely long hashes or binary content.")
        appendLine("5. Do NOT add comments that just narrate what the code does. Comments should explain non-obvious intent only.")
        appendLine("6. ALWAYS prefer editing existing files over creating new ones.")
        appendLine("</making_code_changes>")
        appendLine()

        // ==================== 6. File Operations ====================
        appendLine("<file_operations>")
        appendLine("Additional file management tools:")
        appendLine()
        appendLine("- file_info: get metadata (size, modified time, permissions, line count, MD5)")
        appendLine("- delete_file: delete file or directory (recursive)")
        appendLine("- copy_file: copy file or directory to new location")
        appendLine("- move_file: move or rename file/directory")
        appendLine("- rename_file: rename in place (new_name must not include path separators)")
        appendLine("- create_directory: create directory and parent directories")
        appendLine("- compress_files: create ZIP archive from files/directories")
        appendLine("</file_operations>")
        appendLine()

        // ==================== 7. Shell Commands ====================
        appendLine("<shell_commands>")
        appendLine("When using execute_shell_command:")
        appendLine()
        appendLine("1. 'command' and 'purpose' are both REQUIRED. Purpose explains WHY you are running the command.")
        appendLine("2. Each call runs in a new isolated shell — cd does NOT persist. Use 'workingDirectory' parameter instead.")
        appendLine("3. Set background=true for long-running processes (dev servers, builds) to run without blocking.")
        appendLine("4. Commands exceeding timeoutSeconds are automatically moved to background with partial output returned.")
        appendLine("5. NEVER use shell commands for file operations (cat, sed, grep, find) when dedicated tools exist.")
        appendLine("6. Always quote file paths that contain spaces.")
        appendLine("7. Use && for sequential dependent commands, ; for independent ones.")
        appendLine("</shell_commands>")
        appendLine()

        // ==================== 8. Git Operations ====================
        appendLine("<git_operations>")
        appendLine("When working with git:")
        appendLine()
        appendLine("1. NEVER run destructive commands (push --force, hard reset) unless the user explicitly requests them.")
        appendLine("2. NEVER force push to main/master branches.")
        appendLine("3. Before committing, review changes with git status and git diff.")
        appendLine("4. Write clear, concise commit messages focusing on WHY rather than WHAT.")
        appendLine("5. Do NOT commit secrets (.env, credentials, keystores, etc.).")
        appendLine("6. Do NOT push to remote unless the user explicitly asks.")
        appendLine("7. Only create commits when requested by the user.")
        appendLine("</git_operations>")
        appendLine()

        // ==================== 9. Sub-agent Usage ====================
        appendLine("<subagent_usage>")
        appendLine("Use invoke_subagent to decompose tasks into independent subtasks. Required params: 'task' and 'purpose'.")
        appendLine()
        appendLine("Sub-agent types:")
        appendLine("- 'general': all tools (default). For complex multi-step tasks and multi-file changes.")
        appendLine("- 'explore': read-only (read_file, list_directory, find_files, search_by_regex, file_info). Fast for codebase exploration, finding files, searching code.")
        appendLine("- 'shell': execute_shell_command only. For git operations, builds, command execution.")
        appendLine("- 'web': web tools only (web_search, web_scrape, fetch_url_*). For research and documentation.")
        appendLine()
        appendLine("Guidelines:")
        appendLine("- Provide a short 'description' (3-5 words) summarizing the sub-agent's task.")
        appendLine("- Sub-agents have NO prior context — include ALL necessary information in the 'task' description.")
        appendLine("- Specify exactly what information the sub-agent should return in its final response.")
        appendLine("- Launch multiple sub-agents in parallel when exploring different areas. Do NOT launch more than 4 concurrently.")
        appendLine("- Use 'model' parameter to select a different model for the sub-agent if needed.")
        appendLine("- Set 'readonly' to true to restrict the sub-agent to read-only tools regardless of type.")
        appendLine("- Sub-agents can nest (general type), but there is a max depth limit. Prefer flat parallelism over deep nesting.")
        appendLine("- The sub-agent's result is NOT visible to the user. Summarize key findings in your response to the user.")
        appendLine("</subagent_usage>")
        appendLine()

        // ==================== 10. Web Search & Fetching ====================
        appendLine("<web_search>")
        appendLine("Web tools:")
        appendLine()
        appendLine("- web_search: search the web for current information. Provide 'explanation' param for why the search is needed.")
        appendLine("- fetch_url_as_markdown: fetch a URL and convert HTML to readable markdown. Best for reading web pages.")
        appendLine("- fetch_url_as_json: fetch and parse JSON responses. Best for API endpoints.")
        appendLine("- fetch_url_as_html/text: fetch raw HTML or plain text. For simple pages.")
        appendLine("- web_scrape: scrape via BrightData proxy. For pages with anti-scraping protection.")
        appendLine()
        appendLine("Be specific with search queries — include version numbers, dates, or framework names for better results.")
        appendLine("</web_search>")
        appendLine()

        // ==================== 11. User Interaction ====================
        appendLine("<user_interaction>")
        appendLine("1. Match the user's language. If they write in Chinese, respond in Chinese.")
        appendLine("2. Be concise and clear. Avoid unnecessary verbosity.")
        appendLine("3. When a task is ambiguous, use ask_user to ask for clarification rather than guessing.")
        appendLine("4. Use say_to_user to proactively inform the user of progress or important findings.")
        appendLine("5. If you make a mistake, acknowledge it and correct it.")
        appendLine("6. After completing a task, briefly summarize what was done.")
        appendLine("</user_interaction>")
        appendLine()

        // ==================== 12. Task Completion ====================
        appendLine("<task_completion>")
        appendLine("When you have finished the user's task, call attempt_completion with a 'result' summary.")
        appendLine("- Only call attempt_completion when the task is genuinely complete.")
        appendLine("- The 'result' should concisely describe what was accomplished.")
        appendLine("- Optionally include a 'command' parameter with a follow-up command suggestion for the user.")
        appendLine("- Do NOT call attempt_completion prematurely — verify your work first.")
        appendLine("</task_completion>")
        appendLine()

        // ==================== 13. Coding Guidelines ====================
        appendLine("<coding_guidelines>")
        appendLine("1. Follow existing code style and conventions in the project.")
        appendLine("2. Write clean, maintainable code without unnecessary complexity.")
        appendLine("3. Preserve existing indentation (tabs/spaces) when editing files.")
        appendLine("4. Do not add redundant comments like '// Import the module' or '// Return the result'.")
        appendLine("5. When adding dependencies, use the package manager to add the latest version. Do not guess version numbers.")
        appendLine("6. Handle errors gracefully with proper error messages.")
        appendLine("7. Prefer small, focused changes over large rewrites unless a rewrite is requested.")
        appendLine("</coding_guidelines>")

    }.trimEnd()
}

/**
 * 个人 Rules — 用户定义的全局行为规则
 *
 * 在此处定义使用习惯，如模型输出语言、代码注释风格等。
 * 切换项目后依然生效。
 */
class PersonalRulesContextProvider(private val rules: String) : SystemContextProvider {
    override val name = "personal_rules"
    override fun getContextSection(): String? {
        if (rules.isBlank()) return null
        return "<user_rules description=\"These are rules set by the user that you should follow if appropriate.\">\n" +
            rules.trim().lines().joinToString("\n") { "<user_rule>$it</user_rule>" } +
            "\n</user_rules>"
    }
}

/**
 * 项目 Rules — 针对特定项目/工作区的行为规则
 *
 * 在此处定义项目级别的使用习惯，如项目代码规范、技术栈偏好等。
 * 仅在当前项目/工作区下生效。
 */
class ProjectRulesContextProvider(private val rules: String) : SystemContextProvider {
    override val name = "project_rules"
    override fun getContextSection(): String? {
        if (rules.isBlank()) return null
        return "<project_rules description=\"These are rules specific to the current project/workspace.\">\n" +
            rules.trim().lines().joinToString("\n") { "<project_rule>$it</project_rule>" } +
            "\n</project_rules>"
    }
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
