package com.lhzkml.jasmine.core.conversation.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.lhzkml.jasmine.core.conversation.storage.dao.ConversationDao
import com.lhzkml.jasmine.core.conversation.storage.entity.ConversationEntity
import com.lhzkml.jasmine.core.conversation.storage.entity.MessageEntity

/**
 * Jasmine 本地数据库
 */
@Database(
    entities = [ConversationEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class JasmineDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao

    companion object {
        @Volatile
        private var INSTANCE: JasmineDatabase? = null

        /**
         * 获取数据库单例
         */
        fun getInstance(context: Context): JasmineDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    JasmineDatabase::class.java,
                    "jasmine.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
