package com.lhzkml.jasmine.repository

import com.lhzkml.jasmine.core.config.ConfigRepository

/**
 * Planner 设置 Repository
 *
 * 负责：
 * - Planner 开关
 * - Planner 最大迭代
 * - Critic 开关
 *
 * 对应页面：
 * - PlannerConfigActivity
 * - ChatExecutor 中 planner 参数读取
 */
interface PlannerSettingsRepository {
    
    fun isPlannerEnabled(): Boolean
    fun setPlannerEnabled(enabled: Boolean)
    
    fun getPlannerMaxIterations(): Int
    fun setPlannerMaxIterations(max: Int)
    
    fun isPlannerCriticEnabled(): Boolean
    fun setPlannerCriticEnabled(enabled: Boolean)
}

class DefaultPlannerSettingsRepository(
    private val configRepo: ConfigRepository
) : PlannerSettingsRepository {
    
    override fun isPlannerEnabled(): Boolean = configRepo.isPlannerEnabled()
    
    override fun setPlannerEnabled(enabled: Boolean) {
        configRepo.setPlannerEnabled(enabled)
    }
    
    override fun getPlannerMaxIterations(): Int = configRepo.getPlannerMaxIterations()
    
    override fun setPlannerMaxIterations(max: Int) {
        configRepo.setPlannerMaxIterations(max)
    }
    
    override fun isPlannerCriticEnabled(): Boolean = configRepo.isPlannerCriticEnabled()
    
    override fun setPlannerCriticEnabled(enabled: Boolean) {
        configRepo.setPlannerCriticEnabled(enabled)
    }
}
