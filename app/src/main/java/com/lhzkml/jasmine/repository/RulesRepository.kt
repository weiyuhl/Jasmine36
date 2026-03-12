package com.lhzkml.jasmine.repository

import com.lhzkml.jasmine.core.config.ConfigRepository

/**
 * Rules 规则 Repository
 *
 * 负责：
 * - Personal Rules
 * - Project Rules
 *
 * 对应页面：
 * - RulesActivity
 * - SettingsActivity 中 Rules 摘要
 */
interface RulesRepository {
    
    /**
     * 获取个人规则（全局）
     */
    fun getPersonalRules(): String
    fun setPersonalRules(rules: String)
    
    /**
     * 获取项目规则（工作区相关）
     */
    fun getProjectRules(workspacePath: String): String
    fun setProjectRules(workspacePath: String, rules: String)
}

class DefaultRulesRepository(
    private val configRepo: ConfigRepository
) : RulesRepository {
    
    override fun getPersonalRules(): String = configRepo.getPersonalRules()
    
    override fun setPersonalRules(rules: String) {
        configRepo.setPersonalRules(rules)
    }
    
    override fun getProjectRules(workspacePath: String): String = 
        configRepo.getProjectRules(workspacePath)
    
    override fun setProjectRules(workspacePath: String, rules: String) {
        configRepo.setProjectRules(workspacePath, rules)
    }
}
