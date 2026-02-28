package com.lhzkml.jasmine.core.agent.observe.snapshot

/**
 * 持久化工具类
 * 完整移植 koog 的 PersistenceUtils，提供持久化相关的常量和配置。
 */
object PersistenceUtils {
    /**
     * 墓碑检查点名称
     * 墓碑检查点是特殊标记，表示 Agent 会话已终止或不再有效。
     */
    const val TOMBSTONE_CHECKPOINT_NAME: String = "tombstone"
}
