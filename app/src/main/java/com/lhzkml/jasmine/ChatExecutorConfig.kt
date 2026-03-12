package com.lhzkml.jasmine

import com.lhzkml.jasmine.core.config.AgentStrategyType
import com.lhzkml.jasmine.core.config.GraphToolCallMode
import com.lhzkml.jasmine.core.config.ToolChoiceMode
import com.lhzkml.jasmine.core.config.ToolSelectionStrategyType

/**
 * ChatExecutor 配置数据类
 * 
 * 封装所有 ChatExecutor 需要的配置参数，避免直接调用 ProviderManager。
 * 由 ChatViewModel 从各个 Repository 收集配置后传入。
 */
data class ChatExecutorConfig(
    // Tool 相关
    val toolsEnabled: Boolean,
    
    // LLM Settings 相关
    val defaultSystemPrompt: String,
    val maxTokens: Int,
    val temperature: Double,
    val topP: Double,
    val topK: Int,
    
    // Session 相关
    val isAgentMode: Boolean,
    val workspacePath: String,
    
    // Compression 相关
    val compressionEnabled: Boolean,
    
    // Agent Strategy 相关
    val agentStrategy: AgentStrategyType,
    val agentMaxIterations: Int,
    val maxToolResultLength: Int,
    
    // Tool Choice 相关
    val toolChoiceMode: ToolChoiceMode,
    val toolChoiceNamedTool: String,
    
    // Graph Tool Call 相关
    val graphToolCallMode: GraphToolCallMode,
    
    // Tool Selection 相关
    val toolSelectionStrategy: ToolSelectionStrategyType,
    val toolSelectionNames: Set<String>,
    val toolSelectionTaskDesc: String,
    
    // Planner 相关
    val plannerEnabled: Boolean,
    val plannerMaxIterations: Int,
    val plannerCriticEnabled: Boolean,
    
    // Stream Resume 相关
    val streamResumeEnabled: Boolean,
    val streamResumeMaxRetries: Int
)
