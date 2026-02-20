package com.lhzkml.jasmine.core.conversation.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lhzkml.jasmine.core.conversation.storage.dao.ConversationDao
import com.lhzkml.jasmine.core.conversation.storage.entity.ConversationEntity
import com.lhzkml.jasmine.core.conversation.storage.entity.MessageEntity
import com.lhzkml.jasmine.core.conversation.storage.entity.UsageEntity

/**
 * Jasmine 本地数据库
 */
@Database(
    entities = [ConversationEntity::class, MessageEntity::class, UsageEntity::class],
    version = 3,
    exportSchema = false
)
abstract class JasmineDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao

    companion object {
        @Volatile
        private var INSTANCE: JasmineDatabase? = null

        /** v1 -> v2: 添加 usage_records 表 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `usage_records` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `conversationId` TEXT NOT NULL,
                        `providerId` TEXT NOT NULL,
                        `model` TEXT NOT NULL,
                        `promptTokens` INTEGER NOT NULL,
                        `completionTokens` INTEGER NOT NULL,
                        `totalTokens` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_usage_records_conversationId` ON `usage_records` (`conversationId`)")
            }
        }

        /** v2 -> v3: conversations 表添加 workspacePath 列 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN workspacePath TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * 获取数据库单例
         */
        fun getInstance(context: Context): JasmineDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    JasmineDatabase::class.java,
                    "jasmine.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
