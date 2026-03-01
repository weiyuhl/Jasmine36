# MainActivity MVVM 完全重构计划

## 目标架构

```
UI Layer (Activity/Fragment)
    ↓
ViewModel Layer (状态管理)
    ↓
Repository Layer (数据访问)
    ↓
Data Source Layer (本地/远程数据)
```

## 阶段划分

### 阶段 1：基础架构搭建
1. 添加依赖（ViewModel, LiveData/Flow, Coroutines）
2. 创建基础 Repository 接口
3. 创建基础 ViewModel 基类

### 阶段 2：数据层重构
1. `ConversationRepository` - 对话管理
2. `MessageRepository` - 消息管理
3. `ConfigRepository` - 配置管理（已存在，需要适配）
4. `ToolRepository` - 工具管理

### 阶段 3：ViewModel 层创建
1. `ChatViewModel` - 聊天主界面状态
2. `ConversationListViewModel` - 对话列表
3. `MessageSendViewModel` - 消息发送逻辑
4. `AgentExecutionViewModel` - Agent 执行状态

### 阶段 4：UI 层重构
1. MainActivity 简化为 UI 协调器
2. 使用 ViewBinding
3. 观察 ViewModel 状态更新 UI

### 阶段 5：测试和优化
1. 单元测试
2. 集成测试
3. 性能优化

## 详细实施步骤

### 步骤 1：添加依赖

在 `app/build.gradle.kts` 中添加：
```kotlin
dependencies {
    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    
    // Activity KTX for viewModels()
    implementation("androidx.activity:activity-ktx:1.8.0")
    
    // Fragment KTX (if needed)
    implementation("androidx.fragment:fragment-ktx:1.6.2")
}
```

### 步骤 2：创建 Repository 层

#### 2.1 MessageRepository
```kotlin
interface MessageRepository {
    suspend fun sendMessage(
        message: String,
        conversationId: String?,
        config: ActiveProviderConfig
    ): Result<MessageSendResult>
    
    fun observeMessageStream(): Flow<MessageStreamEvent>
}
```

#### 2.2 ConversationRepository（扩展现有）
已存在，需要添加 Flow 支持

### 步骤 3：创建 ViewModel 层

#### 3.1 ChatViewModel
```kotlin
class ChatViewModel(
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Idle)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    fun sendMessage(text: String) {
        viewModelScope.launch {
            _uiState.value = ChatUiState.Sending
            messageRepository.sendMessage(text, currentConversationId, config)
                .onSuccess { result ->
                    _messages.value += result.message
                    _uiState.value = ChatUiState.Idle
                }
                .onFailure { error ->
                    _uiState.value = ChatUiState.Error(error.message ?: "Unknown error")
                }
        }
    }
}
```

#### 3.2 ConversationListViewModel
```kotlin
class ConversationListViewModel(
    private val conversationRepository: ConversationRepository
) : ViewModel() {
    
    val conversations: StateFlow<List<ConversationInfo>> = 
        conversationRepository.observeConversations()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    fun createConversation(title: String) {
        viewModelScope.launch {
            conversationRepository.createConversation(title, ...)
        }
    }
}
```

### 步骤 4：UI 状态定义

```kotlin
sealed class ChatUiState {
    object Idle : ChatUiState()
    object Sending : ChatUiState()
    data class Streaming(val content: String) : ChatUiState()
    data class Error(val message: String) : ChatUiState()
    data class AgentExecuting(val status: AgentStatus) : ChatUiState()
}

data class AgentStatus(
    val currentTool: String?,
    val progress: String,
    val logs: List<String>
)
```

### 步骤 5：MainActivity 重构

```kotlin
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val chatViewModel: ChatViewModel by viewModels()
    private val conversationListViewModel: ConversationListViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        observeViewModels()
    }
    
    private fun setupUI() {
        binding.btnSend.setOnClickListener {
            val text = binding.etInput.text.toString()
            if (text.isNotBlank()) {
                chatViewModel.sendMessage(text)
                binding.etInput.text.clear()
            }
        }
    }
    
    private fun observeViewModels() {
        lifecycleScope.launch {
            chatViewModel.uiState.collect { state ->
                when (state) {
                    is ChatUiState.Idle -> updateButtonState(ButtonState.IDLE)
                    is ChatUiState.Sending -> updateButtonState(ButtonState.GENERATING)
                    is ChatUiState.Streaming -> appendText(state.content)
                    is ChatUiState.Error -> showError(state.message)
                    is ChatUiState.AgentExecuting -> updateAgentStatus(state.status)
                }
            }
        }
        
        lifecycleScope.launch {
            chatViewModel.messages.collect { messages ->
                renderMessages(messages)
            }
        }
    }
}
```

## 实施顺序

1. ✅ 创建详细计划（当前步骤）
2. ⬜ 添加依赖到 build.gradle.kts
3. ⬜ 创建 UI 状态类（ChatUiState, MessageStreamEvent 等）
4. ⬜ 创建 MessageRepository 接口和实现
5. ⬜ 创建 ChatViewModel
6. ⬜ 创建 ConversationListViewModel
7. ⬜ 重构 MainActivity 使用 ViewModel
8. ⬜ 测试基本功能
9. ⬜ 迁移 Agent 模式逻辑
10. ⬜ 迁移流式输出逻辑
11. ⬜ 迁移错误处理和恢复
12. ⬜ 完整测试
13. ⬜ 性能优化
14. ⬜ 代码清理

## 预期成果

- MainActivity: ~400 行（从 ~2000 行）
- 新增 ViewModel: 4-5 个类，每个 200-300 行
- 新增 Repository: 2-3 个类，每个 150-200 行
- 总代码量可能略有增加，但结构清晰，易于维护和测试

## 注意事项

1. 保持向后兼容，逐步迁移
2. 每个步骤完成后运行测试
3. 使用 Git 分支管理，便于回滚
4. 优先迁移核心功能，次要功能后续处理
5. 文档同步更新

## 开始执行

准备好了吗？我将从步骤 2 开始执行。
