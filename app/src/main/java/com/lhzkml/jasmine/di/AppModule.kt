package com.lhzkml.jasmine.di

import com.lhzkml.jasmine.core.conversation.storage.ConversationRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Koin 应用层模块
 *
 * 提供 Repository、单例等依赖，使 ViewModel 可注入、可测试。
 */
val appModule = module {

    single {
        ConversationRepository(androidContext())
    }
}
