package com.lhzkml.jasmine.core.agent.tools.feature

/**
 * Feature 唯一标识键
 * 移植自 koog 的 AIAgentStorageKey，用于在 pipeline 中唯一标识一个 Feature。
 *
 * @param T Feature 实现类型
 * @param name 键名称
 */
class FeatureKey<T : Any>(val name: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FeatureKey<*>) return false
        return name == other.name
    }

    override fun hashCode(): Int = name.hashCode()

    override fun toString(): String = "FeatureKey($name)"
}
