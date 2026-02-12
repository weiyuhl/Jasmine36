package com.lhzkml.jasmine.core.conversation.storage.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 消息实体，代表对话中的一条消息
 */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** 所属对话 ID */
    val conversationId: String,
    /** 角色：system / user / assistant */
    val role: String,
    /** 消息内容 */
    val content: String,
    /** 创建时间戳（毫秒） */
    val createdAt: Long
)
