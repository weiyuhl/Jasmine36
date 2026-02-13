package com.lhzkml.jasmine.core.conversation.storage.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Token 用量记录实体
 * 每次 API 调用记录一条
 */
@Entity(
    tableName = "usage_records",
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
data class UsageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** 所属对话 ID */
    val conversationId: String,
    /** 供应商 ID */
    val providerId: String,
    /** 模型名称 */
    val model: String,
    /** 提示 token 数 */
    val promptTokens: Int,
    /** 回复 token 数 */
    val completionTokens: Int,
    /** 总 token 数 */
    val totalTokens: Int,
    /** 记录时间戳 */
    val createdAt: Long
)
