package com.lhzkml.jasmine.core.conversation.storage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 对话实体，代表一次完整的对话会话
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    val id: String,
    /** 对话标题（通常取第一条用户消息的摘要） */
    val title: String,
    /** 使用的供应商 ID */
    val providerId: String,
    /** 使用的模型名称 */
    val model: String,
    /** 该对话使用的系统提示词 */
    val systemPrompt: String = "You are a helpful assistant.",
    /** 关联的工作区路径（Agent 模式），空字符串表示 Chat 模式 */
    val workspacePath: String = "",
    /** 创建时间戳（毫秒） */
    val createdAt: Long,
    /** 最后更新时间戳（毫秒） */
    val updatedAt: Long
)
