package com.lhzkml.jasmine.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.lhzkml.jasmine.core.conversation.storage.ConversationInfo
import com.lhzkml.jasmine.ui.components.CustomAlertDialog
import com.lhzkml.jasmine.ui.components.CustomText
import com.lhzkml.jasmine.ui.components.CustomTextButton
import com.lhzkml.jasmine.ui.components.CustomButtonDefaults
import com.lhzkml.jasmine.ui.theme.Accent
import com.lhzkml.jasmine.ui.theme.BgPrimary
import com.lhzkml.jasmine.ui.theme.Divider
import com.lhzkml.jasmine.ui.theme.TextPrimary
import com.lhzkml.jasmine.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.io.File

private val DrawerWidth = 280.dp

@Composable
fun MainScreen(
    viewModel: ChatViewModel,
    onNavigateToSettings: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val drawerStateEnd = rememberDrawerState(DrawerValue.Closed)
    val drawerStateStart = rememberDrawerState(DrawerValue.Closed)
    var conversationToDelete by remember { mutableStateOf<ConversationInfo?>(null) }

    // 响应 ViewModel 的抽屉打开请求
    LaunchedEffect(uiState.requestOpenDrawerEnd) {
        if (uiState.requestOpenDrawerEnd) {
            drawerStateEnd.open()
            viewModel.onEvent(ChatUiEvent.ClearDrawerRequestEnd)
        }
    }
    LaunchedEffect(uiState.requestOpenDrawerStart) {
        if (uiState.requestOpenDrawerStart) {
            drawerStateStart.open()
            viewModel.onEvent(ChatUiEvent.ClearDrawerRequestStart)
        }
    }

    // 嵌套：外层右抽屉（RTL 使抽屉在右侧），内层左抽屉
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    ModalNavigationDrawer(
        drawerState = drawerStateEnd,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(DrawerWidth)) {
                RightDrawerContent(
                    conversations = uiState.conversations,
                    isEmpty = uiState.conversationsEmpty,
                    onNewChat = {
                        viewModel.onEvent(ChatUiEvent.NewConversation)
                        scope.launch { drawerStateEnd.close() }
                    },
                    onConversationClick = { info ->
                        viewModel.onEvent(ChatUiEvent.LoadConversation(info.id))
                        scope.launch { drawerStateEnd.close() }
                    },
                    onConversationDelete = { info -> conversationToDelete = info },
                    onSettings = {
                        scope.launch { drawerStateEnd.close() }
                        if (onNavigateToSettings != null) {
                            onNavigateToSettings()
                        } else {
                            viewModel.onEvent(ChatUiEvent.OpenSettings)
                        }
                    }
                )
            }
        },
        gesturesEnabled = true,
        modifier = Modifier.fillMaxSize()
    ) {
        ModalNavigationDrawer(
            drawerState = drawerStateStart,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.width(DrawerWidth)) {
                    FileTreeDrawerContent(
                        rootPath = uiState.workspacePath,
                        rootName = if (uiState.workspacePath.isNotEmpty())
                            File(uiState.workspacePath).name else "",
                        onFileClick = { file ->
                            Toast.makeText(context, file.absolutePath, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            },
            gesturesEnabled = uiState.showFileTree,
            modifier = Modifier.fillMaxSize()
        ) {
            ChatScreen(viewModel = viewModel)
        }
    }
    }

    // 删除对话确认对话框
    conversationToDelete?.let { info ->
        CustomAlertDialog(
            onDismissRequest = { conversationToDelete = null },
            containerColor = androidx.compose.ui.graphics.Color.White,
            title = { CustomText("删除对话", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = { CustomText("确定删除这个对话吗？", color = TextPrimary, fontSize = 14.sp) },
            confirmButton = {
                CustomTextButton(
                    onClick = {
                        viewModel.onEvent(ChatUiEvent.DeleteConversation(info))
                        conversationToDelete = null
                    },
                    colors = CustomButtonDefaults.textButtonColors(contentColor = Accent)
                ) { CustomText("删除", fontSize = 14.sp) }
            },
            dismissButton = {
                CustomTextButton(
                    onClick = { conversationToDelete = null },
                    colors = CustomButtonDefaults.textButtonColors(contentColor = TextPrimary)
                ) { CustomText("取消", fontSize = 14.sp) }
            }
        )
    }
}

@Composable
private fun FileTreeDrawerContent(
    rootPath: String,
    rootName: String,
    onFileClick: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(BgPrimary)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            CustomText(
                text = "资源管理器",
                color = TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Divider)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            CustomText(
                text = rootName.ifEmpty { "未选择工作区" },
                color = Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            FileTreeComposable(
                rootPath = rootPath,
                onFileClick = onFileClick,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
