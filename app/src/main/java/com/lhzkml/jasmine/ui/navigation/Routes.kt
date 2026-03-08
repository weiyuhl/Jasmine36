package com.lhzkml.jasmine.ui.navigation

/**
 * 全应用路由常量
 *
 * Single-Activity + Navigation Compose，所有设置子页面均通过路由导航。
 */
object Routes {
    const val MAIN = "main"
    const val SETTINGS = "settings"

    // 模型与供应商
    const val PROVIDER_LIST = "provider_list"
    const val PROVIDER_CONFIG = "provider_config/{providerId}"
    const val ADD_CUSTOM_PROVIDER = "add_custom_provider"

    // MNN
    const val MNN_MANAGEMENT = "mnn_management"
    const val MNN_MODEL_MARKET = "mnn_model_market"
    const val MNN_MODEL_SETTINGS = "mnn_model_settings/{modelId}"

    // 模型参数
    const val TOKEN_MANAGEMENT = "token_management"
    const val SAMPLING_PARAMS = "sampling_params"
    const val SYSTEM_PROMPT = "system_prompt"

    // RAG
    const val RAG_CONFIG = "rag_config"
    const val RAG_LIBRARY_CONTENT = "rag_library_content/{libraryId}"
    const val EMBEDDING_CONFIG = "embedding_config"

    // Rules
    const val RULES = "rules"

    // Agent 与工具
    const val TOOL_CONFIG = "tool_config"
    const val TOOL_CONFIG_AGENT = "tool_config_agent"
    const val AGENT_STRATEGY = "agent_strategy"
    const val MCP_SERVER = "mcp_server"
    const val MCP_SERVER_EDIT = "mcp_server_edit/{serverId}"
    const val SHELL_POLICY = "shell_policy"

    // 执行与调试
    const val COMPRESSION_CONFIG = "compression_config"
    const val TIMEOUT_CONFIG = "timeout_config"
    const val TRACE_CONFIG = "trace_config"
    const val PLANNER_CONFIG = "planner_config"
    const val SNAPSHOT_CONFIG = "snapshot_config"
    const val EVENT_HANDLER_CONFIG = "event_handler_config"

    // 检查点
    const val CHECKPOINT_MANAGER = "checkpoint_manager"
    const val CHECKPOINT_DETAIL = "checkpoint_detail/{agentId}"

    // 关于
    const val ABOUT = "about"
    const val OSS_LICENSES_LIST = "oss_licenses_list"
    const val OSS_LICENSES_DETAIL = "oss_licenses_detail/{name}"

    fun providerConfig(providerId: String) = "provider_config/$providerId"
    fun mnnModelSettings(modelId: String) = "mnn_model_settings/$modelId"
    fun ragLibraryContent(libraryId: String) = "rag_library_content/$libraryId"
    fun mcpServerEdit(serverId: String) = "mcp_server_edit/$serverId"
    fun checkpointDetail(agentId: String) = "checkpoint_detail/$agentId"
    fun ossLicensesDetail(name: String) = "oss_licenses_detail/$name"
}
