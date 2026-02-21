package com.lhzkml.jasmine.core.agent.tools.feature.pipeline

import com.lhzkml.jasmine.core.agent.tools.feature.AgentFunctionalFeature
import com.lhzkml.jasmine.core.agent.tools.feature.config.FeatureConfig

/**
 * 函数式 Agent Pipeline
 * 移植自 koog 的 AIAgentFunctionalPipeline。
 *
 * 不包含 Node/Subgraph 事件（函数式策略没有图结构）。
 */
class AgentFunctionalPipeline : AgentPipeline() {

    /** 安装函数式 Feature */
    fun <TConfig : FeatureConfig, TFeatureImpl : Any> install(
        feature: AgentFunctionalFeature<TConfig, TFeatureImpl>,
        configure: TConfig.() -> Unit
    ) {
        val featureConfig = feature.createInitialConfig().apply { configure() }
        val featureImpl = feature.install(featureConfig, this)
        super.install(feature.key, featureConfig, featureImpl)
    }
}
