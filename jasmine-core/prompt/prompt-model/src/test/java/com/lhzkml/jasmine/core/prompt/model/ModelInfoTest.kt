package com.lhzkml.jasmine.core.prompt.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ModelInfoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `deserialize model list response`() {
        val jsonStr = """
        {
            "object": "list",
            "data": [
                {"id": "deepseek-chat", "object": "model", "owned_by": "deepseek"},
                {"id": "deepseek-coder", "object": "model", "owned_by": "deepseek"}
            ]
        }
        """.trimIndent()

        val response = json.decodeFromString(ModelListResponse.serializer(), jsonStr)
        assertEquals("list", response.objectType)
        assertEquals(2, response.data.size)
        assertEquals("deepseek-chat", response.data[0].id)
        assertEquals("deepseek", response.data[0].ownedBy)
    }

    @Test
    fun `deserialize empty model list`() {
        val jsonStr = """{"object": "list", "data": []}"""
        val response = json.decodeFromString(ModelListResponse.serializer(), jsonStr)
        assertTrue(response.data.isEmpty())
    }

    @Test
    fun `model info defaults`() {
        val model = ModelInfo(id = "test-model")
        assertEquals("model", model.objectType)
        assertEquals("", model.ownedBy)
    }
}
