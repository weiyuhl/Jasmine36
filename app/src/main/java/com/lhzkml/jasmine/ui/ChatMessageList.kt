package com.lhzkml.jasmine.ui

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.ChatItem

@Composable
fun ChatMessageList(
    items: List<ChatItem>,
    isGenerating: Boolean,
    scrollTrigger: Int,
    onUserScrollUp: () -> Unit,
    onReachBottom: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var autoScrollEnabled by remember { mutableStateOf(true) }
    var userDragging by remember { mutableStateOf(false) }
    val currentIsGenerating by rememberUpdatedState(isGenerating)
    var lastScrollTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(isGenerating) {
        if (isGenerating) {
            autoScrollEnabled = true
            userDragging = false
            onReachBottom()
        }
    }

    LaunchedEffect(scrollTrigger) {
        if (scrollTrigger != lastScrollTrigger) {
            lastScrollTrigger = scrollTrigger
            if (items.isNotEmpty() && autoScrollEnabled) {
                val lastIdx = items.lastIndex
                val info = listState.layoutInfo
                val lastItem = info.visibleItemsInfo.lastOrNull { it.index == lastIdx }
                if (lastItem != null) {
                    val overflow = (lastItem.offset + lastItem.size) - info.viewportEndOffset
                    if (overflow > 0) {
                        listState.scrollBy(overflow.toFloat())
                    }
                }
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemScrollOffset to listState.firstVisibleItemIndex }
            .collect { (_, _) ->
                if (listState.isScrollInProgress && currentIsGenerating) {
                    val info = listState.layoutInfo
                    val lastVisible = info.visibleItemsInfo.lastOrNull()
                    val total = info.totalItemsCount
                    if (lastVisible != null && total > 0) {
                        val contentEnd = lastVisible.offset + lastVisible.size
                        val viewEnd = info.viewportEndOffset
                        val nearBottom = lastVisible.index >= total - 1 && contentEnd <= viewEnd + 100
                        if (!nearBottom) {
                            userDragging = true
                            autoScrollEnabled = false
                            onUserScrollUp()
                        } else {
                            userDragging = false
                            autoScrollEnabled = true
                            onReachBottom()
                        }
                    }
                }
                if (!listState.isScrollInProgress && userDragging) {
                    userDragging = false
                    val info = listState.layoutInfo
                    val lastVisible = info.visibleItemsInfo.lastOrNull()
                    val total = info.totalItemsCount
                    if (lastVisible != null && total > 0) {
                        val contentEnd = lastVisible.offset + lastVisible.size
                        val viewEnd = info.viewportEndOffset
                        if (lastVisible.index >= total - 1 && contentEnd <= viewEnd + 100) {
                            autoScrollEnabled = true
                            onReachBottom()
                        }
                    }
                }
            }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
        modifier = modifier.fillMaxSize()
    ) {
        itemsIndexed(items, key = { index, item ->
            when (item) {
                is ChatItem.UserMessage -> "user_$index"
                is ChatItem.AiMessage -> "ai_$index"
                is ChatItem.TypingIndicator -> "typing"
            }
        }) { _, item ->
            when (item) {
                is ChatItem.UserMessage -> UserBubble(item)
                is ChatItem.AiMessage -> AiBubble(item)
                is ChatItem.TypingIndicator -> TypingIndicator()
            }
        }
    }
}
