package com.lhzkml.jasmine.core.agent.tools

/**
 * 子代理配置
 *
 * @param maxDepth 最大嵌套深度，防止无限递归（默认 2 层）
 * @param maxIterationsPerSubAgent 每个子代理最大工具调用迭代次数（默认 8）
 * @param enabledTypes 允许的子代理类型
 */
data class SubAgentConfig(
    val maxDepth: Int = 2,
    val maxIterationsPerSubAgent: Int = 8,
    val enabledTypes: Set<String> = setOf(
        SubAgentType.GENERAL,
        SubAgentType.EXPLORE,
        SubAgentType.SHELL,
        SubAgentType.WEB
    )
)

/**
 * 子代理类型常量，每种类型对应不同的工具子集
 */
object SubAgentType {
    const val GENERAL = "general"
    const val EXPLORE = "explore"
    const val SHELL = "shell"
    const val WEB = "web"

    /** explore 类型可用的只读工具名 */
    val EXPLORE_TOOLS = setOf(
        "read_file", "list_directory", "find_files",
        "search_by_regex", "file_info"
    )

    /** shell 类型可用的工具名 */
    val SHELL_TOOLS = setOf("execute_shell_command")

    /** web 类型可用的工具名 */
    val WEB_TOOLS = setOf(
        "web_search", "web_scrape",
        "fetch_url_as_html", "fetch_url_as_text", "fetch_url_as_json"
    )
}
