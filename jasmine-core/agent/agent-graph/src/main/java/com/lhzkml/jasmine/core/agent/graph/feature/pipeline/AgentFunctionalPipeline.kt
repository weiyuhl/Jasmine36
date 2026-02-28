package com.lhzkml.jasmine.core.agent.graph.feature.pipeline

import com.lhzkml.jasmine.core.agent.graph.feature.AgentFunctionalFeature
import com.lhzkml.jasmine.core.agent.graph.feature.config.FeatureConfig

/**
 * 函数�?Agent Pipeline
 * 移植�?koog �?AIAgentFunctionalPipeline�?
 *
 * 不包�?Node/Subgraph 事件（函数式策略没有图结构）�?
 */
class AgentFunctionalPipeline : AgentPipeline() {

    /** 安装函数�?Feature */
    fun <TConfig : FeatureConfig, TFeatureImpl : Any> install(
        feature: AgentFunctionalFeature<TConfig, TFeatureImpl>,
        configure: TConfig.() -> Unit
    ) {
        val featureConfig = feature.createInitialConfig().apply { configure() }
        val featureImpl = feature.install(featureConfig, this)
        super.install(feature.key, featureConfig, featureImpl)
    }
}
