package com.lhzkml.jasmine.core.prompt.llm

import com.lhzkml.jasmine.core.prompt.model.Concept
import com.lhzkml.jasmine.core.prompt.model.FactType
import com.lhzkml.jasmine.core.prompt.model.MemoryScope
import com.lhzkml.jasmine.core.prompt.model.MemorySubject
import com.lhzkml.jasmine.core.prompt.model.MultipleFacts
import com.lhzkml.jasmine.core.prompt.model.SingleFact
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class LocalFileMemoryProviderTest {

    private lateinit var rootDir: File
    private lateinit var provider: LocalFileMemoryProvider

    @Before
    fun setup() {
        rootDir = File(System.getProperty("java.io.tmpdir"), "jasmine-memory-test-${System.nanoTime()}")
        rootDir.mkdirs()
        provider = LocalFileMemoryProvider(rootDir)
    }

    @After
    fun tearDown() {
        rootDir.deleteRecursively()
    }

    private val buildConcept = Concept("build-system", "Project build system", FactType.SINGLE)
    private val depsConcept = Concept("dependencies", "Project dependencies", FactType.MULTIPLE)
    private val subject = MemorySubject.Project
    private val scope = MemoryScope.Agent("test-agent")

    @Test
    fun `save and load single fact`() = runTest {
        val fact = SingleFact(buildConcept, System.currentTimeMillis(), "Gradle 8.0")
        provider.save(fact, subject, scope)

        val loaded = provider.load(buildConcept, subject, scope)
        assertEquals(1, loaded.size)
        assertTrue(loaded[0] is SingleFact)
        assertEquals("Gradle 8.0", (loaded[0] as SingleFact).value)
    }

    @Test
    fun `save and load multiple facts`() = runTest {
        val fact = MultipleFacts(depsConcept, System.currentTimeMillis(), listOf("kotlin-stdlib", "ktor-client"))
        provider.save(fact, subject, scope)

        val loaded = provider.load(depsConcept, subject, scope)
        assertEquals(1, loaded.size)
        assertTrue(loaded[0] is MultipleFacts)
        assertEquals(listOf("kotlin-stdlib", "ktor-client"), (loaded[0] as MultipleFacts).values)
    }

    @Test
    fun `append multiple saves to same concept`() = runTest {
        val fact1 = SingleFact(buildConcept, 1000L, "Gradle 7.0")
        val fact2 = SingleFact(buildConcept, 2000L, "Gradle 8.0")
        provider.save(fact1, subject, scope)
        provider.save(fact2, subject, scope)

        val loaded = provider.load(buildConcept, subject, scope)
        assertEquals(2, loaded.size)
        assertEquals("Gradle 7.0", (loaded[0] as SingleFact).value)
        assertEquals("Gradle 8.0", (loaded[1] as SingleFact).value)
    }

    @Test
    fun `loadAll returns all facts`() = runTest {
        val fact1 = SingleFact(buildConcept, 1000L, "Gradle 8.0")
        val fact2 = MultipleFacts(depsConcept, 2000L, listOf("kotlin-stdlib"))
        provider.save(fact1, subject, scope)
        provider.save(fact2, subject, scope)

        val all = provider.loadAll(subject, scope)
        assertEquals(2, all.size)
    }

    @Test
    fun `loadByDescription filters by concept description`() = runTest {
        val fact1 = SingleFact(buildConcept, 1000L, "Gradle 8.0")
        val fact2 = MultipleFacts(depsConcept, 2000L, listOf("kotlin-stdlib"))
        provider.save(fact1, subject, scope)
        provider.save(fact2, subject, scope)

        val results = provider.loadByDescription("build", subject, scope)
        assertEquals(1, results.size)
        assertEquals("Gradle 8.0", (results[0] as SingleFact).value)
    }

    @Test
    fun `load from empty returns empty list`() = runTest {
        val loaded = provider.load(buildConcept, subject, scope)
        assertTrue(loaded.isEmpty())
    }

    @Test
    fun `different scopes are isolated`() = runTest {
        val scope1 = MemoryScope.Agent("agent-1")
        val scope2 = MemoryScope.Agent("agent-2")
        val fact = SingleFact(buildConcept, 1000L, "Gradle 8.0")

        provider.save(fact, subject, scope1)

        val loaded1 = provider.load(buildConcept, subject, scope1)
        val loaded2 = provider.load(buildConcept, subject, scope2)
        assertEquals(1, loaded1.size)
        assertTrue(loaded2.isEmpty())
    }

    @Test
    fun `different subjects are isolated`() = runTest {
        val fact = SingleFact(buildConcept, 1000L, "Gradle 8.0")

        provider.save(fact, MemorySubject.User, scope)

        val loaded1 = provider.load(buildConcept, MemorySubject.User, scope)
        val loaded2 = provider.load(buildConcept, MemorySubject.Project, scope)
        assertEquals(1, loaded1.size)
        assertTrue(loaded2.isEmpty())
    }

    @Test
    fun `CrossProduct scope works`() = runTest {
        val fact = SingleFact(buildConcept, 1000L, "Gradle 8.0")
        provider.save(fact, subject, MemoryScope.CrossProduct)

        val loaded = provider.load(buildConcept, subject, MemoryScope.CrossProduct)
        assertEquals(1, loaded.size)
    }

    @Test
    fun `Feature scope works`() = runTest {
        val fact = SingleFact(buildConcept, 1000L, "Gradle 8.0")
        provider.save(fact, subject, MemoryScope.Feature("chat"))

        val loaded = provider.load(buildConcept, subject, MemoryScope.Feature("chat"))
        assertEquals(1, loaded.size)
    }

    @Test
    fun `Product scope works`() = runTest {
        val fact = SingleFact(buildConcept, 1000L, "Gradle 8.0")
        provider.save(fact, subject, MemoryScope.Product("jasmine"))

        val loaded = provider.load(buildConcept, subject, MemoryScope.Product("jasmine"))
        assertEquals(1, loaded.size)
    }

    @Test
    fun `encrypted storage works`() = runTest {
        // 简单的 Base64 加密用于测试
        val encryption = object : Encryption {
            override fun encrypt(text: String): String =
                java.util.Base64.getEncoder().encodeToString(text.toByteArray())
            override fun decrypt(text: String): String =
                String(java.util.Base64.getDecoder().decode(text))
        }
        val encryptedProvider = LocalFileMemoryProvider(
            config = LocalMemoryConfig("encrypted-memory"),
            storage = EncryptedMemoryStorage(encryption),
            rootDir = rootDir
        )

        val fact = SingleFact(buildConcept, 1000L, "Gradle 8.0")
        encryptedProvider.save(fact, subject, scope)

        val loaded = encryptedProvider.load(buildConcept, subject, scope)
        assertEquals(1, loaded.size)
        assertEquals("Gradle 8.0", (loaded[0] as SingleFact).value)
    }

    @Test
    fun `custom storage directory name`() = runTest {
        val customProvider = LocalFileMemoryProvider(rootDir, "custom-memory")
        val fact = SingleFact(buildConcept, 1000L, "Gradle 8.0")
        customProvider.save(fact, subject, scope)

        val loaded = customProvider.load(buildConcept, subject, scope)
        assertEquals(1, loaded.size)

        // 验证目录结构
        val expectedDir = File(rootDir, "custom-memory/agent/test-agent/subject/project")
        assertTrue(expectedDir.exists())
    }
}
