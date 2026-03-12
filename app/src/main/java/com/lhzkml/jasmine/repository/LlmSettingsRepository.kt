package com.lhzkml.jasmine.repository

import com.lhzkml.jasmine.core.config.ConfigRepository

/**
 * LLM 设置 Repository
 *
 * 负责：
 * - 默认系统提示词
 * - Max Tokens
 * - Temperature
 * - TopP
 * - TopK
 *
 * 对应页面：
 * - SystemPromptConfigActivity
 * - TokenManagementActivity
 * - SamplingParamsConfigActivity
 * - SettingsActivity 中 LLM 参数摘要
 */
interface LlmSettingsRepository {
    
    // System Prompt
    fun getDefaultSystemPrompt(): String
    fun setDefaultSystemPrompt(prompt: String)
    
    // Max Tokens
    fun getMaxTokens(): Int
    fun setMaxTokens(tokens: Int)
    
    // Temperature
    fun getTemperature(): Double
    fun setTemperature(temperature: Double)
    
    // TopP
    fun getTopP(): Double
    fun setTopP(topP: Double)
    
    // TopK
    fun getTopK(): Int
    fun setTopK(topK: Int)
}

class DefaultLlmSettingsRepository(
    private val configRepo: ConfigRepository
) : LlmSettingsRepository {
    
    override fun getDefaultSystemPrompt(): String = configRepo.getDefaultSystemPrompt()
    
    override fun setDefaultSystemPrompt(prompt: String) {
        configRepo.setDefaultSystemPrompt(prompt)
    }
    
    override fun getMaxTokens(): Int = configRepo.getMaxTokens()
    
    override fun setMaxTokens(tokens: Int) {
        configRepo.setMaxTokens(tokens)
    }
    
    override fun getTemperature(): Double = configRepo.getTemperature().toDouble()
    
    override fun setTemperature(temperature: Double) {
        configRepo.setTemperature(temperature.toFloat())
    }
    
    override fun getTopP(): Double = configRepo.getTopP().toDouble()
    
    override fun setTopP(topP: Double) {
        configRepo.setTopP(topP.toFloat())
    }
    
    override fun getTopK(): Int = configRepo.getTopK()
    
    override fun setTopK(topK: Int) {
        configRepo.setTopK(topK)
    }
}
