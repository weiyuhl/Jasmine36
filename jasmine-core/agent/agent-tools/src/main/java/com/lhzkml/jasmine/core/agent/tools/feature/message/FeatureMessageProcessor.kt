package com.lhzkml.jasmine.core.agent.tools.feature.message

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Feature 消息处理器抽象基类
 * 移植自 koog 的 FeatureMessageProcessor。
 *
 * 负责处理 Feature 消息/事件的输出，如日志、文件写入等。
 * 子类实现 processMessage 方法来处理具体消息。
 */
abstract class FeatureMessageProcessor {

    /**
     * 消息过滤器
     * 移植自 koog 的 messageFilter。
     * 返回 true 表示处理该消息，false 表示忽略。
     */
    var messageFilter: (FeatureMessage) -> Boolean = { true }
        private set

    /**
     * 设置消息过滤器
     * 移植自 koog 的 setMessageFilter。
     */
    fun setMessageFilter(filter: (FeatureMessage) -> Boolean) {
        messageFilter = filter
    }

    /** 处理器是否处于打开状态 */
    private val _isOpen = AtomicBoolean(false)
    val isOpen: Boolean get() = _isOpen.get()

    /**
     * 初始化处理器
     * 移植自 koog 的 initialize()。
     */
    open suspend fun initialize() {
        _isOpen.set(true)
    }

    /**
     * 处理消息（子类实现）
     * 移植自 koog 的 processMessage()。
     */
    protected abstract suspend fun processMessage(message: FeatureMessage)

    /**
     * 接收并处理消息
     * 移植自 koog 的 onMessage()。
     * 先通过 messageFilter 过滤，通过后才调用 processMessage。
     */
    suspend fun onMessage(message: FeatureMessage) {
        if (messageFilter(message)) {
            processMessage(message)
        }
    }

    /**
     * 关闭处理器，释放资源
     */
    open fun close() {
        _isOpen.set(false)
    }
}
