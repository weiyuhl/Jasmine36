package com.lhzkml.jasmine.di

import com.lhzkml.jasmine.config.AppConfig
import com.lhzkml.jasmine.repository.*
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Repository 依赖注入模块
 *
 * 按阶段组织，从简单到复杂
 */
val repositoryModule = module {
    
    // ========== 阶段四：最简单的 Repository ==========
    
    single<TimeoutSettingsRepository> {
        DefaultTimeoutSettingsRepository(AppConfig.configRepo())
    }
    
    single<AboutRepository> {
        DefaultAboutRepository(androidContext())
    }
    
    // ========== 阶段三：设置类 Repository ==========
    
    single<SnapshotSettingsRepository> {
        DefaultSnapshotSettingsRepository(AppConfig.configRepo())
    }
    
    single<PlannerSettingsRepository> {
        DefaultPlannerSettingsRepository(AppConfig.configRepo())
    }
    
    single<AgentStrategyRepository> {
        DefaultAgentStrategyRepository(AppConfig.configRepo())
    }
    
    single<ToolSettingsRepository> {
        DefaultToolSettingsRepository(AppConfig.configRepo())
    }
    
    single<EventHandlerSettingsRepository> {
        DefaultEventHandlerSettingsRepository(AppConfig.configRepo())
    }
    
    single<ShellPolicyRepository> {
        DefaultShellPolicyRepository(AppConfig.configRepo())
    }
    
    single<RulesRepository> {
        DefaultRulesRepository(AppConfig.configRepo())
    }
    
    single<TraceSettingsRepository> {
        DefaultTraceSettingsRepository(AppConfig.configRepo())
    }
    
    single<CompressionSettingsRepository> {
        DefaultCompressionSettingsRepository(AppConfig.configRepo())
    }
    
    // ========== 阶段二：功能域 Repository ==========
    
    single<RagConfigRepository> {
        DefaultRagConfigRepository(AppConfig.configRepo())
    }
    
    single<RagLibraryRepository> {
        DefaultRagLibraryRepository()
    }
    
    single<McpRepository> {
        DefaultMcpRepository(
            configRepo = AppConfig.configRepo(),
            mcpConnectionManager = AppConfig.mcpConnectionManager()
        )
    }
    
    single<CheckpointRepository> {
        DefaultCheckpointRepository(AppConfig.checkpointService())
    }
    
    single<MnnModelRepository> {
        DefaultMnnModelRepository(androidContext())
    }
    
    // ========== 阶段一：核心 Repository ==========
    
    single<SessionRepository> {
        DefaultSessionRepository(AppConfig.configRepo())
    }
    
    single<ChatConversationRepository> {
        DefaultChatConversationRepository(get()) // 使用 appModule 中的 ConversationRepository
    }
    
    single<ProviderRepository> {
        DefaultProviderRepository(
            configRepo = AppConfig.configRepo(),
            providerRegistry = AppConfig.providerRegistry()
        )
    }
    
    single<ModelSelectionRepository> {
        DefaultModelSelectionRepository(
            context = androidContext(),
            configRepo = AppConfig.configRepo()
        )
    }
    
    single<LlmSettingsRepository> {
        DefaultLlmSettingsRepository(AppConfig.configRepo())
    }
}
