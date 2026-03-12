package com.lhzkml.jasmine.repository

import com.lhzkml.jasmine.core.agent.observe.trace.TraceEventCategory
import com.lhzkml.jasmine.core.config.ConfigRepository

/**
 * Trace 设置 Repository
 *
 * 负责：
 * - Trace 开关
 * - File 输出开关
 * - TraceEventFilter
 *
 * 对应页面：
 * - TraceConfigActivity
 * - LauncherActivity 中默认 trace 开关
 * - SettingsActivity 中 trace 摘要
 */
interface TraceSettingsRepository {
    
    fun isTraceEnabled(): Boolean
    fun setTraceEnabled(enabled: Boolean)
    
    fun isTraceFileEnabled(): Boolean
    fun setTraceFileEnabled(enabled: Boolean)
    
    /**
     * 获取 Trace 事件过滤器
     * 空集合表示监听所有事件
     */
    fun getTraceEventFilter(): Set<TraceEventCategory>
    fun setTraceEventFilter(filter: Set<TraceEventCategory>)
}

class DefaultTraceSettingsRepository(
    private val configRepo: ConfigRepository
) : TraceSettingsRepository {
    
    override fun isTraceEnabled(): Boolean = configRepo.isTraceEnabled()
    
    override fun setTraceEnabled(enabled: Boolean) {
        configRepo.setTraceEnabled(enabled)
    }
    
    override fun isTraceFileEnabled(): Boolean = configRepo.isTraceFileEnabled()
    
    override fun setTraceFileEnabled(enabled: Boolean) {
        configRepo.setTraceFileEnabled(enabled)
    }
    
    override fun getTraceEventFilter(): Set<TraceEventCategory> = configRepo.getTraceEventFilter()
    
    override fun setTraceEventFilter(filter: Set<TraceEventCategory>) {
        configRepo.setTraceEventFilter(filter)
    }
}
