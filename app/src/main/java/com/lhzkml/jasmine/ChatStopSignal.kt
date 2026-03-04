package com.lhzkml.jasmine

import java.util.concurrent.atomic.AtomicBoolean

/**
 * 停止信号：用于本地模型（MNN）生成过程中响应用户点击停止按钮。
 * 因 MNN 使用阻塞式 JNI 调用，协程取消无法立即中断，需通过回调返回 true 让原生层退出。
 */
object ChatStopSignal {
    private val requested = AtomicBoolean(false)

    /** 请求停止生成（用户点击停止按钮时调用） */
    fun requestStop() {
        requested.set(true)
    }

    /** 开始新一轮生成前重置 */
    fun reset() {
        requested.set(false)
    }

    /** 是否已请求停止（MNN onToken 回调中检查） */
    fun isRequested(): Boolean = requested.get()
}
