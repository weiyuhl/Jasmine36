package com.lhzkml.jasmine.core.prompt.llm

import com.lhzkml.jasmine.core.prompt.model.ChatMessage
import com.lhzkml.jasmine.core.prompt.model.ChatResult
import com.lhzkml.jasmine.core.prompt.model.Concept
import com.lhzkml.jasmine.core.prompt.model.FactType
import com.lhzkml.jasmine.core.prompt.model.MemoryScope
import com.lhzkml.jasmine.core.prompt.model.MemoryScopeType
import com.lhzkml.jasmine.core.prompt.model.MemoryScopesProfile
import com.lhzkml.jasmine.core.prompt.model.MemorySubject
import com.lhzkml.jasmine.core.prompt.model.MultipleFacts
import com.lhzkml.jasmine.core.prompt.model.SingleFact
import com.lhzkml.jasmine.core.prompt.model.ToolCall
import com.lhzkml.jasmine.core.prompt.model.ToolResult
import com.lhzkml.jasmine.core.prompt.model.Usage
import com.lhzkml.jasmine.core.prompt.model.prompt
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class AgentMemoryTest {

    private lateinit var rootDir: File
    private lateinit var provider: LocalFileMemoryProvider
    private lateinit var memory: AgentMemory
    private val scope = MemoryScope.Agent("test")
    private val scopesProfile = MemoryScopesProfile(
        MemoryScopeType.AGENT to "test"
    )

    @Before
    fun setup() {
        rootDir = File(System.getProperty("java.io.tmpdir"), "jasmine-memory-test-${System.nanoTime()}")
        rootDir.mkdirs()
        provider = LocalFileMemoryProvider(rootDir)
        memory = AgentMemory(provider, scopesProfile)
    }

    @After
    fun tearDown() {
        rootDir.deleteRecursively()
    }

    private val buildConcept = Concept("build-system", "Project build system", FactType.SINGLE)
    private val depsConcept = Concept("dependencies", "Project dependencies", FactType.MULTIPLE)

    // ========== 直接 save/load 测试 ==========

    @Test
    fun `save and load single fact`() = runTest {
        val fact = SingleFact(buildConcept, System.currentTimeMillis(), "Gradle 8.0")
        memory.save(fact, MemorySubject.Project, scope)

        val loaded = memory.load(buildConcept, MemorySubject.Project, scope)
        assertEquals(1, loaded.size)
        assertEquals("Gradle 8.0", (loaded[0] as SingleFact).value)
    }

    @Test
    fun `save and load multiple facts`() = runTest {
        val fact = MultipleFacts(depsConcept, System.currentTimeMillis(), listOf("ktor", "kotlinx-serialization"))
        memory.save(fact, MemorySubject.Project, scope)

        val loaded = memory.load(depsConcept, MemorySubject.Project, scope)
        assertEquals(1, loaded.size)
        assertEquals(2, (loaded[0] as MultipleFacts).values.size)
    }

    @Test
    fun `loadAll returns all facts`() = runTest {
        memory.save(SingleFact(buildConcept, 1000L, "Gradle"), MemorySubject.Project, scope)
        memory.save(MultipleFacts(depsConcept, 2000L, listOf("ktor")), MemorySubject.Project, scope)

        val all = memory.loadAll(MemorySubject.Project, scope)
        assertEquals(2, all.size)
    }

    @Test
    fun `loadByDescription works`() = runTest {
        memory.save(SingleFact(buildConcept, 1000L, "Gradle"), MemorySubject.Project, scope)
        memory.save(MultipleFacts(depsConcept, 2000L, listOf("ktor")), MemorySubject.Project, scope)

        val results = memory.loadByDescription("build", MemorySubject.Project, scope)
        assertEquals(1, results.size)
    }

    // ========== saveFactsFromHistory 测试 ==========

    @Test
    fun `saveFactsFromHistory extracts single fact`() = runTest {
        val mockClient = object : MemoryMockChatClient() {
            override suspend fun chatWithUsage(
                messages: List<ChatMessage>,
                model: String,
                maxTokens: Int?,
                samplingParams: com.lhzkml.jasmine.core.prompt.model.SamplingParams?,
                tools: List<com.lhzkml.jasmine.core.prompt.model.ToolDescriptor>
            ): ChatResult {
                return ChatResult("Gradle 8.0 with Kotlin DSL", Usage(100, 20, 120))
            }
        }

        val initialPrompt = prompt("test") {
            system("You are a helpful assistant.")
            user("What build system does this project use?")
            assistant("This project uses Gradle 8.0 with Kotlin DSL for build configuration.")
        }

        val session = LLMSession(mockClient, "test-model", initialPrompt)
        memory.saveFactsFromHistory(session, buildConcept, MemorySubject.Project, scope)

        // 验证事实已保存
        val loaded = memory.load(buildConcept, MemorySubject.Project, scope)
        assertEquals(1, loaded.size)
        assertTrue(loaded[0] is SingleFact)
        assertEquals("Gradle 8.0 with Kotlin DSL", (loaded[0] as SingleFact).value)

        // 验证 prompt 已恢复
        assertEquals(3, session.prompt.messages.size)
        assertEquals("system", session.prompt.messages[0].role)
    }

    @Test
    fun `saveFactsFromHistory extracts multiple facts`() = runTest {
        val mockClient = object : MemoryMockChatClient() {
            override suspend fun chatWithUsage(
                messages: List<ChatMessage>,
                model: String,
                maxTokens: Int?,
                samplingParams: com.lhzkml.jasmine.core.prompt.model.SamplingParams?,
                tools: List<com.lhzkml.jasmine.core.prompt.model.ToolDescriptor>
            ): ChatResult {
                return ChatResult("kotlin-stdlib 1.9.0\nktor-client 2.3.0\nkotlinx-serialization 1.6.0", Usage(100, 30, 130))
            }
        }

        val initialPrompt = prompt("test") {
            system("You are a helpful assistant.")
            user("What are the project dependencies?")
            assistant("The project uses kotlin-stdlib, ktor-client, and kotlinx-serialization.")
        }

        val session = LLMSession(mockClient, "test-model", initialPrompt)
        memory.saveFactsFromHistory(session, depsConcept, MemorySubject.Project, scope)

        val loaded = memory.load(depsConcept, MemorySubject.Project, scope)
        assertEquals(1, loaded.size)
        assertTrue(loaded[0] is MultipleFacts)
        assertEquals(3, (loaded[0] as MultipleFacts).values.size)
        assertEquals("kotlin-stdlib 1.9.0", (loaded[0] as MultipleFacts).values[0])
    }

    // ========== loadFactsToAgent 测试 ==========

    @Test
    fun `loadFactsToAgent injects facts into session`() = runTest {
        memory.save(SingleFact(buildConcept, 1000L, "Gradle 8.0"), MemorySubject.Project, scope)

        val mockClient = MemoryMockChatClient()
        val initialPrompt = prompt("test") {
            system("You are a helpful assistant.")
        }
        val session = LLMSession(mockClient, "test-model", initialPrompt)

        memory.loadFactsToAgent(
            session,
            buildConcept,
            scopes = listOf(MemoryScopeType.AGENT),
            subjects = listOf(MemorySubject.Project)
        )

        val messages = session.prompt.messages
        assertEquals(2, messages.size) // system + user(facts)
        assertTrue(messages[1].content.contains("build-system"))
        assertTrue(messages[1].content.contains("Gradle 8.0"))
    }

    @Test
    fun `loadFactsToAgent with no facts does nothing`() = runTest {
        val mockClient = MemoryMockChatClient()
        val initialPrompt = prompt("test") {
            system("You are a helpful assistant.")
        }
        val session = LLMSession(mockClient, "test-model", initialPrompt)

        memory.loadFactsToAgent(
            session,
            buildConcept,
            scopes = listOf(MemoryScopeType.AGENT),
            subjects = listOf(MemorySubject.Project)
        )

        assertEquals(1, session.prompt.messages.size) // only system
    }

    @Test
    fun `loadFactsToAgent prioritizes higher priority subjects`() = runTest {
        // User 优先级 10, Project 优先级 20
        memory.save(
            SingleFact(buildConcept, 1000L, "Maven"),
            MemorySubject.Project,
            scope
        )
        memory.save(
            SingleFact(buildConcept, 2000L, "Gradle"),
            MemorySubject.User,
            scope
        )

        val mockClient = MemoryMockChatClient()
        val session = LLMSession(mockClient, "test-model", prompt("test") { system("Hi") })

        memory.loadFactsToAgent(
            session,
            buildConcept,
            scopes = listOf(MemoryScopeType.AGENT),
            subjects = listOf(MemorySubject.User, MemorySubject.Project)
        )

        val messages = session.prompt.messages
        assertEquals(2, messages.size)
        // User 优先级更高，应该选 User 的 "Gradle"
        assertTrue(messages[1].content.contains("Gradle"))
        assertFalse(messages[1].content.contains("Maven"))
    }

    // ========== loadAllFactsToAgent 测试 ==========

    @Test
    fun `loadAllFactsToAgent loads all facts`() = runTest {
        memory.save(SingleFact(buildConcept, 1000L, "Gradle 8.0"), MemorySubject.Project, scope)
        memory.save(MultipleFacts(depsConcept, 2000L, listOf("ktor", "kotlinx")), MemorySubject.Project, scope)

        val mockClient = MemoryMockChatClient()
        val session = LLMSession(mockClient, "test-model", prompt("test") { system("Hi") })

        memory.loadAllFactsToAgent(
            session,
            scopes = listOf(MemoryScopeType.AGENT),
            subjects = listOf(MemorySubject.Project)
        )

        // system + 2 user messages (one per concept)
        assertEquals(3, session.prompt.messages.size)
    }

    // ========== parseFactsFromResponse 测试 ==========

    @Test
    fun `parseFactsFromResponse parses JSON format`() {
        val json = """[
            {"subject": "user", "keyword": "preferred-language", "description": "User's preferred language", "value": "Kotlin"},
            {"subject": "project", "keyword": "build-system", "description": "Project build system", "value": "Gradle 8.0"}
        ]"""

        val facts = parseFactsFromResponse(json)
        assertEquals(2, facts.size)

        val (subject1, fact1) = facts[0]
        assertEquals("user", subject1.name)
        assertTrue(fact1 is SingleFact)
        assertEquals("Kotlin", (fact1 as SingleFact).value)

        val (subject2, fact2) = facts[1]
        assertEquals("project", subject2.name)
        assertTrue(fact2 is SingleFact)
        assertEquals("Gradle 8.0", (fact2 as SingleFact).value)
    }

    @Test
    fun `parseFactsFromResponse groups same subject+keyword into MultipleFacts`() {
        val json = """[
            {"subject": "project", "keyword": "dependencies", "description": "Project deps", "value": "ktor"},
            {"subject": "project", "keyword": "dependencies", "description": "Project deps", "value": "kotlinx-serialization"}
        ]"""

        val facts = parseFactsFromResponse(json)
        assertEquals(1, facts.size)

        val (subject, fact) = facts[0]
        assertEquals("project", subject.name)
        assertTrue(fact is MultipleFacts)
        assertEquals(2, (fact as MultipleFacts).values.size)
    }

    @Test
    fun `parseFactsFromResponse parses pipe-separated format`() {
        val text = """user|preferred-language|User's preferred language|Kotlin
project|build-system|Project build system|Gradle 8.0"""

        val facts = parseFactsFromResponse(text)
        assertEquals(2, facts.size)
        assertEquals("Kotlin", (facts[0].second as SingleFact).value)
        assertEquals("Gradle 8.0", (facts[1].second as SingleFact).value)
    }

    // ========== NoMemory 测试 ==========

    @Test
    fun `NoMemory does nothing`() = runTest {
        val noMemory = AgentMemory(NoMemory)
        noMemory.save(SingleFact(buildConcept, 1000L, "test"), MemorySubject.Project, scope)
        val loaded = noMemory.load(buildConcept, MemorySubject.Project, scope)
        assertTrue(loaded.isEmpty())
    }

    // ========== MemoryScopesProfile 测试 ==========

    @Test
    fun `MemoryScopesProfile resolves scopes correctly`() {
        val profile = MemoryScopesProfile(
            MemoryScopeType.AGENT to "my-agent",
            MemoryScopeType.FEATURE to "chat",
            MemoryScopeType.PRODUCT to "jasmine",
            MemoryScopeType.ORGANIZATION to "my-org"
        )

        assertEquals(MemoryScope.Agent("my-agent"), profile.getScope(MemoryScopeType.AGENT))
        assertEquals(MemoryScope.Feature("chat"), profile.getScope(MemoryScopeType.FEATURE))
        assertEquals(MemoryScope.Product("jasmine"), profile.getScope(MemoryScopeType.PRODUCT))
        assertEquals(MemoryScope.CrossProduct, profile.getScope(MemoryScopeType.ORGANIZATION))
    }

    @Test
    fun `MemoryScopesProfile returns null for unconfigured scope`() {
        val profile = MemoryScopesProfile()
        assertNull(profile.getScope(MemoryScopeType.AGENT))
    }

    // ========== buildHistoryXml 测试 ==========

    @Test
    fun `buildHistoryXml wraps messages in XML`() {
        val messages = listOf(
            ChatMessage.system("You are helpful."),
            ChatMessage.user("Hello"),
            ChatMessage.assistant("Hi there")
        )
        val xml = buildHistoryXml(messages)
        assertTrue(xml.contains("<conversation_to_extract_facts>"))
        assertTrue(xml.contains("<system>"))
        assertTrue(xml.contains("<user>"))
        assertTrue(xml.contains("<assistant>"))
        assertTrue(xml.contains("</conversation_to_extract_facts>"))
    }

    @Test
    fun `buildHistoryXml handles tool calls`() {
        val toolCall = ToolCall("call_1", "read_file", """{"path":"test.kt"}""")
        val messages = listOf(
            ChatMessage.system("System"),
            ChatMessage.user("Read the file"),
            ChatMessage.assistantWithToolCalls(listOf(toolCall)),
            ChatMessage.toolResult(ToolResult("call_1", "read_file", "file content here"))
        )
        val xml = buildHistoryXml(messages)
        assertTrue(xml.contains("<tool_call tool=read_file>"))
        assertTrue(xml.contains("""{"path":"test.kt"}"""))
        assertTrue(xml.contains("<tool_result tool=read_file>"))
        assertTrue(xml.contains("file content here"))
    }
}

// ========== Mock ==========

private open class MemoryMockChatClient : ChatClient {
    override val provider = LLMProvider.OpenAI

    override suspend fun chat(
        messages: List<ChatMessage>,
        model: String,
        maxTokens: Int?,
        samplingParams: com.lhzkml.jasmine.core.prompt.model.SamplingParams?,
        tools: List<com.lhzkml.jasmine.core.prompt.model.ToolDescriptor>
    ): String = "mock response"

    override suspend fun chatWithUsage(
        messages: List<ChatMessage>,
        model: String,
        maxTokens: Int?,
        samplingParams: com.lhzkml.jasmine.core.prompt.model.SamplingParams?,
        tools: List<com.lhzkml.jasmine.core.prompt.model.ToolDescriptor>
    ): ChatResult = ChatResult("mock response", Usage(10, 5, 15))

    override fun chatStream(
        messages: List<ChatMessage>,
        model: String,
        maxTokens: Int?,
        samplingParams: com.lhzkml.jasmine.core.prompt.model.SamplingParams?,
        tools: List<com.lhzkml.jasmine.core.prompt.model.ToolDescriptor>
    ): kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.flowOf("mock")

    override suspend fun chatStreamWithUsage(
        messages: List<ChatMessage>,
        model: String,
        maxTokens: Int?,
        samplingParams: com.lhzkml.jasmine.core.prompt.model.SamplingParams?,
        tools: List<com.lhzkml.jasmine.core.prompt.model.ToolDescriptor>,
        onChunk: suspend (String) -> Unit
    ): StreamResult = StreamResult("mock response", Usage(10, 5, 15))

    override suspend fun listModels() = emptyList<com.lhzkml.jasmine.core.prompt.model.ModelInfo>()
    override fun close() {}
}
