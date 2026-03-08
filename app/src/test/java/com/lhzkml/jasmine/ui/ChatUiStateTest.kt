package com.lhzkml.jasmine.ui

import org.junit.Assert.*
import org.junit.Test

class ChatUiStateTest {

    @Test
    fun `default state has expected values`() {
        val state = ChatUiState()
        assertFalse(state.isGenerating)
        assertEquals("", state.currentModel)
        assertEquals("", state.currentModelDisplay)
        assertTrue(state.modelList.isEmpty())
        assertFalse(state.isAgentMode)
        assertEquals("", state.workspacePath)
        assertEquals("", state.workspaceLabel)
        assertFalse(state.showFileTree)
        assertTrue(state.conversations.isEmpty())
        assertTrue(state.conversationsEmpty)
        assertFalse(state.userScrolledUp)
        assertEquals(0, state.scrollToBottomTrigger)
        assertNull(state.checkpointRecoveryDialog)
        assertNull(state.startupRecoveryDialog)
        assertNull(state.navigationEvent)
        assertNull(state.toastMessage)
        assertNull(state.error)
        assertFalse(state.isLoading)
        assertTrue(state.supportsThinkingMode.not())
        assertTrue(state.isThinkingModeEnabled)
    }

    @Test
    fun `copy preserves unmodified fields`() {
        val state = ChatUiState(
            isGenerating = true,
            currentModel = "gpt-4",
            isAgentMode = true,
            workspacePath = "/some/path"
        )
        val updated = state.copy(isGenerating = false)
        assertFalse(updated.isGenerating)
        assertEquals("gpt-4", updated.currentModel)
        assertTrue(updated.isAgentMode)
        assertEquals("/some/path", updated.workspacePath)
    }

    @Test
    fun `scrollToBottomTrigger increments correctly`() {
        var state = ChatUiState()
        state = state.copy(scrollToBottomTrigger = state.scrollToBottomTrigger + 1)
        assertEquals(1, state.scrollToBottomTrigger)
        state = state.copy(scrollToBottomTrigger = state.scrollToBottomTrigger + 1)
        assertEquals(2, state.scrollToBottomTrigger)
    }

    @Test
    fun `navigation event round trip`() {
        var state = ChatUiState()
        state = state.copy(navigationEvent = NavigationEvent.Settings)
        assertTrue(state.navigationEvent is NavigationEvent.Settings)

        state = state.copy(navigationEvent = null)
        assertNull(state.navigationEvent)
    }

    @Test
    fun `provider config navigation event carries data`() {
        val event = NavigationEvent.ProviderConfig("deepseek", tab = 1)
        assertEquals("deepseek", event.providerId)
        assertEquals(1, event.tab)
    }

    @Test
    fun `toast message round trip`() {
        var state = ChatUiState()
        state = state.copy(toastMessage = "Hello")
        assertEquals("Hello", state.toastMessage)

        state = state.copy(toastMessage = null)
        assertNull(state.toastMessage)
    }

    @Test
    fun `ChatUiEvent types are distinct`() {
        val events: List<ChatUiEvent> = listOf(
            ChatUiEvent.SendMessage("hi"),
            ChatUiEvent.StopGeneration,
            ChatUiEvent.SelectModel("gpt-4"),
            ChatUiEvent.SetThinkingMode(true),
            ChatUiEvent.LoadConversation("abc"),
            ChatUiEvent.NewConversation,
            ChatUiEvent.CloseWorkspace,
            ChatUiEvent.OpenSettings,
            ChatUiEvent.OpenDrawerEnd,
            ChatUiEvent.OpenDrawerStart,
            ChatUiEvent.UserScrolledUp(true),
            ChatUiEvent.ClearNavigationEvent,
            ChatUiEvent.ClearToastMessage
        )
        assertEquals(events.size, events.map { it::class }.distinct().size)
    }

    @Test
    fun `SendMessage carries text`() {
        val event = ChatUiEvent.SendMessage("hello world")
        assertEquals("hello world", event.text)
    }

    @Test
    fun `UserScrolledUp carries value`() {
        assertTrue(ChatUiEvent.UserScrolledUp(true).scrolledUp)
        assertFalse(ChatUiEvent.UserScrolledUp(false).scrolledUp)
    }
}
