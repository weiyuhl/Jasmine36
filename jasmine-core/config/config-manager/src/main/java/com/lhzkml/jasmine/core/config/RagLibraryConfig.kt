package com.lhzkml.jasmine.core.config

/**
 * RAG 知识库配置
 * 用于区分不同用途的知识库（如项目文档、个人笔记、API 文档）
 * @param id 唯一标识，如 "project_docs", "personal_notes"
 * @param name 显示名称
 * @param description 用途描述（可选）
 */
data class RagLibraryConfig(
    val id: String,
    val name: String,
    val description: String = ""
)
