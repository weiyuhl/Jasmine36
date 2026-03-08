package com.lhzkml.jasmine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lhzkml.jasmine.core.conversation.storage.ConversationInfo
import com.lhzkml.jasmine.core.conversation.storage.ConversationRepository
import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 对话管理 ViewModel
 * 
 * 职责：
 * - 对话的创建、加载、删除
 * - 对话列表管理
 * - 消息历史管理
 * 
 * 从原 ChatViewModel 中拆分出来，遵循单一职责原则
 */
class ConversationViewModel(
    private val repository: ConversationRepository,
    private val workspacePath: String = ""
) : ViewModel() {

    // 对话列表
    private val _conversations = MutableStateFlow<List<ConversationInfo>>(emptyList())
    val conversations: StateFlow<List<ConversationInfo>> = _conversations.asStateFlow()

    // 当前对话 ID
    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId.asStateFlow()

    // 消息历史
    private val _messageHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messageHistory: StateFlow<List<ChatMessage>> = _messageHistory.asStateFlow()

    init {
        observeConversations()
    }

    /**
     * 监听对话列表变化
     */
    private fun observeConversations() {
        viewModelScope.launch {
            repository.observeConversationsByWorkspace(workspacePath)
                .collectLatest { list ->
                    _conversations.value = list
                }
        }
    }

    /**
     * 创建新对话
     */
    fun createConversation(
        title: String,
        providerId: String,
        model: String,
        systemPrompt: String
    ) {
        viewModelScope.launch {
            val id = repository.createConversation(
                title = title,
                providerId = providerId,
                model = model,
                systemPrompt = systemPrompt,
                workspacePath = workspacePath
            )
            _currentConversationId.value = id
            
            // 添加系统消息到历史
            val systemMsg = ChatMessage.system(systemPrompt)
            _messageHistory.value = listOf(systemMsg)
            repository.addMessage(id, systemMsg)
        }
    }

    /**
     * 加载对话
     */
    fun loadConversation(id: String) {
        viewModelScope.launch {
            val messages = repository.getMessages(id)
            _messageHistory.value = messages.filter { it.role != "agent_log" }
            _currentConversationId.value = id
        }
    }

    /**
     * 删除对话
     */
    fun deleteConversation(id: String) {
        viewModelScope.launch {
            repository.deleteConversation(id)
            if (_currentConversationId.value == id) {
                clearCurrentConversation()
            }
        }
    }

    /**
     * 清除当前对话（开始新对话）
     */
    fun clearCurrentConversation() {
        _currentConversationId.value = null
        _messageHistory.value = emptyList()
    }

    /**
     * 添加消息到历史
     */
    fun addMessage(message: ChatMessage) {
        viewModelScope.launch {
            val currentId = _currentConversationId.value
            if (currentId != null) {
                _messageHistory.value = _messageHistory.value + message
                repository.addMessage(currentId, message)
            }
        }
    }

    /**
     * 更新系统提示词
     */
    fun updateSystemPrompt(systemPrompt: String) {
        val history = _messageHistory.value.toMutableList()
        if (history.isNotEmpty() && history[0].role == "system") {
            history[0] = ChatMessage.system(systemPrompt)
            _messageHistory.value = history
        }
    }

    /**
     * 获取对话信息
     */
    suspend fun getConversationInfo(id: String): ConversationInfo? {
        return repository.getConversation(id)
    }
}
