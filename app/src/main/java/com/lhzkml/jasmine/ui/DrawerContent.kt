package com.lhzkml.jasmine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.core.conversation.storage.ConversationInfo
import com.lhzkml.jasmine.ProviderManager
import com.lhzkml.jasmine.ui.components.CustomText
import com.lhzkml.jasmine.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RightDrawerContent(
    conversations: List<ConversationInfo>,
    isEmpty: Boolean,
    onNewChat: () -> Unit,
    onConversationClick: (ConversationInfo) -> Unit,
    onConversationDelete: (ConversationInfo) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(BgPrimary)
    ) {
        CustomText(
            text = "＋ 新对话",
            color = Accent,
            fontSize = 16.sp,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable { onNewChat() }
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Divider)
        )

        CustomText(
            text = "历史对话",
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp)
        )

        Box(modifier = Modifier.weight(1f)) {
            if (isEmpty) {
                CustomText(
                    text = "暂无历史对话",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                ) {
                    items(conversations, key = { it.id }) { info ->
                        ConversationItem(
                            info = info,
                            onClick = { onConversationClick(info) },
                            onDelete = { onConversationDelete(info) }
                        )
                    }
                }
            }
        }

        CustomText(
            text = "⚙  设置",
            color = TextPrimary,
            fontSize = 22.sp,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clickable { onSettings() }
                .padding(horizontal = 16.dp, vertical = 16.dp)
        )
    }
}

@Composable
fun ConversationItem(
    info: ConversationInfo,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val providerName = ProviderManager.getAllProviders()
        .find { it.id == info.providerId }?.name ?: info.providerId
    val dateStr = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
        .format(Date(info.updatedAt))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            CustomText(
                text = info.title,
                color = TextPrimary,
                fontSize = 14.sp,
                maxLines = 1
            )
            CustomText(
                text = "$providerName · $dateStr",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        CustomText(
            text = "✕",
            color = TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier
                .clickable { onDelete() }
                .padding(6.dp)
        )
    }
}
