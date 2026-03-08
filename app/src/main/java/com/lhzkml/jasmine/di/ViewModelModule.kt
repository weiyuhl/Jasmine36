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
            conversationRepo = get()
        )
    }
}
