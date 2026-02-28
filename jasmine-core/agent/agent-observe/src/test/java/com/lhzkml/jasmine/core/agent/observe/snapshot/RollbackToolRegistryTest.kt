package com.lhzkml.jasmine.core.agent.observe.snapshot

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RollbackToolRegistryTest {

    @Test
    fun `EMPTY registry has no tools`() {
        assertNull(RollbackToolRegistry.EMPTY.getRollbackTool("writeFile"))
        assertTrue(RollbackToolRegistry.EMPTY.rollbackToolsMap.isEmpty())
    }

    @Test
    fun `DSL builder registers rollback tools`() {
        val registry = RollbackToolRegistry {
            registerRollback("writeFile", "deleteFile") { }
            registerRollback("createDir", "removeDir") { }
        }
        assertNotNull(registry.getRollbackTool("writeFile"))
        assertEquals("deleteFile", registry.getRollbackTool("writeFile")!!.rollbackToolName)
        assertEquals("removeDir", registry.getRollbackTool("createDir")!!.rollbackToolName)
        assertNull(registry.getRollbackTool("readFile"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate registration throws`() {
        RollbackToolRegistry {
            registerRollback("writeFile", "deleteFile") { }
            registerRollback("writeFile", "undoWrite") { }
        }
    }

    @Test
    fun `plus merges two registries`() {
        val r1 = RollbackToolRegistry { registerRollback("a", "undo_a") { } }
        val r2 = RollbackToolRegistry { registerRollback("b", "undo_b") { } }
        val merged = r1 + r2
        assertNotNull(merged.getRollbackTool("a"))
        assertNotNull(merged.getRollbackTool("b"))
    }

    @Test
    fun `add does not overwrite existing`() {
        val registry = RollbackToolRegistry { registerRollback("a", "undo_a") { } }
        registry.add("a", RollbackToolRegistry.RollbackToolEntry("a", "different", {}))
        assertEquals("undo_a", registry.getRollbackTool("a")!!.rollbackToolName)
    }

    @Test
    fun `rollback executor is invoked`() = runBlocking {
        var executed = false
        val registry = RollbackToolRegistry {
            registerRollback("writeFile", "deleteFile") { executed = true }
        }
        registry.getRollbackTool("writeFile")!!.rollbackExecutor("args")
        assertTrue(executed)
    }
}
