# Jasmine 向量 RAG 知识库开发计划

基于 ObjectBox + 本地/远程 embedding 实现向量 RAG 知识库。

---

## 一、项目现状分析

### 1.1 现有架构

| 模块 | 技术栈 | 职责 |
|------|--------|------|
| **conversation-storage** | Room + KSP | 对话与消息持久化（ConversationEntity, MessageEntity） |
| **prompt-llm** | 自定义 | SystemContextProvider 注入上下文、SystemContextCollector 收集 |
| **ChatExecutor** | app 层 | 构建 system prompt → trim messages → 调用 LLM |
| **agent-tools** | 工具 | read_file、list_directory、search_by_regex、find_files 等 |
| **MNN** | 本地推理 | 已集成本地 LLM，可扩展 embedding |
| **ConfigRepository** | SharedPreferences | 配置持久化 |

### 1.2 关键注入点

RAG 检索结果需注入到 **system prompt** 中。当前流程：

```
ChatExecutor.execute()
  → contextCollector().buildSystemPrompt(basePrompt)
  → SystemContextCollector 遍历所有 SystemContextProvider
  → 各 Provider 返回 getContextSection()
  → 拼接到 basePrompt 末尾
```

**最佳接入方式**：新增 `RagContextProvider implements SystemContextProvider`，在 `getContextSection()` 中根据用户消息做向量检索，返回 `<rag_context>相关文档...</rag_context>`。

---

## 二、技术选型

### 2.1 向量数据库：ObjectBox 4.x

- **ObjectBox 4.0+** 支持 `@HnswIndex(dimensions=N)` 的向量索引
- HNSW 算法，支持百万级向量毫秒级检索
- **纯本地**，数据不出设备
- 与现有 Room 并存：Room 管对话，ObjectBox 管知识库

```kotlin
@Entity
data class KnowledgeChunk(
    @Id var id: Long = 0,
    var sourceId: String = "",      // 知识源 ID（如文件路径、文档 ID）
    var content: String = "",      // 原始文本
    var metadata: String = "{}",  // JSON：路径、行号、时间等
    @HnswIndex(dimensions = 384) var embedding: FloatArray? = null
)
```

### 2.2 向量生成：三种方案

| 方案 | 优点 | 缺点 | 推荐场景 |
|------|------|------|----------|
| **A. 远程 API** | 易集成、效果好 | 需网络、有延迟、隐私顾虑 | 快速验证、非敏感场景 |
| **B. 本地 MNN** | 与现有 MNN 一致、纯离线 | 需适配 MNN embedding 模型 | 强调隐私、离线优先 |
| **C. MediaPipe/ONNX** | Google 官方、生态成熟 | 新增依赖、模型体积 | 通用推荐 |

**建议**：先实现 **A（远程 API）** 快速跑通 RAG；预留 **B/C** 接口，后续可切换为本地 embedding。

### 2.3 Embedding 维度

- **OpenAI text-embedding-3-small**：1536 维
- **all-MiniLM-L6-V2**（本地常用）：384 维
- **bge-small / snowflake-arctic-embed**：512 / 384 维

ObjectBox `dimensions` 需与模型保持一致，建议先用 **384** 以便兼容多种本地模型。

---

## 三、模块划分

### 3.1 新建 jasmine-core 模块

```
jasmine-core/rag/
├── rag-core/           # 核心抽象
│   ├── RagContextProvider.kt    # SystemContextProvider 实现
│   ├── KnowledgeChunk.kt       # 知识块模型（不含 ObjectBox 注解，供接口使用）
│   ├── EmbeddingService.kt     # 接口：文本 → 向量
│   ├── KnowledgeIndex.kt       # 接口：增删查知识
│   └── RagConfig.kt            # 启用开关、topK、阈值等
├── rag-objectbox/      # ObjectBox 实现（Android）
│   ├── KnowledgeChunkEntity.kt # @Entity + @HnswIndex
│   ├── ObjectBoxKnowledgeIndex.kt
│   └── ObjectBox初始化
└── rag-embedding-api/  # 远程 Embedding 实现
    └── ApiEmbeddingService.kt  # 调用 OpenAI/DeepSeek 等 embedding API
```

### 3.2 App 层职责

- **RagConfigActivity**：知识库管理 UI（添加/删除知识源、索引状态、触发重建）
- **知识源类型**：
  - 工作区文件（基于 workspacePath + glob 扫描）
  - 手动粘贴文本
  - 可选：URL 抓取、PDF 解析（后期）
- **ConfigRepository** 扩展：`isRagEnabled`、`getRagTopK`、`getRagThreshold`、`getRagKnowledgeSourceIds`

---

## 四、数据流设计

### 4.1 索引流程（写入）

```
用户添加知识源（目录/文件/文本）
  → 分块（按段落/按固定 token、带重叠）
  → EmbeddingService.embed(text) → FloatArray
  → KnowledgeIndex.insert(chunk)
  → ObjectBox 自动建 HNSW 索引
```

### 4.2 检索流程（读取）

```
用户发送消息
  → RagContextProvider.getContextSection()
  → EmbeddingService.embed(userMessage)
  → KnowledgeIndex.search(queryVector, topK)
  → 格式化 Top-K chunks 为 XML
  → 返回 "<rag_context>...</rag_context>"
  → 注入 system prompt
```

### 4.3 分块策略

- **代码文件**：按函数/类边界分块，或按固定行数（如 50 行）
- **文档/文本**：按段落，或按 256/512 token 滑动窗口，overlap 50 token
- **元数据**：sourceId（文件路径）、startLine、endLine、chunkIndex

---

## 五、实施阶段

### 阶段 1：基础设施（约 1–2 周）

1. **引入 ObjectBox**
   - 添加 `io.objectbox:objectbox-kotlin` 依赖
   - 配置 ObjectBox Gradle 插件与 `objectbox-model` 生成
   - 在 JasmineApplication 中初始化 `ObjectBox.init()`

2. **新建 jasmine-core/rag 模块**
   - `rag-core`：接口与抽象，依赖 `prompt-llm`（SystemContextProvider）
   - `rag-objectbox`：ObjectBox 实现，依赖 `rag-core`
   - `rag-embedding-api`：远程 API 实现，依赖 `rag-core`、`prompt-llm`（ChatClient）

3. **定义实体与接口**
   - `KnowledgeChunkEntity`（ObjectBox Entity + HnswIndex）
   - `EmbeddingService`、`KnowledgeIndex` 接口

4. **实现 ApiEmbeddingService**
   - 使用现有 ChatClient/Provider 配置调用 embedding 接口
   - 支持 OpenAI、DeepSeek 等（需在 ConfigRepository 中配置 embedding 端点）

### 阶段 2：RAG 核心逻辑（约 1 周）

5. **实现 ObjectBoxKnowledgeIndex**
   - insert、deleteBySourceId、search(queryVector, topK)
   - 使用 `City_.location.nearestNeighbors(query, topK)` 模式

6. **实现 RagContextProvider**
   - 实现 `SystemContextProvider`
   - 若 `!isRagEnabled` 或 `messageHistory` 为空，返回 null
   - 取最后一条 user 消息 → embed → search → 格式化 → 返回

7. **分块与索引服务**
   - `ChunkingStrategy`：按行/按 token 分块
   - `IndexingService`：遍历文件 → 分块 → embed → insert
   - 后台协程执行，避免阻塞 UI

### 阶段 3：UI 与配置（约 1 周）

8. **ConfigRepository 扩展**
   - `isRagEnabled`、`setRagEnabled`
   - `getRagTopK`、`setRagTopK`（默认 5）
   - `getRagKnowledgeSourcePath`、`setRagKnowledgeSourcePath`（工作区子路径，空=全部）
   - `getRagEmbeddingProvider`（用于切换远程/本地，初期仅远程）

9. **RagConfigActivity**
   - 开关：启用/关闭 RAG
   - 知识源选择：当前工作区 / 指定子目录
   - 按钮：立即重建索引
   - 显示：已索引文件数、chunk 数、最后索引时间

10. **MainActivity / ChatViewModel 注册 RagContextProvider**
    - 在构建 `SystemContextCollector` 时，若 RAG 启用则注册 `RagContextProvider`

### 阶段 4：优化与扩展（可选）

11. **增量索引**
    - 监听工作区文件变更，只对新增/修改文件重新分块与索引
    - 删除文件时调用 `deleteBySourceId`

12. **本地 Embedding**
    - 集成 MediaPipe Text Embedder 或 ONNX 版 sentence-transformers
    - 实现 `LocalEmbeddingService implements EmbeddingService`
    - 配置中增加「使用本地 embedding」选项

13. **多知识源**
    - 支持多个知识库（如「项目文档」「个人笔记」）
    - Entity 增加 `knowledgeBaseId` 字段，检索时按库过滤

---

## 六、依赖与版本

### 6.1 ObjectBox

```kotlin
// build.gradle.kts (project)
plugins {
    id("io.objectbox.objectbox") version "4.3.0"  // 或最新 4.x
}

// build.gradle.kts (rag-objectbox module)
plugins {
    id("io.objectbox.objectbox")
}
dependencies {
    implementation("io.objectbox:objectbox-kotlin:4.3.0")
}
```

### 6.2 与 Room 并存

- Room 用于 `conversation-storage`，不做改动
- ObjectBox 仅用于 RAG 知识库
- 两者独立，无冲突

---

## 七、风险与注意事项

1. **ObjectBox 需在 Application 中初始化**，且依赖 Android Context，`rag-objectbox` 需为 Android library。
2. **Embedding 维度固定**：索引时所用模型的 dimensions 必须一致，更换模型需重建索引。
3. **Token 消耗**：RAG 注入会增加 system prompt 长度，需在 ContextManager 的 token 预算中预留空间。
4. **并发**：索引重建与检索可能并发，ObjectBox 读多写少场景表现良好，写入时注意避免长时间锁。
5. **首次索引**：大工作区首次全量索引可能较慢，需在 UI 提示「索引中」并允许取消。

---

## 八、文件变更预估

| 类型 | 路径 | 说明 |
|------|------|------|
| 新增 | `jasmine-core/rag/rag-core/` | 接口、模型、RagContextProvider |
| 新增 | `jasmine-core/rag/rag-objectbox/` | ObjectBox 实体与 KnowledgeIndex 实现 |
| 新增 | `jasmine-core/rag/rag-embedding-api/` | 远程 EmbeddingService |
| 新增 | `app/.../RagConfigActivity.kt` | 知识库管理页 |
| 修改 | `settings.gradle.kts` | 添加 rag 模块 |
| 修改 | `app/build.gradle.kts` | 依赖 rag、ObjectBox 插件 |
| 修改 | `ConfigRepository` | RAG 相关配置项 |
| 修改 | `JasmineApplication` | ObjectBox.init |
| 修改 | `ChatViewModel` / `SystemContextCollector` 注册处 | 注册 RagContextProvider |

---

## 九、方案审查结论

### 9.1 设计缺口（需修正）

| 问题 | 说明 | 建议修正 |
|------|------|----------|
| **Provider 无法获取当前用户消息** | `SystemContextProvider.getContextSection()` 无参数，而 RAG 检索依赖用户输入做 query embedding | 扩展接口：`getContextSection(context: SystemContext?): String?`，其中 `SystemContext` 含 `currentUserMessage`；或由 `buildSystemPrompt` 增加可选 `query` 参数并传入 |
| **同步接口 vs 异步操作** | `getContextSection()` 是同步 `fun`，而 embedding API 调用为 `suspend` | 方案 1：接口改为 `suspend fun getContextSection()`；方案 2：在 `buildSystemPrompt` 调用前于 ChatExecutor 中 `runBlocking`/`withContext` 完成 RAG 检索，将结果注入 RagContextProvider 的可变状态，再由同步 `getContextSection()` 返回 |
| **ChatClient ≠ Embedding API** | 计划写“使用 ChatClient 调用 embedding”，但 `ChatClient` 仅支持 chat 接口，无 embedding 能力 | 新建 `EmbeddingClient` 或 `EmbeddingService`，复用 Provider 的 baseUrl、apiKey，单独调用 `/v1/embeddings` 等端点；与 ChatClient 并列，不耦合 |

### 9.2 推荐修正后的接入流程

```
ChatExecutor.execute(message, userMsg, ...)
  → refreshContextCollector()
  → [新增] 若 RAG 启用：runBlocking/withContext 执行
      embed(message) → search(topK) → formattedChunks
  → [修正] buildSystemPrompt(basePrompt, ragContext = formattedChunks)
      // 或：RagContextProvider 持有 ragContext，由 ChatExecutor 在调用前注入
  → 后续流程不变
```

或更彻底的改法：`SystemContextProvider` 增加 `suspend fun getContextSection(query: String?): String?`，`SystemContextCollector.buildSystemPrompt` 改为 `suspend` 并传入 query，由 Collector 负责协程调度。

### 9.3 其他审阅意见

- **ObjectBox 选型**：合适，文档齐全，与 Room 无冲突。
- **rag-core 依赖 prompt-llm**：合理，仅依赖 `SystemContextProvider` 接口。
- **rag-objectbox 需 Android library**：正确，ObjectBox 依赖 Android Context。
- **384 维与远程 API**：若先用 OpenAI embedding，维度为 1536，与 384 不兼容；需明确：要么固定用 384 维模型（含远程兼容 384 的），要么将 dimensions 做成可配置并在切换时强制重建索引。
- **ConfigRepository 职责**：RAG 配置集中在 Config 层合理；需新增 `getEmbeddingModel()` 或 `getRagEmbeddingEndpoint()` 以支持多供应商。
- **Agent 模式下 workspacePath**：RAG 知识源默认绑定 workspace 合理；未设置 workspace 时（纯 Chat 模式）可支持“手动添加的文档”类知识源。

### 9.4 总体评价

方案整体可行，ObjectBox + SystemContextProvider 接入思路正确。实施前需解决上述三个设计缺口（用户消息传递、同步/异步、Embedding 客户端独立实现），修正后再按阶段推进可降低返工风险。

---

## 十、总结

本计划采用 **ObjectBox 向量库 + 远程/本地 Embedding** 实现 RAG，通过 **SystemContextProvider** 接入现有对话流程。建议先按第九章完成设计修正，再按阶段 1–3 推进；阶段 4 的优化与本地化可视需求迭代。
