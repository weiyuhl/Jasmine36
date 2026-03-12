package com.lhzkml.jasmine.di

import com.lhzkml.jasmine.ui.ChatViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin ViewModel 模块
 *
 * ChatViewModel 注入 ConversationRepository，单元测试时可替换为 Mock。
 */
val viewModelModule = module {

    viewModel {
        ChatViewModel(
            application = get(),
            conversationRepo = get(),
            sessionRepository = get(),
            providerRepository = get(),
            modelSelectionRepository = get(),
            llmSettingsRepository = get(),
            timeoutSettingsRepository = get(),
            toolSettingsRepository = get(),
            agentStrategyRepository = get(),
            ragConfigRepository = get(),
            mcpRepository = get(),
            compressionSettingsRepository = get(),
            snapshotSettingsRepository = get(),
            plannerSettingsRepository = get()
        )
    }
}
