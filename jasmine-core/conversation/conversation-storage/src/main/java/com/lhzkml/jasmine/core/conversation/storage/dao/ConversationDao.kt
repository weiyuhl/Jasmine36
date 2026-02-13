package com.lhzkml.jasmine.core.conversation.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lhzkml.jasmine.core.conversation.storage.entity.ConversationEntity
import com.lhzkml.jasmine.core.conversation.storage.entity.MessageEntity
import com.lhzkml.jasmine.core.conversation.storage.entity.UsageEntity
import kotlinx.coroutines.flow.Flow

/**
 * 对话数据访问对象
 */
@Dao
interface ConversationDao {

    // ========== 对话操作 ==========

    /** 创建对话 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    /** 更新对话 */
    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    /** 获取所有对话，按更新时间倒序 */
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    /** 根据 ID 获取对话 */
    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversation(id: String): ConversationEntity?

    /** 删除对话（消息会级联删除） */
    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    /** 更新对话的系统提示词 */
    @Query("UPDATE conversations SET systemPrompt = :systemPrompt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSystemPrompt(id: String, systemPrompt: String, updatedAt: Long)

    /** 删除所有对话 */
    @Query("DELETE FROM conversations")
    suspend fun deleteAllConversations()

    // ========== 消息操作 ==========

    /** 插入一条消息 */
    @Insert
    suspend fun insertMessage(message: MessageEntity): Long

    /** 批量插入消息 */
    @Insert
    suspend fun insertMessages(messages: List<MessageEntity>)

    /** 获取某个对话的所有消息，按时间正序 */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getMessages(conversationId: String): List<MessageEntity>

    /** 实时观察某个对话的消息 */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    /** 删除某个对话的所有消息 */
    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessages(conversationId: String)

    /** 获取某个对话的消息数量 */
    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId")
    suspend fun getMessageCount(conversationId: String): Int

    // ========== 用量操作 ==========

    /** 插入一条用量记录 */
    @Insert
    suspend fun insertUsage(usage: UsageEntity): Long

    /** 获取某个对话的用量汇总 */
    @Query("SELECT COALESCE(SUM(promptTokens), 0) as promptTokens, COALESCE(SUM(completionTokens), 0) as completionTokens, COALESCE(SUM(totalTokens), 0) as totalTokens FROM usage_records WHERE conversationId = :conversationId")
    suspend fun getConversationUsage(conversationId: String): UsageSummary

    /** 获取全局用量汇总 */
    @Query("SELECT COALESCE(SUM(promptTokens), 0) as promptTokens, COALESCE(SUM(completionTokens), 0) as completionTokens, COALESCE(SUM(totalTokens), 0) as totalTokens FROM usage_records")
    suspend fun getTotalUsage(): UsageSummary

    /** 获取全局用量汇总（实时观察） */
    @Query("SELECT COALESCE(SUM(promptTokens), 0) as promptTokens, COALESCE(SUM(completionTokens), 0) as completionTokens, COALESCE(SUM(totalTokens), 0) as totalTokens FROM usage_records")
    fun observeTotalUsage(): Flow<UsageSummary>

    /** 删除所有用量记录 */
    @Query("DELETE FROM usage_records")
    suspend fun deleteAllUsage()
}

/** 用量汇总数据类（Room 查询结果映射） */
data class UsageSummary(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)
