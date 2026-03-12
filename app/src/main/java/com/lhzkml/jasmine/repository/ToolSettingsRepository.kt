package com.lhzkml.jasmine.repository

import com.lhzkml.jasmine.core.config.ConfigRepository

/**
 * 工具设置 Repository
 *
 * 负责：
 * - Tools 总开关
 * - Enabled Tools
 * - Agent Tool Preset
 * - BrightData Key
 *
 * 对应页面：
 * - ToolConfigActivity
 * - LauncherActivity 中进入工作区时的默认工具开关
 * - SettingsActivity 中工具摘要
 */
interface ToolSettingsRepository {
    
    // Tools 总开关
    fun isToolsEnabled(): Boolean
    fun setToolsEnabled(enabled: Boolean)
    
    // Enabled Tools (空集合表示全部启用)
    fun getEnabledTools(): Set<String>
    fun setEnabledTools(tools: Set<String>)
    
    // Agent Tool Preset
    fun getAgentToolPreset(): Set<String>
    fun setAgentToolPreset(tools: Set<String>)
    
    // BrightData Key
    fun getBrightDataKey(): String
    fun setBrightDataKey(key: String)
}

class DefaultToolSettingsRepository(
    private val configRepo: ConfigRepository
) : ToolSettingsRepository {
    
    override fun isToolsEnabled(): Boolean = configRepo.isToolsEnabled()
    
    override fun setToolsEnabled(enabled: Boolean) {
        configRepo.setToolsEnabled(enabled)
    }
    
    override fun getEnabledTools(): Set<String> = configRepo.getEnabledTools()
    
    override fun setEnabledTools(tools: Set<String>) {
        configRepo.setEnabledTools(tools)
    }
    
    override fun getAgentToolPreset(): Set<String> = configRepo.getAgentToolPreset()
    
    override fun setAgentToolPreset(tools: Set<String>) {
        configRepo.setAgentToolPreset(tools)
    }
    
    override fun getBrightDataKey(): String = configRepo.getBrightDataKey()
    
    override fun setBrightDataKey(key: String) {
        configRepo.setBrightDataKey(key)
    }
}
