package com.lhzkml.jasmine.core.agent.tools.feature

import com.lhzkml.jasmine.core.agent.tools.feature.config.FeatureConfig
import com.lhzkml.jasmine.core.agent.tools.feature.pipeline.AgentFunctionalPipeline
import com.lhzkml.jasmine.core.agent.tools.feature.pipeline.AgentGraphPipeline

/**
 * Agent Feature 基础接口
 * 移植自 koog 的 AIAgentFeature。
 *
 * Feature 是可以安装到 Agent Pipeline 中的插件，提供特定功能和配置能力。
 *
 * @param TConfig 配置类型
 * @param TFeatureImpl Feature 实现类型
 */
interface AgentFeature<TConfig : FeatureConfig, TFeatureImpl : Any> {

    /** Feature 唯一标识键 */
    val key: FeatureKey<TFeatureImpl>

    /** 创建初始配置 */
    fun createInitialConfig(): TConfig
}

/**
 * 图策略 Agent Feature
 * 移植自 koog 的 AIAgentGraphFeature。
 * 可安装到 AgentGraphPipeline 中。
 */
interface AgentGraphFeature<TConfig : FeatureConfig, TFeatureImpl : Any> : AgentFeature<TConfig, TFeatureImpl> {
    /** 安装 Feature 到图 Pipeline */
    fun install(config: TConfig, pipeline: AgentGraphPipeline): TFeatureImpl
}

/**
 * 函数式 Agent Feature
 * 移植自 koog 的 AIAgentFunctionalFeature。
 * 可安装到 AgentFunctionalPipeline 中。
 */
interface AgentFunctionalFeature<TConfig : FeatureConfig, TFeatureImpl : Any> : AgentFeature<TConfig, TFeatureImpl> {
    /** 安装 Feature 到函数式 Pipeline */
    fun install(config: TConfig, pipeline: AgentFunctionalPipeline): TFeatureImpl
}
