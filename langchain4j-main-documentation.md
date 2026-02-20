# LangChain4j 详细文档

> 版本: 1.12.0-beta20-SNAPSHOT
> 语言: Java (JDK 17+, 部分模块需要 JDK 21+)
> 构建系统: Maven
> 模块总数: ~75 个

---

## 1. 项目概述

LangChain4j 是一个面向 Java 生态的通用 LLM 应用框架，提供从模型抽象、工具调用、RAG 管道、Agent 编排到 MCP 协议的完整能力栈。其设计哲学是 SPI 插件化 + 注解驱动，通过声明式 API 最大限度减少样板代码。

核心特点:
- 声明式 Agent: 通过 @Tool / @Agent 注解 + AiServices 代理自动生成工具描述和执行逻辑
- 多 Agent 工作流: Loop / Sequence / Parallel / Conditional / Supervisor 五种编排模式
- 完整 RAG 管道: DocumentLoader -> DocumentSplitter -> EmbeddingModel -> EmbeddingStore -> ContentRetriever -> RetrievalAugmentor
- MCP 协议: 4 种传输 (HTTP/SSE/Stdio/WebSocket)，支持工具过滤/映射/资源转工具
- 20+ 供应商适配: OpenAI / Anthropic / Gemini / Bedrock / Ollama / Mistral 等
- 20+ 向量存储: PGVector / Milvus / Pinecone / Qdrant / Elasticsearch / Chroma 等
- Guardrails: 输入/输出安全护栏机制

---

## 2. 模块架构

### 2.1 核心模块

| 模块 | 说明 |
|------|------|
| `langchain4j-parent` | 父 POM，统一依赖版本管理 |
| `langchain4j-bom` | BOM (Bill of Materials)，供下游项目统一版本 |
| `langchain4j-core` | 核心抽象层: 模型接口、消息类型、工具规范、RAG、Guardrails、Embedding、Document |
| `langchain4j` | 主模块: AiServices、ToolService、分类、ChatMemory 实现 |
| `langchain4j-test` | 测试工具模块 |
| `langchain4j-kotlin` | Kotlin 扩展支持 |
| `langchain4j-easy-rag` | 简化 RAG 配置的便捷模块 |

### 2.2 Agent 模块

| 模块 | 说明 |
|------|------|
| `langchain4j-agentic` | Agent 框架: @Agent 注解、声明式 Agent (Loop/Sequence/Parallel/Conditional/Supervisor)、工作流编排、可观测性 (AgentListener/AgentMonitor/HtmlReportGenerator)、Scope 持久化 |
| `langchain4j-agentic-patterns` | Agent 模式: GOAP (目标导向行动规划)、P2P (点对点 Agent 通信) |
| `langchain4j-agentic-a2a` | A2A 协议: Agent-to-Agent 远程调用，基于 AgentCard 发现和调用远程 Agent |

### 2.3 MCP 模块

| 模块 | 说明 |
|------|------|
| `langchain4j-mcp` | MCP 客户端: McpClient 接口、4 种传输 (HTTP/SSE/Stdio/WebSocket)、McpToolProvider (动态工具提供+过滤+映射)、ResourcesAsTools (MCP 资源转工具)、Registry Client |
| `langchain4j-mcp-docker` | MCP Docker 集成 |

### 2.4 HTTP 客户端模块

| 模块 | 说明 |
|------|------|
| `langchain4j-http-client` | HTTP 客户端抽象层 |
| `langchain4j-http-client-jdk` | 基于 JDK HttpClient 的实现 |
| `langchain4j-http-client-apache` | 基于 Apache HttpClient 的实现 |

### 2.5 模型供应商模块

| 模块 | 供应商 |
|------|--------|
| `langchain4j-open-ai` | OpenAI (社区实现) |
| `langchain4j-open-ai-official` | OpenAI (官方 SDK) |
| `langchain4j-anthropic` | Anthropic Claude |
| `langchain4j-google-ai-gemini` | Google AI Gemini |
| `langchain4j-vertex-ai` | Google Vertex AI (PaLM) |
| `langchain4j-vertex-ai-gemini` | Google Vertex AI Gemini |
| `langchain4j-vertex-ai-anthropic` | Google Vertex AI Anthropic |
| `langchain4j-azure-open-ai` | Azure OpenAI |
| `langchain4j-bedrock` | AWS Bedrock |
| `langchain4j-ollama` | Ollama (本地模型) |
| `langchain4j-mistral-ai` | Mistral AI |
| `langchain4j-hugging-face` | Hugging Face |
| `langchain4j-cohere` | Cohere |
| `langchain4j-jina` | Jina AI |
| `langchain4j-nomic` | Nomic |
| `langchain4j-voyage-ai` | Voyage AI |
| `langchain4j-watsonx` | IBM Watsonx |
| `langchain4j-workers-ai` | Cloudflare Workers AI |
| `langchain4j-ovh-ai` | OVH AI |
| `langchain4j-local-ai` | LocalAI |
| `langchain4j-github-models` | GitHub Models |
| `langchain4j-jlama` | JLama (JDK 21+, 本地推理) |
| `langchain4j-gpu-llama3` | GPU Llama3 (JDK 21+, 本地 GPU 推理) |
| `langchain4j-onnx-scoring` | ONNX 评分模型 |

### 2.6 向量存储 / 聊天记忆存储模块

| 模块 | 存储后端 |
|------|----------|
| `langchain4j-pgvector` | PostgreSQL PGVector |
| `langchain4j-milvus` | Milvus |
| `langchain4j-pinecone` | Pinecone |
| `langchain4j-qdrant` | Qdrant |
| `langchain4j-chroma` | Chroma |
| `langchain4j-elasticsearch` | Elasticsearch |
| `langchain4j-opensearch` | OpenSearch |
| `langchain4j-weaviate` | Weaviate |
| `langchain4j-mongodb-atlas` | MongoDB Atlas |
| `langchain4j-cassandra` | Apache Cassandra |
| `langchain4j-coherence` | Oracle Coherence |
| `langchain4j-couchbase` | Couchbase |
| `langchain4j-infinispan` | Infinispan |
| `langchain4j-mariadb` | MariaDB |
| `langchain4j-oracle` | Oracle Database |
| `langchain4j-tablestore` | Alibaba Tablestore |
| `langchain4j-vespa` | Vespa |
| `langchain4j-azure-ai-search` | Azure AI Search |
| `langchain4j-azure-cosmos-mongo-vcore` | Azure Cosmos DB (MongoDB vCore) |
| `langchain4j-azure-cosmos-nosql` | Azure Cosmos DB (NoSQL) |

### 2.7 本地嵌入模型模块

| 模块 | 模型 |
|------|------|
| `langchain4j-embeddings` | 嵌入模型抽象层 |
| `langchain4j-embeddings-all-minilm-l6-v2` | all-MiniLM-L6-v2 |
| `langchain4j-embeddings-all-minilm-l6-v2-q` | all-MiniLM-L6-v2 (量化) |
| `langchain4j-embeddings-bge-small-en` | BGE-small-en |
| `langchain4j-embeddings-bge-small-en-q` | BGE-small-en (量化) |
| `langchain4j-embeddings-bge-small-en-v15` | BGE-small-en-v1.5 |
| `langchain4j-embeddings-bge-small-en-v15-q` | BGE-small-en-v1.5 (量化) |
| `langchain4j-embeddings-bge-small-zh-v15` | BGE-small-zh-v1.5 (中文) |
| `langchain4j-embeddings-bge-small-zh-v15-q` | BGE-small-zh-v1.5 (中文量化) |
| `langchain4j-embeddings-e5-small-v2` | E5-small-v2 |
| `langchain4j-embeddings-e5-small-v2-q` | E5-small-v2 (量化) |

### 2.8 文档处理模块

| 模块 | 说明 |
|------|------|
| `langchain4j-document-loader-amazon-s3` | 从 Amazon S3 加载文档 |
| `langchain4j-document-loader-azure-storage-blob` | 从 Azure Blob Storage 加载文档 |
| `langchain4j-document-loader-github` | 从 GitHub 仓库加载文档 |
| `langchain4j-document-loader-selenium` | 通过 Selenium 加载网页文档 |
| `langchain4j-document-loader-playwright` | 通过 Playwright 加载网页文档 |
| `langchain4j-document-loader-tencent-cos` | 从腾讯云 COS 加载文档 |
| `langchain4j-document-loader-google-cloud-storage` | 从 Google Cloud Storage 加载文档 |
| `langchain4j-document-parser-apache-pdfbox` | PDF 解析 (Apache PDFBox) |
| `langchain4j-document-parser-apache-poi` | Office 文档解析 (Apache POI) |
| `langchain4j-document-parser-apache-tika` | 通用文档解析 (Apache Tika) |
| `langchain4j-document-parser-markdown` | Markdown 解析 |
| `langchain4j-document-parser-yaml` | YAML 解析 |
| `langchain4j-document-transformer-jsoup` | HTML 文档转换 (Jsoup) |

### 2.9 其他基础设施模块

| 模块 | 说明 |
|------|------|
| `langchain4j-code-execution-engine-graalvm-polyglot` | GraalVM Polyglot 代码执行引擎 |
| `langchain4j-code-execution-engine-judge0` | Judge0 代码执行引擎 |
| `langchain4j-code-execution-engine-azure-acads` | Azure ACADS 代码执行引擎 |
| `langchain4j-web-search-engine-google-custom` | Google Custom Search |
| `langchain4j-web-search-engine-tavily` | Tavily 搜索 |
| `langchain4j-web-search-engine-searchapi` | SearchAPI 搜索 |
| `langchain4j-embedding-store-filter-parser-sql` | SQL 语法的 Embedding Store 过滤器解析 |
| `langchain4j-guardrails` | 内置 Guardrails 实现 (JsonExtractorOutputGuardrail, MessageModeratorInputGuardrail) |
| `langchain4j-experimental-sql` | 实验性 SQL 模块 |

---

## 3. 核心抽象层 (langchain4j-core)

### 3.1 模型接口体系

#### ChatModel / StreamingChatModel

核心聊天模型接口，所有供应商模块都实现这两个接口。

```java
public interface ChatModel {
    // 主 API: 发送 ChatRequest，返回 ChatResponse
    ChatResponse chat(ChatRequest chatRequest);

    // 便捷方法
    String chat(String userMessage);
    ChatResponse chat(ChatMessage... messages);
    ChatResponse chat(List<ChatMessage> messages);

    // 模型能力声明
    Set<Capability> supportedCapabilities();

    // 默认请求参数 (temperature/topP/maxTokens 等)
    ChatRequestParameters defaultRequestParameters();

    // 监听器
    List<ChatModelListener> listeners();

    // 供应商标识
    ModelProvider provider();
}
```

ChatModel 内置了监听器机制: 每次 chat() 调用会自动触发 `onRequest` / `onResponse` / `onError` 回调。

#### ChatRequest / ChatResponse

```java
// 请求: 消息列表 + 参数
ChatRequest.builder()
    .messages(userMessage)
    .parameters(ChatRequestParameters.builder()
        .temperature(0.7)
        .topP(0.9)
        .maxOutputTokens(4096)
        .toolSpecifications(tools)
        .build())
    .build();

// 响应: AI 消息 + 元数据 (token 用量、finish reason 等)
ChatResponse response = chatModel.chat(request);
AiMessage aiMessage = response.aiMessage();
TokenUsage tokenUsage = response.metadata().tokenUsage();
```

#### Capability 枚举

声明模型支持的能力:
- `RESPONSE_FORMAT_JSON_SCHEMA` - 结构化 JSON 输出
- 其他供应商特定能力

#### ChatModelListener

模型层监听器，可监控所有 LLM 调用:

```java
public interface ChatModelListener {
    void onRequest(ChatModelRequestContext requestContext);
    void onResponse(ChatModelResponseContext responseContext);
    void onError(ChatModelErrorContext errorContext);
}
```

### 3.2 消息类型体系

```
ChatMessage (接口)
  |-- SystemMessage        系统消息
  |-- UserMessage          用户消息 (支持多模态 Content)
  |-- AiMessage            AI 回复 (text + toolExecutionRequests)
  |-- ToolExecutionResultMessage  工具执行结果
  |-- CustomMessage        自定义消息 (Map<String, Object> attributes)
```

Content 类型 (UserMessage 内的多模态内容):
- `TextContent` - 文本
- `ImageContent` - 图片 (URL 或 Base64)
- `AudioContent` - 音频
- `VideoContent` - 视频
- `PdfFileContent` - PDF 文件

ChatMessageType 枚举: `SYSTEM`, `USER`, `AI`, `TOOL_EXECUTION_RESULT`, `CUSTOM`

消息序列化: `ChatMessageSerializer` / `ChatMessageDeserializer` 支持 JSON 序列化/反序列化，通过 SPI 加载 `ChatMessageJsonCodec` 实现。

### 3.3 工具系统

#### ToolSpecification

描述一个 LLM 可调用的工具:

```java
ToolSpecification.builder()
    .name("get_weather")
    .description("获取指定城市的天气")
    .parameters(JsonObjectSchema.builder()
        .addStringProperty("city", "城市名称")
        .required("city")
        .build())
    .metadata(Map.of("cache_control", "ephemeral"))  // 供应商特定元数据
    .build();
```

字段:
- `name` - 工具名称
- `description` - 工具描述
- `parameters` - JSON Schema 格式的参数定义 (JsonObjectSchema)
- `metadata` - 供应商特定元数据 (目前仅 Anthropic 支持)

#### @Tool 注解

标注 Java 方法为工具，自动生成 ToolSpecification:

```java
public class WeatherTools {
    @Tool("获取指定城市的天气")
    public String getWeather(@P("城市名称") String city) {
        return "晴天, 25°C";
    }
}
```

属性:
- `name` - 工具名 (默认使用方法名)
- `value` - 工具描述
- `returnBehavior` - 返回行为: `TO_LLM` (默认，结果回传 LLM) / `IMMEDIATE` (立即返回调用者)
- `metadata` - JSON 格式的元数据字符串

#### @P 注解

标注工具方法参数:
- `value` - 参数描述
- `required` - 是否必需 (默认 true)

#### ToolExecutionRequest / ToolExecutionResultMessage

LLM 返回的工具调用请求和执行结果:

```java
// LLM 返回的工具调用请求
ToolExecutionRequest request = aiMessage.toolExecutionRequests().get(0);
String toolName = request.name();
String arguments = request.arguments();  // JSON 字符串

// 工具执行结果
ToolExecutionResultMessage.builder()
    .id(request.id())
    .toolName(request.name())
    .text("执行结果文本")
    .isError(false)  // 错误标记，让 LLM 感知执行失败
    .build();
```

### 3.4 Embedding / 向量存储

#### EmbeddingModel

将文本转换为向量:

```java
public interface EmbeddingModel {
    Response<Embedding> embed(String text);
    Response<Embedding> embed(TextSegment textSegment);
    Response<List<Embedding>> embedAll(List<TextSegment> textSegments);
    int dimension();  // 向量维度
}
```

支持 `EmbeddingModelListener` 监听器 (onRequest/onResponse/onError)。

#### EmbeddingStore

向量数据库抽象:

```java
public interface EmbeddingStore<Embedded> {
    // 添加
    String add(Embedding embedding);
    void add(String id, Embedding embedding);
    String add(Embedding embedding, Embedded embedded);
    List<String> addAll(List<Embedding> embeddings);

    // 删除
    void remove(String id);
    void removeAll(Collection<String> ids);
    void removeAll(Filter filter);  // 按元数据过滤删除
    void removeAll();

    // 搜索
    EmbeddingSearchResult<Embedded> search(EmbeddingSearchRequest request);

    // 可观测性
    EmbeddingStore<Embedded> addListener(EmbeddingStoreListener listener);
}
```

EmbeddingSearchRequest 支持:
- `queryEmbedding` - 查询向量
- `maxResults` - 最大结果数
- `minScore` - 最小相似度分数
- `filter` - 元数据过滤条件 (Filter 接口)

### 3.5 Document 管道

#### DocumentSource -> DocumentParser -> Document -> DocumentSplitter -> TextSegment

```java
// 文档源
public interface DocumentSource {
    InputStream inputStream() throws IOException;
    Metadata metadata();
}

// 文档解析器
public interface DocumentParser {
    Document parse(InputStream inputStream);
}

// 文档
public interface Document {
    String text();
    Metadata metadata();
    TextSegment toTextSegment();
}

// 文档分割器
public interface DocumentSplitter {
    List<TextSegment> split(Document document);
    List<TextSegment> splitAll(List<Document> documents);
}

// 文档转换器
public interface DocumentTransformer {
    Document transform(Document document);
    List<Document> transformAll(List<Document> documents);
}
```

DocumentLoader 工具类:
```java
Document doc = DocumentLoader.load(source, parser);
```

### 3.6 RAG (检索增强生成)

#### RetrievalAugmentor

RAG 流程的入口:

```java
public interface RetrievalAugmentor {
    AugmentationResult augment(AugmentationRequest augmentationRequest);
}
```

默认实现 `DefaultRetrievalAugmentor` 的完整 RAG 管道:

```
UserMessage
  -> QueryTransformer (查询转换/扩展)
    -> ContentRetriever (内容检索，可多个)
      -> ContentAggregator (结果聚合)
        -> ContentInjector (注入到消息)
          -> 增强后的 ChatMessage
```

#### ContentRetriever

内容检索器接口:

```java
public interface ContentRetriever {
    List<Content> retrieve(Query query);
}
```

`EmbeddingStoreContentRetriever` 是最常用的实现，基于向量相似度检索。

#### ContentAggregator

多检索器结果聚合:
- `DefaultContentAggregator` - 默认聚合
- `ReRankingContentAggregator` - 基于 ScoringModel 重排序
- `ReciprocalRankFuser` - 倒数排名融合算法

### 3.7 Guardrails (安全护栏)

```java
public interface Guardrail<P extends GuardrailRequest, R extends GuardrailResult<R>> {
    R validate(P request);
}
```

两种方向:
- `InputGuardrail` - 输入护栏: 验证用户消息是否安全
- `OutputGuardrail` - 输出护栏: 验证 LLM 输出是否符合预期

OutputGuardrailsConfig 支持 `maxRetries`，当输出不符合要求时自动重试。

内置实现 (langchain4j-guardrails 模块):
- `JsonExtractorOutputGuardrail` - 从 LLM 输出中提取 JSON
- `MessageModeratorInputGuardrail` - 消息内容审核

ChatExecutor 将 Guardrails 与 ChatModel 组合:
```java
ChatExecutor.builder(chatModel)
    .inputGuardrails(inputGuardrails)
    .outputGuardrails(outputGuardrails)
    .build()
    .execute();
```

### 3.8 ChatMemory (聊天记忆)

```java
public interface ChatMemory {
    Object id();
    void add(ChatMessage message);
    void set(ChatMessage... messages);  // 替换全部消息 (用于压缩)
    List<ChatMessage> messages();
    void clear();
}
```

主要实现:
- `MessageWindowChatMemory` - 滑动窗口记忆 (保留最近 N 条消息)
- `TokenWindowChatMemory` - Token 窗口记忆 (保留最近 N 个 token 的消息)

ChatMemoryProvider 支持按 memoryId 创建独立的记忆实例:
```java
ChatMemoryProvider provider = memoryId ->
    MessageWindowChatMemory.withMaxMessages(20);
```

### 3.9 其他核心接口

| 接口 | 说明 |
|------|------|
| `ImageModel` | 图像生成模型 |
| `ScoringModel` | 文本评分/重排序模型 |
| `ModerationModel` | 内容审核模型 |
| `ClassificationModel` | 文本分类模型 |
| `AudioTranscriptionModel` | 音频转文字模型 |
| `LanguageModel` | 文本补全模型 (非聊天) |
| `PromptTemplate` | 提示词模板 (变量替换) |
| `ModelCatalog` | 模型信息目录 (查询模型参数/能力) |
| `JsonSchemaElement` | JSON Schema 结构化输出定义 |

---

## 4. 主模块 (langchain4j)

### 4.1 AiServices

声明式 Agent 的核心入口。通过 Java 接口 + 注解定义 Agent，AiServices 自动生成代理实现:

```java
// 定义 Agent 接口
interface Assistant {
    @SystemMessage("你是一个有帮助的助手")
    String chat(String userMessage);
}

// 构建 Agent
Assistant assistant = AiServices.builder(Assistant.class)
    .chatModel(chatModel)
    .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
    .tools(new WeatherTools(), new CalculatorTools())
    .toolProvider(mcpToolProvider)
    .contentRetriever(embeddingStoreContentRetriever)
    .retrievalAugmentor(retrievalAugmentor)
    .inputGuardrails(inputGuardrails)
    .outputGuardrails(outputGuardrails)
    .build();

// 使用
String answer = assistant.chat("今天北京天气怎么样?");
```

AiServices.builder() 完整配置项:

| 方法 | 说明 |
|------|------|
| `chatModel()` | 聊天模型 |
| `streamingChatModel()` | 流式聊天模型 |
| `systemMessage()` | 系统消息 |
| `systemMessageProvider()` | 动态系统消息提供器 |
| `chatMemory()` | 聊天记忆 |
| `chatMemoryProvider()` | 按 memoryId 创建记忆 |
| `chatRequestTransformer()` | 请求转换器 (修改发送给 LLM 的请求) |
| `tools()` | 静态工具 (带 @Tool 注解的对象) |
| `toolProvider()` | 动态工具提供器 (每次调用动态决定工具集) |
| `executeToolsConcurrently()` | 并发执行多个工具调用 |
| `maxSequentialToolsInvocations()` | 最大连续工具调用次数 (默认 100) |
| `beforeToolExecution()` | 工具执行前回调 |
| `afterToolExecution()` | 工具执行后回调 |
| `hallucinatedToolNameStrategy()` | LLM 调用不存在工具时的策略 |
| `toolArgumentsErrorHandler()` | 工具参数解析错误处理器 |
| `toolExecutionErrorHandler()` | 工具执行错误处理器 |
| `contentRetriever()` | RAG 内容检索器 |
| `retrievalAugmentor()` | RAG 增强器 |
| `moderationModel()` | 内容审核模型 |
| `inputGuardrails()` | 输入安全护栏 |
| `outputGuardrails()` | 输出安全护栏 |
| `inputGuardrailsConfig()` | 输入护栏配置 |
| `outputGuardrailsConfig()` | 输出护栏配置 (含 maxRetries) |
| `registerListener()` | 注册 AiService 事件监听器 |
| `storeRetrievedContentInChatMemory()` | 是否将检索内容存入聊天记忆 |

### 4.2 ToolService

工具执行引擎，AiServices 内部使用:

```java
// 核心能力
- 工具注册: 从 @Tool 注解方法自动生成 ToolSpecification + ToolExecutor
- 动态工具: ToolProvider 每次请求动态提供工具
- 并发执行: executeToolsConcurrently() 多个 tool_call 并行
- 推理循环: executeInferenceAndToolsLoop() 自动处理 LLM <-> 工具的多轮交互
- 错误处理: ToolArgumentsErrorHandler / ToolExecutionErrorHandler 分类处理
- 幻觉策略: HallucinatedToolNameStrategy 处理 LLM 调用不存在的工具
- 立即返回: ReturnBehavior.IMMEDIATE 跳过 LLM 后处理
- 回调: beforeToolExecution / afterToolExecution
```

ToolExecutionResult:
- `resultText()` - 结果文本 (发送给 LLM)
- `result()` - 原始 Java 对象 (保留类型信息)
- `isError()` - 错误标记 (让 LLM 感知执行失败)

### 4.3 ToolProvider

动态工具提供机制:

```java
public interface ToolProvider {
    ToolProviderResult provideTools(ToolProviderRequest request);
}
```

ToolProviderRequest 包含:
- `userMessage()` - 当前用户消息
- `invocationContext()` - 调用上下文 (含 memoryId)

每次 LLM 调用前，ToolProvider 可根据用户消息和上下文动态决定提供哪些工具。

---

## 5. Agent 框架 (langchain4j-agentic)

### 5.1 @Agent 注解

将方法标注为 Agent，可被其他 Agent 调用:

```java
@Agent(name = "researcher", description = "负责搜索和收集信息")
String research(@V("topic") String topic);
```

属性:
- `name` - Agent 名称 (默认使用方法名)
- `value` / `description` - Agent 描述
- `outputKey` - 输出变量名 (存入 AgenticScope)
- `async` - 是否异步执行
- `summarizedContext` - 参与上下文定义的其他 Agent 名称

### 5.2 AgentBuilder / UntypedAgentBuilder

编程式构建 Agent:

```java
// 类型安全构建
AgentBuilder<MyAgent> builder = new AgentBuilder<>(MyAgent.class);
MyAgent agent = builder.build();

// 无类型构建 (动态)
UntypedAgentBuilder builder = new UntypedAgentBuilder()
    .returnType(String.class)
    .inputs(new AgentArgument("topic", String.class));
```

### 5.3 声明式工作流编排

langchain4j-agentic 提供 5 种声明式 Agent 编排模式:

#### LoopAgent (循环)
Agent 反复执行直到满足退出条件:
```java
interface MyLoopAgent {
    @Agent("执行任务直到完成")
    String execute(@V("task") String task);
}
```

#### SequenceAgent (顺序)
多个 Agent 按顺序执行，前一个的输出作为后一个的输入:
```java
// Agent A -> Agent B -> Agent C
```

#### ParallelAgent (并行)
多个 Agent 并行执行，结果汇总:
```java
// Agent A |
// Agent B | -> 汇总结果
// Agent C |
```

#### ConditionalAgent (条件)
根据条件选择执行不同的 Agent:
```java
// if condition -> Agent A
// else -> Agent B
```

#### SupervisorAgent (监督者)
一个监督者 Agent 协调多个子 Agent:
```java
// Supervisor -> 决定调用哪个子 Agent
//   |-- Agent A
//   |-- Agent B
//   |-- Agent C
```

### 5.4 AgenticScope (作用域)

Agent 执行的上下文容器，存储变量和状态:

```java
// 持久化
public interface AgenticScopePersister {
    void persist(AgenticScope scope);
    AgenticScope restore(String scopeId);
}

// 存储
public interface AgenticScopeStore {
    void save(String key, Object value);
    Object load(String key);
}
```

`AgenticScopeJsonCodec` 使用 Jackson 进行序列化。
`AgenticScopeRegistry` 提供全局 Scope 注册。

### 5.5 Planner (规划器)

```java
public interface Planner {
    Plan plan(PlannerRequest request);
}
```

PlannerAgent 是声明式规划器，结合 SupervisorPlanner 实现监督者模式的规划。

### 5.6 可观测性

#### AgentListener

```java
public interface AgentListener {
    void beforeAgentInvocation(AgentRequest agentRequest);
    void afterAgentInvocation(AgentResponse agentResponse);
    void onAgentInvocationError(AgentInvocationError error);
    void afterAgenticScopeCreated(AgenticScope scope);
}
```

#### AgentMonitor

包装 AgentListener，记录所有 Agent 调用的执行树:

```java
AgentMonitor monitor = new AgentMonitor("my-workflow");
// ... 执行 Agent ...
// 获取执行记录
List<AgentInvocation> invocations = monitor.invocations();
```

#### HtmlReportGenerator

从 AgentMonitor 生成 HTML 可视化报告，展示:
- Agent 调用拓扑图
- 每个 Agent 的输入/输出
- 执行时间和状态

### 5.7 错误恢复

```java
// ErrorRecoveryResult 三种策略
ErrorRecoveryResult.throwException();  // 抛出异常
ErrorRecoveryResult.retry();           // 重试
ErrorRecoveryResult.result(fallback);  // 返回降级结果
```

---

## 6. Agent 模式 (langchain4j-agentic-patterns)

### 6.1 GOAP (目标导向行动规划)

Goal-Oriented Action Planning，基于前置条件和效果的规划算法:

```java
// GoalOrientedPlanner 实现 Planner 接口
GoalOrientedPlanner planner = new GoalOrientedPlanner();

// GoalOrientedSearchGraph 构建依赖图
GoalOrientedSearchGraph graph = new GoalOrientedSearchGraph(agents);
List<AgentInstance> plan = graph.search(preconditions, goal);
```

核心类:
- `GoalOrientedPlanner` - GOAP 规划器
- `GoalOrientedSearchGraph` - 目标搜索图
- `DependencyGraphSearch` - 依赖图搜索算法

### 6.2 P2P (点对点)

Agent 之间直接通信的模式:

```java
public interface P2PAgent {
    @Agent("处理 P2P 请求")
    String invoke(@V("p2p_request") String request);
}
```

核心类:
- `P2PAgent` - P2P Agent 接口
- `P2PPlanner` - P2P 规划器 (支持 maxAgentsInvocations 和 exitCondition)
- `VariablesExtractorAgent` - 变量提取 Agent

---

## 7. A2A 协议 (langchain4j-agentic-a2a)

Agent-to-Agent 远程调用协议:

```java
// 客户端构建
A2AClientBuilder<RemoteAgent> builder = new DefaultA2AClientBuilder<>(
    "http://remote-agent:8080",
    RemoteAgent.class
);

// 自动发现远程 Agent 的 AgentCard
AgentCard card = builder.agentCard();
```

核心类:
- `A2AClientInstance` - A2A 客户端实例 (inputKeys + agentCard)
- `A2AClientAgentInvoker` - A2A 客户端 Agent 调用器
- `DefaultA2AClientBuilder` - A2A 客户端构建器
- `DefaultA2AService` - A2A 服务端实现

---

## 8. MCP 协议 (langchain4j-mcp)

### 8.1 McpClient

MCP 客户端接口:

```java
public interface McpClient {
    String key();                                    // 客户端标识
    List<ToolSpecification> listTools();             // 列出所有工具
    ToolExecutionResult executeTool(ToolExecutionRequest request);  // 执行工具
    List<McpResource> listResources();               // 列出资源
    McpResourceContents readResource(String uri);    // 读取资源
    void close();                                    // 关闭连接
}
```

默认实现 `DefaultMcpClient` 支持:
- 自动初始化握手 (initialize/initialized)
- 工具列表缓存
- 日志消息处理 (McpLogMessageHandler)

### 8.2 四种传输

| 传输 | 类 | 说明 |
|------|-----|------|
| Streamable HTTP | `StreamableHttpMcpTransport` | 基于 HTTP 的流式传输 (推荐) |
| SSE | `HttpMcpTransport` (SSE 模式) | Server-Sent Events 传输 |
| Stdio | `StdioMcpTransport` | 标准输入输出传输 (本地进程) |
| WebSocket | WebSocket 传输 | WebSocket 双向通信 |

McpTransport 接口:
```java
public interface McpTransport {
    void start(McpOperationHandler messageHandler);
    CompletableFuture<JsonNode> initialize(McpInitializeRequest request);
    CompletableFuture<JsonNode> executeOperationWithResponse(McpClientMessage request);
    void close();
}
```

### 8.3 McpToolProvider

将 MCP 服务器的工具动态提供给 AiServices:

```java
McpToolProvider toolProvider = McpToolProvider.builder()
    .mcpClients(mcpClient1, mcpClient2)
    .failIfOneServerFails(false)           // 单个服务器失败不影响其他
    .filter((client, spec) -> ...)         // 工具过滤
    .filterToolNames("tool1", "tool2")     // 按名称过滤
    .toolNameMapper((client, spec) -> ...) // 工具名映射
    .toolSpecificationMapper((client, spec) -> ...)  // 工具规范映射
    .toolWrapper(executor -> ...)          // 执行器包装 (用于追踪)
    .resourcesAsToolsPresenter(presenter)  // 资源转工具
    .build();

// 动态管理
toolProvider.addMcpClient(newClient);
toolProvider.removeMcpClient(oldClient);
toolProvider.addFilter(additionalFilter);
toolProvider.setFilter(newFilter);
toolProvider.resetFilters();
```

过滤在映射之前执行，确保过滤使用原始工具名。

### 8.4 ResourcesAsTools

将 MCP 资源自动暴露为两个工具:
- `list_resources` - 列出所有可用资源
- `get_resource` - 获取指定资源内容

```java
McpResourcesAsToolsPresenter presenter = new McpResourcesAsToolsPresenter();
McpToolProvider.builder()
    .mcpClients(client)
    .resourcesAsToolsPresenter(presenter)
    .build();
```

---

## 9. 工具执行详细机制

### 9.1 执行流程

```
1. LLM 返回 AiMessage (含 toolExecutionRequests)
2. ToolService 遍历每个 ToolExecutionRequest
3. 查找对应的 ToolExecutor
   - 找到: 执行工具
   - 未找到: 应用 HallucinatedToolNameStrategy
4. 执行工具:
   a. 触发 beforeToolExecution 回调
   b. 调用 ToolExecutor.executeWithContext()
   c. 捕获异常:
      - ToolArgumentsException -> argumentsErrorHandler
      - 其他异常 -> executionErrorHandler
   d. 触发 afterToolExecution 回调
5. 构建 ToolExecutionResultMessage 加入消息历史
6. 检查是否有 IMMEDIATE 返回的工具
7. 继续下一轮 LLM 调用或结束循环
```

### 9.2 并发执行

```java
AiServices.builder(MyAgent.class)
    .executeToolsConcurrently()  // 使用默认线程池
    // 或
    .executeToolsConcurrently(customExecutor)  // 使用自定义线程池
    .build();
```

当 LLM 返回多个 tool_call 时，自动并行执行。单个 tool_call 时仍在当前线程执行。

### 9.3 错误处理策略

```java
// 参数解析错误
AiServices.builder(MyAgent.class)
    .toolArgumentsErrorHandler((error, context) -> {
        // 默认: 抛出异常
        // 可选: 返回错误文本让 LLM 重试
        return ToolErrorHandlerResult.text("参数错误: " + error.getMessage());
    })

// 执行错误
    .toolExecutionErrorHandler((error, context) -> {
        // 默认: 返回错误消息文本
        return ToolErrorHandlerResult.text("执行失败: " + error.getMessage());
    })

// 幻觉工具名
    .hallucinatedToolNameStrategy(request -> {
        // 默认: HallucinatedToolNameStrategy.THROW_EXCEPTION
        // 可选: 返回提示消息让 LLM 重试
        return ToolExecutionResultMessage.from(request, "工具不存在，请检查工具名");
    })
    .build();
```

---

## 10. 完整使用示例

### 10.1 基础聊天

```java
ChatModel chatModel = OpenAiChatModel.builder()
    .apiKey("sk-...")
    .modelName("gpt-4o")
    .build();

String answer = chatModel.chat("你好");
```

### 10.2 带工具的 Agent

```java
// 定义工具
class MyTools {
    @Tool("搜索网页")
    String search(@P("查询关键词") String query) {
        return WebSearchEngine.search(query);
    }

    @Tool("获取当前时间")
    String getCurrentTime() {
        return LocalDateTime.now().toString();
    }
}

// 定义 Agent 接口
interface MyAssistant {
    @SystemMessage("你是一个有帮助的助手，可以搜索网页和查看时间")
    String chat(String message);
}

// 构建并使用
MyAssistant assistant = AiServices.builder(MyAssistant.class)
    .chatModel(chatModel)
    .tools(new MyTools())
    .chatMemory(MessageWindowChatMemory.withMaxMessages(20))
    .build();

String answer = assistant.chat("现在几点了?");
```

### 10.3 RAG 管道

```java
// 1. 加载文档
Document doc = DocumentLoader.load(
    new FileSystemDocumentSource(Path.of("data.pdf")),
    new ApachePdfBoxDocumentParser()
);

// 2. 分割文档
DocumentSplitter splitter = DocumentSplitters.recursive(500, 50);
List<TextSegment> segments = splitter.split(doc);

// 3. 嵌入并存储
EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
EmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
for (TextSegment segment : segments) {
    Embedding embedding = embeddingModel.embed(segment).content();
    store.add(embedding, segment);
}

// 4. 构建 RAG Agent
ContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
    .embeddingStore(store)
    .embeddingModel(embeddingModel)
    .maxResults(5)
    .minScore(0.7)
    .build();

MyAssistant assistant = AiServices.builder(MyAssistant.class)
    .chatModel(chatModel)
    .contentRetriever(retriever)
    .build();
```

### 10.4 MCP 集成

```java
// 创建 MCP 客户端
McpClient mcpClient = DefaultMcpClient.builder()
    .transport(new StdioMcpTransport.Builder()
        .command("npx", "-y", "@modelcontextprotocol/server-filesystem", "/tmp")
        .build())
    .build();

// 创建 MCP 工具提供器
McpToolProvider toolProvider = McpToolProvider.builder()
    .mcpClients(mcpClient)
    .build();

// 集成到 AiServices
MyAssistant assistant = AiServices.builder(MyAssistant.class)
    .chatModel(chatModel)
    .toolProvider(toolProvider)
    .build();
```

### 10.5 多 Agent 工作流

```java
// 定义多个 Agent
interface ResearchTeam {
    @Agent(name = "researcher", description = "搜索和收集信息")
    String research(@V("topic") String topic);

    @Agent(name = "writer", description = "撰写报告")
    String write(@V("research_result") String researchResult);

    @Agent(name = "reviewer", description = "审核报告")
    String review(@V("draft") String draft);
}

// 使用 AgenticServices 编排
// Sequence: researcher -> writer -> reviewer
```

### 10.6 Guardrails

```java
// 自定义输入护栏
class ProfanityFilter implements InputGuardrail {
    @Override
    public InputGuardrailResult validate(InputGuardrailRequest request) {
        if (containsProfanity(request.userMessage().text())) {
            return InputGuardrailResult.failure("消息包含不当内容");
        }
        return InputGuardrailResult.success();
    }
}

// 自定义输出护栏
class JsonFormatGuardrail implements OutputGuardrail {
    @Override
    public OutputGuardrailResult validate(OutputGuardrailRequest request) {
        try {
            new ObjectMapper().readTree(request.aiMessage().text());
            return OutputGuardrailResult.success();
        } catch (Exception e) {
            return OutputGuardrailResult.failure("输出不是有效的 JSON");
        }
    }
}

// 使用
MyAssistant assistant = AiServices.builder(MyAssistant.class)
    .chatModel(chatModel)
    .inputGuardrails(new ProfanityFilter())
    .outputGuardrails(new JsonFormatGuardrail())
    .outputGuardrailsConfig(OutputGuardrailsConfig.builder()
        .maxRetries(3)  // 输出不符合要求时最多重试 3 次
        .build())
    .build();
```

---

## 11. 设计模式和架构特点

### 11.1 SPI 插件化

langchain4j 大量使用 Java SPI (ServiceLoader) 机制:
- `ChatMessageJsonCodec` - 消息序列化编解码器
- `AiServicesFactory` - AiServices 工厂
- `WorkflowAgentsBuilder` - 工作流构建器
- 各供应商模块通过 SPI 自动注册

### 11.2 Builder 模式

几乎所有核心类都使用 Builder 模式:
- `ChatRequest.builder()`
- `ToolSpecification.builder()`
- `AiServices.builder()`
- `McpToolProvider.builder()`
- `EmbeddingSearchRequest.builder()`

### 11.3 参数覆盖机制

`ChatRequestParameters` 支持层级覆盖:
```java
// 模型默认参数 <- 请求参数 (请求参数优先)
defaultRequestParameters().overrideWith(chatRequest.parameters())
```

### 11.4 监听器模式

多层监听器:
- `ChatModelListener` - 模型层 (所有 LLM 调用)
- `EmbeddingModelListener` - 嵌入模型层
- `EmbeddingStoreListener` - 向量存储层
- `AgentListener` - Agent 层 (Agent 调用前后)
- `AiServiceListener` - AiService 层 (请求/响应/工具执行事件)

---

## 12. 与 jasmine-core 的关键差异

| 维度 | langchain4j | jasmine-core |
|------|-------------|-------------|
| 工具定义 | @Tool 注解自动生成 | Tool 抽象类手动实现 |
| 工具执行 | 支持并发 | 仅顺序 |
| Agent 定义 | 声明式 (@Agent + 接口代理) | 命令式 (ToolExecutor while 循环) |
| 工作流 | 5 种编排模式 | 仅 Loop (图引擎在 graph 模块) |
| RAG | 完整管道 | 无 |
| Embedding | 完整支持 | 无 |
| Guardrails | 输入/输出护栏 | 无 |
| MCP 传输 | 4 种 | 2 种 (HTTP/SSE) |
| 图引擎 | 无 | GraphAgent |
| GOAP | 有 (agentic-patterns) | 有 (planner 模块) |
| 流式续传 | 无 | StreamResumeHelper |
| 上下文压缩 | 无内置 | 4 种 CompressionStrategy |
| 移动端工具 | 无 | DEX 编辑/文件操作/Shell 等 |
