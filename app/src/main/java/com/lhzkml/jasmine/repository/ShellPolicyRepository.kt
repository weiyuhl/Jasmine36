package com.lhzkml.jasmine.repository

import com.lhzkml.jasmine.core.agent.tools.ShellPolicy
import com.lhzkml.jasmine.core.config.ConfigRepository

/**
 * Shell 策略 Repository
 *
 * 负责：
 * - Shell Policy
 * - Shell Blacklist
 * - Shell Whitelist
 *
 * 对应页面：
 * - ShellPolicyActivity
 * - SettingsActivity 中 Shell 策略摘要
 */
interface ShellPolicyRepository {
    
    fun getShellPolicy(): ShellPolicy
    fun setShellPolicy(policy: ShellPolicy)
    
    fun getShellBlacklist(): List<String>
    fun setShellBlacklist(commands: List<String>)
    
    fun getShellWhitelist(): List<String>
    fun setShellWhitelist(commands: List<String>)
}

class DefaultShellPolicyRepository(
    private val configRepo: ConfigRepository
) : ShellPolicyRepository {
    
    override fun getShellPolicy(): ShellPolicy = configRepo.getShellPolicy()
    
    override fun setShellPolicy(policy: ShellPolicy) {
        configRepo.setShellPolicy(policy)
    }
    
    override fun getShellBlacklist(): List<String> = configRepo.getShellBlacklist()
    
    override fun setShellBlacklist(commands: List<String>) {
        configRepo.setShellBlacklist(commands)
    }
    
    override fun getShellWhitelist(): List<String> = configRepo.getShellWhitelist()
    
    override fun setShellWhitelist(commands: List<String>) {
        configRepo.setShellWhitelist(commands)
    }
}
