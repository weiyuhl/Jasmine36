package com.lhzkml.jasmine.di

import com.lhzkml.jasmine.config.AppConfig
import com.lhzkml.jasmine.core.config.ConfigRepository
import com.lhzkml.jasmine.repository.*
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Repository 依赖注入模块
 *
 * 按阶段组织，从简单到复杂
 * 
 * 修复说明：
 * - ConfigRepository 统一通过 DI 注入，避免直接使用 AppConfig.configRepo()
 * - 这样便于单元测试和依赖管理
 */
val repositoryModule = module {
    
    // ========== ConfigRepository 注入 ==========
    
    single<ConfigRepository> {
        AppConfig.configRepo()
    }
    
    // ========== 阶段四：最简单的 Repository ==========
    
    single<TimeoutSettingsRepository> {
        DefaultTimeoutSettingsRepository(get())
    }
    
    single<AboutRepository> {
        DefaultAboutRepository(androidContext())
    }
    
    // ========== 阶段三：设置类 Repository ==========
    
    single<SnapshotSettingsRepository> {
        DefaultSnapshotSettingsRepository(get())
    }
    
    single<PlannerSettingsRepository> {
        DefaultPlannerSettingsRepository(get())
    }
    
    single<AgentStrategyRepository> {
        DefaultAgentStrategyRepository(get())
    }
    
    single<ToolSettingsRepository> {
        DefaultToolSettingsRepository(get())
    }
    
    single<EventHandlerSettingsRepository> {
        DefaultEventHandlerSettingsRepository(get())
    }
    
    single<ShellPolicyRepository> {
        DefaultShellPolicyRepository(get())
    }
    
    single<RulesRepository> {
        DefaultRulesRepository(get())
    }
    
    single<TraceSettingsRepository> {
        DefaultTraceSettingsRepository(get())
    }
    
    single<CompressionSettingsRepository> {
        DefaultCompressionSettingsRepository(get())
    }
    
    // ========== 阶段二：功能域 Repository ==========
    
    single<RagConfigRepository> {
        DefaultRagConfigRepository(get())
    }
    
    single<RagLibraryRepository> {
        DefaultRagLibraryRepository()
    }
    
    single<McpRepository> {
        DefaultMcpRepository(
            configRepo = get(),
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
        DefaultSessionRepository(get())
    }
    
    single<ChatConversationRepository> {
        DefaultChatConversationRepository(get()) // 使用 appModule 中的 ConversationRepository
    }
    
    single<ProviderRepository> {
        DefaultProviderRepository(
            configRepo = get(),
            providerRegistry = AppConfig.providerRegistry()
        )
    }
    
    single<ModelSelectionRepository> {
        DefaultModelSelectionRepository(
            context = androidContext(),
            configRepo = get()
        )
    }
    
    single<LlmSettingsRepository> {
        DefaultLlmSettingsRepository(get())
    }
}
