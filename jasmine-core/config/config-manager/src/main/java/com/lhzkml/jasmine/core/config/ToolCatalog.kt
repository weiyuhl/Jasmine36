package com.lhzkml.jasmine.core.config

/**
 * 工具目录
 *
 * 定义所有可用工具的元数据（名称、描述），供 UI 层展示。
 * 避免工具定义硬编码在 Activity 中。
 */
object ToolCatalog {

    data class ToolMeta(
        val id: String,
        val description: String
    )

    val allTools: List<ToolMeta> = listOf(
        ToolMeta("calculator", "计算器（四则运算/科学计算/进制转换/单位转换/统计）"),
        ToolMeta("get_current_time", "获取当前时间"),
        ToolMeta("file_tools", "文件操作（读写/编辑/搜索/压缩等 17 个工具）"),
        ToolMeta("execute_shell_command", "执行命令"),
        ToolMeta("web_search", "网络搜索/抓取（需要 BrightData Key）"),
        ToolMeta("fetch_url", "URL 抓取（本地直接请求，HTML/纯文本/JSON）"),
        ToolMeta("attempt_completion", "显式完成任务（Agent 模式）")
    )
}
