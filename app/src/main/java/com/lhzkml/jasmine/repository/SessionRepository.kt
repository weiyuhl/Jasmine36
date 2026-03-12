package com.lhzkml.jasmine.repository

import android.net.Uri
import com.lhzkml.jasmine.core.config.ConfigRepository

/**
 * Session Repository
 *
 * 负责：
 * - isAgentMode
 * - setAgentMode
 * - workspacePath
 * - workspaceUri
 * - lastConversationId
 * - lastSession
 *
 * 对应页面/功能：
 * - LauncherActivity
 * - ChatViewModel 中工作区恢复/退出工作区
 *
 * 说明：
 * - 文件选择器、权限请求、跳系统设置页不属于 Repository，属于 View 层 effect
 */
interface SessionRepository {
    
    // Agent Mode
    fun isAgentMode(): Boolean
    fun setAgentMode(enabled: Boolean)
    
    // Workspace
    fun getWorkspacePath(): String
    fun setWorkspacePath(path: String)
    fun getWorkspaceUri(): String
    fun setWorkspaceUri(uri: String)
    
    /**
     * 解析 Uri 为可读路径
     */
    fun parseWorkspaceUri(uri: Uri): String
    
    // Last Session
    fun hasLastSession(): Boolean
    fun setLastSession(hasSession: Boolean)
    fun getLastConversationId(): String?
    fun setLastConversationId(id: String?)
}

class DefaultSessionRepository(
    private val configRepo: ConfigRepository
) : SessionRepository {
    
    override fun isAgentMode(): Boolean = configRepo.isAgentMode()
    
    override fun setAgentMode(enabled: Boolean) {
        configRepo.setAgentMode(enabled)
    }
    
    override fun getWorkspacePath(): String = configRepo.getWorkspacePath()
    
    override fun setWorkspacePath(path: String) {
        configRepo.setWorkspacePath(path)
    }
    
    override fun getWorkspaceUri(): String = configRepo.getWorkspaceUri()
    
    override fun setWorkspaceUri(uri: String) {
        configRepo.setWorkspaceUri(uri)
    }
    
    override fun parseWorkspaceUri(uri: Uri): String {
        // 简单解析，实际可能需要更复杂的逻辑
        val path = uri.path?.substringAfterLast(':')
        return path ?: uri.toString()
    }
    
    override fun hasLastSession(): Boolean = configRepo.hasLastSession()
    
    override fun setLastSession(hasSession: Boolean) {
        configRepo.setLastSession(hasSession)
    }
    
    override fun getLastConversationId(): String? = configRepo.getLastConversationId()
    
    override fun setLastConversationId(id: String?) {
        if (id != null) {
            configRepo.setLastConversationId(id)
        }
    }
}
