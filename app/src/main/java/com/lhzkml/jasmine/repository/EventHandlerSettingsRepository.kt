package com.lhzkml.jasmine.repository

import com.lhzkml.jasmine.core.agent.observe.event.EventCategory
import com.lhzkml.jasmine.core.config.ConfigRepository

/**
 * EventHandler 设置 Repository
 *
 * 负责：
 * - EventHandler 开关
 * - EventHandlerFilter
 *
 * 对应页面：
 * - EventHandlerConfigActivity
 * - LauncherActivity 中默认 event handler 开关
 * - SettingsActivity 中摘要显示
 */
interface EventHandlerSettingsRepository {
    
    fun isEventHandlerEnabled(): Boolean
    fun setEventHandlerEnabled(enabled: Boolean)
    
    /**
     * 获取事件处理器过滤器
     * 空集合表示监听所有事件
     */
    fun getEventHandlerFilter(): Set<EventCategory>
    fun setEventHandlerFilter(filter: Set<EventCategory>)
}

class DefaultEventHandlerSettingsRepository(
    private val configRepo: ConfigRepository
) : EventHandlerSettingsRepository {
    
    override fun isEventHandlerEnabled(): Boolean = configRepo.isEventHandlerEnabled()
    
    override fun setEventHandlerEnabled(enabled: Boolean) {
        configRepo.setEventHandlerEnabled(enabled)
    }
    
    override fun getEventHandlerFilter(): Set<EventCategory> = configRepo.getEventHandlerFilter()
    
    override fun setEventHandlerFilter(filter: Set<EventCategory>) {
        configRepo.setEventHandlerFilter(filter)
    }
}
