package com.lhzkml.jasmine.core.prompt.llm

import org.junit.Assert.*
import org.junit.Test

class JasmineCoreTest {

    @Test
    fun `version is not empty`() {
        assertTrue(JasmineCore.VERSION.isNotEmpty())
    }

    @Test
    fun `name is Jasmine`() {
        assertEquals("Jasmine", JasmineCore.NAME)
    }
}
