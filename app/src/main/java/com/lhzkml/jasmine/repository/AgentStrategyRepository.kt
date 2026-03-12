package com.lhzkml.jasmine.repository

import com.lhzkml.jasmine.core.config.AgentStrategyType
import com.lhzkml.jasmine.core.config.ConfigRepository
import com.lhzkml.jasmine.core.config.GraphToolCallMode
import com.lhzkml.jasmine.core.config.ToolChoiceMode
import com.lhzkml.jasmine.core.config.ToolSelectionStrategyType

/**
 * Agent 策略 Repository
 *
 * 负责：
 * - Agent Strategy
 * - Graph Tool Call Mode
 * - Tool Selection Strategy / Names / TaskDesc
 * - Tool Choice Mode / NamedTool
 * - Agent MaxIterations
 * - MaxToolResultLength
 *
 * 对应页面：
 * - AgentStrategyActivity
 * - ChatExecutor 中 agent 执行参数读取
 */
interface AgentStrategyRepository {
    
    // Agent Strategy
    fun getAgentStrategy(): AgentStrategyType
    fun setAgentStrategy(strategy: AgentStrategyType)
    
    // Graph Tool Call Mode
    fun getGraphToolCallMode(): GraphToolCallMode
    fun setGraphToolCallMode(mode: GraphToolCallMode)
    
    // Tool Selection Strategy
    fun getToolSelectionStrategy(): ToolSelectionStrategyType
    fun setToolSelectionStrategy(strategy: ToolSelectionStrategyType)
    fun getToolSelectionNames(): Set<String>
    fun setToolSelectionNames(names: Set<String>)
    fun getToolSelectionTaskDesc(): String
    fun setToolSelectionTaskDesc(desc: String)
    
    // Tool Choice
    fun getToolChoiceMode(): ToolChoiceMode
    fun setToolChoiceMode(mode: ToolChoiceMode)
    fun getToolChoiceNamedTool(): String
    fun setToolChoiceNamedTool(toolName: String)
    
    // Agent Iterations
    fun getAgentMaxIterations(): Int
    fun setAgentMaxIterations(max: Int)
    
    // Tool Result Length
    fun getMaxToolResultLength(): Int
    fun setMaxToolResultLength(length: Int)
}

class DefaultAgentStrategyRepository(
    private val configRepo: ConfigRepository
) : AgentStrategyRepository {
    
    override fun getAgentStrategy(): AgentStrategyType = configRepo.getAgentStrategy()
    
    override fun setAgentStrategy(strategy: AgentStrategyType) {
        configRepo.setAgentStrategy(strategy)
    }
    
    override fun getGraphToolCallMode(): GraphToolCallMode = configRepo.getGraphToolCallMode()
    
    override fun setGraphToolCallMode(mode: GraphToolCallMode) {
        configRepo.setGraphToolCallMode(mode)
    }
    
    override fun getToolSelectionStrategy(): ToolSelectionStrategyType = 
        configRepo.getToolSelectionStrategy()
    
    override fun setToolSelectionStrategy(strategy: ToolSelectionStrategyType) {
        configRepo.setToolSelectionStrategy(strategy)
    }
    
    override fun getToolSelectionNames(): Set<String> = configRepo.getToolSelectionNames()
    
    override fun setToolSelectionNames(names: Set<String>) {
        configRepo.setToolSelectionNames(names)
    }
    
    override fun getToolSelectionTaskDesc(): String = configRepo.getToolSelectionTaskDesc()
    
    override fun setToolSelectionTaskDesc(desc: String) {
        configRepo.setToolSelectionTaskDesc(desc)
    }
    
    override fun getToolChoiceMode(): ToolChoiceMode = configRepo.getToolChoiceMode()
    
    override fun setToolChoiceMode(mode: ToolChoiceMode) {
        configRepo.setToolChoiceMode(mode)
    }
    
    override fun getToolChoiceNamedTool(): String = configRepo.getToolChoiceNamedTool()
    
    override fun setToolChoiceNamedTool(toolName: String) {
        configRepo.setToolChoiceNamedTool(toolName)
    }
    
    override fun getAgentMaxIterations(): Int = configRepo.getAgentMaxIterations()
    
    override fun setAgentMaxIterations(max: Int) {
        configRepo.setAgentMaxIterations(max)
    }
    
    override fun getMaxToolResultLength(): Int = configRepo.getMaxToolResultLength()
    
    override fun setMaxToolResultLength(length: Int) {
        configRepo.setMaxToolResultLength(length)
    }
}
