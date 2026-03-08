package com.lhzkml.jasmine.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.LauncherActivity
import com.lhzkml.jasmine.ProviderConfigActivity
import com.lhzkml.jasmine.SettingsActivity
import com.lhzkml.jasmine.ui.components.CustomAlertDialog
import com.lhzkml.jasmine.ui.components.CustomText
import com.lhzkml.jasmine.ui.components.CustomTextButton
import com.lhzkml.jasmine.ui.components.CustomButtonDefaults
import com.lhzkml.jasmine.ui.theme.*

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 处理导航事件
    LaunchedEffect(uiState.navigationEvent) {
        uiState.navigationEvent?.let { event ->
            when (event) {
                is NavigationEvent.Settings -> {
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                }
                is NavigationEvent.ProviderConfig -> {
                    context.startActivity(Intent(context, ProviderConfigActivity::class.java).apply {
                        putExtra("provider_id", event.providerId)
                        putExtra("tab", event.tab)
                    })
                }
                is NavigationEvent.Launcher -> {
                    val intent = Intent(context, LauncherActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    (context as? android.app.Activity)?.finish()
                }
            }
            viewModel.onEvent(ChatUiEvent.ClearNavigationEvent)
        }
    }

    // 处理 Toast 消息
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.onEvent(ChatUiEvent.ClearToastMessage)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
        TopBar(
            isAgentMode = uiState.isAgentMode,
            onMenuClick = { viewModel.onEvent(ChatUiEvent.OpenDrawerEnd) },
            onFileTreeClick = { viewModel.onEvent(ChatUiEvent.OpenDrawerStart) }
        )

        if (uiState.workspaceLabel.isNotEmpty()) {
            WorkspaceBar(
                label = uiState.workspaceLabel,
                isAgent = uiState.isAgentMode,
                onClose = { viewModel.onEvent(ChatUiEvent.CloseWorkspace) }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Divider)
        )

        if (viewModel.chatItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                ChatInputBar(
                    isGenerating = uiState.isGenerating,
                    currentModelDisplay = uiState.currentModelDisplay,
                    modelList = uiState.modelList,
                    currentModel = uiState.currentModel,
                    onSend = { viewModel.onEvent(ChatUiEvent.SendMessage(it)) },
                    onStop = { viewModel.onEvent(ChatUiEvent.StopGeneration) },
                    onModelSelected = { viewModel.onEvent(ChatUiEvent.SelectModel(it)) },
                    shortenModelName = { viewModel.shortenModelName(it) },
                    supportsThinkingMode = uiState.supportsThinkingMode,
                    isThinkingModeEnabled = uiState.isThinkingModeEnabled,
                    onThinkingModeChanged = if (uiState.supportsThinkingMode) {
                        { enabled -> viewModel.onEvent(ChatUiEvent.SetThinkingMode(enabled)) }
                    } else null,
                    modifier = Modifier.offset(y = 24.dp)
                )
            }
        } else {
            ChatMessageList(
                items = viewModel.chatItems,
                isGenerating = uiState.isGenerating,
                scrollTrigger = uiState.scrollToBottomTrigger,
                onUserScrollUp = { viewModel.onEvent(ChatUiEvent.UserScrolledUp(true)) },
                onReachBottom = { viewModel.onEvent(ChatUiEvent.UserScrolledUp(false)) },
                modifier = Modifier.weight(1f)
            )

            ChatInputBar(
                isGenerating = uiState.isGenerating,
                currentModelDisplay = uiState.currentModelDisplay,
                modelList = uiState.modelList,
                currentModel = uiState.currentModel,
                onSend = { viewModel.onEvent(ChatUiEvent.SendMessage(it)) },
                onStop = { viewModel.onEvent(ChatUiEvent.StopGeneration) },
                onModelSelected = { viewModel.onEvent(ChatUiEvent.SelectModel(it)) },
                shortenModelName = { viewModel.shortenModelName(it) },
                supportsThinkingMode = uiState.supportsThinkingMode,
                isThinkingModeEnabled = uiState.isThinkingModeEnabled,
                onThinkingModeChanged = if (uiState.supportsThinkingMode) {
                    { enabled -> viewModel.onEvent(ChatUiEvent.SetThinkingMode(enabled)) }
                } else null
            )
        }
    }

    uiState.checkpointRecoveryDialog?.let { state ->
        CustomAlertDialog(
            onDismissRequest = { state.onSelect(null) },
            containerColor = Color.White,
            title = { CustomText(state.title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    CustomText(text = state.message, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.padding(bottom = 8.dp))
                    state.labels.forEachIndexed { index, label ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { state.onSelect(index) }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            CustomText(text = label, fontSize = 14.sp, color = TextPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                CustomTextButton(
                    onClick = { state.onSelect(null) },
                    colors = CustomButtonDefaults.textButtonColors(contentColor = TextSecondary)
                ) { CustomText("取消", fontSize = 14.sp) }
            },
            dismissButton = null
        )
    }

    uiState.startupRecoveryDialog?.let { state ->
        CustomAlertDialog(
            onDismissRequest = { state.onConfirm(false) },
            containerColor = Color.White,
            title = { CustomText(state.title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = { CustomText(state.message, color = TextPrimary, fontSize = 14.sp) },
            confirmButton = {
                CustomTextButton(
                    onClick = { state.onConfirm(true) },
                    colors = CustomButtonDefaults.textButtonColors(contentColor = Accent)
                ) { CustomText("恢复", fontSize = 14.sp) }
            },
            dismissButton = {
                CustomTextButton(
                    onClick = { state.onConfirm(false) },
                    colors = CustomButtonDefaults.textButtonColors(contentColor = TextSecondary)
                ) { CustomText("忽略", fontSize = 14.sp) }
            }
        )
    }

    uiState.toolDialog?.let { dialog ->
        ToolDialogComposable(dialog)
    }
}

@Composable
fun TopBar(
    isAgentMode: Boolean,
    onMenuClick: () -> Unit,
    onFileTreeClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(BgPrimary)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CustomText(
            text = "Jasmine",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )

        if (isAgentMode) {
            CustomText(
                text = "📁",
                fontSize = 16.sp,
                modifier = Modifier
                    .clickable { onFileTreeClick() }
                    .padding(end = 8.dp)
            )
        }

        CustomText(
            text = "☰",
            fontSize = 20.sp,
            color = TextPrimary,
            modifier = Modifier.clickable { onMenuClick() }
        )
    }
}

@Composable
fun WorkspaceBar(
    label: String,
    isAgent: Boolean,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(AccentLight)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CustomText(
            text = label,
            color = TextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        CustomText(
            text = if (isAgent) "关闭" else "退出",
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.clickable { onClose() }
        )
    }
}

@Composable
private fun ToolDialogComposable(state: ToolDialogState) {
    when (state) {
        is ToolDialogState.ShellConfirmation -> {
            CustomAlertDialog(
                onDismissRequest = { state.onResult(false) },
                containerColor = Color.White,
                title = { CustomText("执行命令确认", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        CustomText("目的：${state.purpose}", fontSize = 14.sp, color = TextPrimary, modifier = Modifier.padding(bottom = 8.dp))
                        CustomText("命令：${state.command}", fontSize = 13.sp, color = TextSecondary)
                    }
                },
                confirmButton = {
                    CustomTextButton(onClick = { state.onResult(true) }, colors = CustomButtonDefaults.textButtonColors(contentColor = Accent)) {
                        CustomText("允许", fontSize = 14.sp)
                    }
                },
                dismissButton = {
                    CustomTextButton(onClick = { state.onResult(false) }, colors = CustomButtonDefaults.textButtonColors(contentColor = TextSecondary)) {
                        CustomText("拒绝", fontSize = 14.sp)
                    }
                }
            )
        }

        is ToolDialogState.AskUser -> {
            var input by remember { mutableStateOf("") }
            CustomAlertDialog(
                onDismissRequest = { state.onResult("(用户取消)") },
                containerColor = Color.White,
                title = { CustomText("AI 询问", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        CustomText(state.question, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.padding(bottom = 12.dp))
                        BasicTextField(
                            value = input,
                            onValueChange = { input = it },
                            textStyle = TextStyle(fontSize = 14.sp, color = TextPrimary),
                            cursorBrush = SolidColor(Accent),
                            modifier = Modifier.fillMaxWidth().border(1.dp, Divider, RoundedCornerShape(8.dp)).padding(12.dp)
                        )
                    }
                },
                confirmButton = {
                    CustomTextButton(onClick = { state.onResult(input) }, colors = CustomButtonDefaults.textButtonColors(contentColor = Accent)) {
                        CustomText("发送", fontSize = 14.sp)
                    }
                },
                dismissButton = {
                    CustomTextButton(onClick = { state.onResult("(用户取消)") }, colors = CustomButtonDefaults.textButtonColors(contentColor = TextSecondary)) {
                        CustomText("取消", fontSize = 14.sp)
                    }
                }
            )
        }

        is ToolDialogState.SingleSelect -> {
            var selectedIndex by remember { mutableStateOf(-1) }
            CustomAlertDialog(
                onDismissRequest = { state.onResult("(用户取消)") },
                containerColor = Color.White,
                title = { CustomText(state.question, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        state.options.forEachIndexed { index, option ->
                            val isSelected = index == selectedIndex
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedIndex = index }
                                    .background(if (isSelected) AccentLight else Color.Transparent)
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                CustomText(option, fontSize = 14.sp, color = if (isSelected) Accent else TextPrimary)
                            }
                        }
                    }
                },
                confirmButton = {
                    CustomTextButton(onClick = {
                        state.onResult(if (selectedIndex >= 0) state.options[selectedIndex] else "(未选择)")
                    }, colors = CustomButtonDefaults.textButtonColors(contentColor = Accent)) {
                        CustomText("确定", fontSize = 14.sp)
                    }
                },
                dismissButton = {
                    CustomTextButton(onClick = { state.onResult("(用户取消)") }, colors = CustomButtonDefaults.textButtonColors(contentColor = TextSecondary)) {
                        CustomText("取消", fontSize = 14.sp)
                    }
                }
            )
        }

        is ToolDialogState.MultiSelect -> {
            val selected = remember { mutableStateListOf(*BooleanArray(state.options.size) { false }.toTypedArray()) }
            CustomAlertDialog(
                onDismissRequest = { state.onResult(listOf("(用户取消)")) },
                containerColor = Color.White,
                title = { CustomText(state.question, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        state.options.forEachIndexed { index, option ->
                            val isChecked = selected[index]
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selected[index] = !isChecked }
                                    .background(if (isChecked) AccentLight else Color.Transparent)
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CustomText(if (isChecked) "☑" else "☐", fontSize = 16.sp, color = if (isChecked) Accent else TextSecondary, modifier = Modifier.padding(end = 12.dp))
                                    CustomText(option, fontSize = 14.sp, color = TextPrimary)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    CustomTextButton(onClick = {
                        val result = state.options.filterIndexed { i, _ -> selected[i] }
                        state.onResult(result.ifEmpty { listOf("(未选择)") })
                    }, colors = CustomButtonDefaults.textButtonColors(contentColor = Accent)) {
                        CustomText("确定", fontSize = 14.sp)
                    }
                },
                dismissButton = {
                    CustomTextButton(onClick = { state.onResult(listOf("(用户取消)")) }, colors = CustomButtonDefaults.textButtonColors(contentColor = TextSecondary)) {
                        CustomText("取消", fontSize = 14.sp)
                    }
                }
            )
        }

        is ToolDialogState.RankPriorities -> {
            val ranked = remember { state.items.toMutableStateList() }
            CustomAlertDialog(
                onDismissRequest = { state.onResult(state.items) },
                containerColor = Color.White,
                title = { CustomText("排序优先级", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        CustomText("${state.question}\n\n点击项目向上移动", fontSize = 14.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            ranked.forEachIndexed { index, item ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (index > 0) {
                                                val moved = ranked.removeAt(index)
                                                ranked.add(index - 1, moved)
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                ) {
                                    CustomText("${index + 1}. $item", fontSize = 14.sp, color = TextPrimary)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    CustomTextButton(onClick = { state.onResult(ranked.toList()) }, colors = CustomButtonDefaults.textButtonColors(contentColor = Accent)) {
                        CustomText("确定", fontSize = 14.sp)
                    }
                },
                dismissButton = {
                    CustomTextButton(onClick = { state.onResult(state.items) }, colors = CustomButtonDefaults.textButtonColors(contentColor = TextSecondary)) {
                        CustomText("取消", fontSize = 14.sp)
                    }
                }
            )
        }

        is ToolDialogState.AskMultipleQuestions -> {
            val answers = remember { mutableStateListOf(*Array(state.questions.size) { "" }) }
            CustomAlertDialog(
                onDismissRequest = { state.onResult(List(state.questions.size) { "(用户取消)" }) },
                containerColor = Color.White,
                title = { CustomText("AI 询问 (${state.questions.size} 个问题)", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        state.questions.forEachIndexed { index, question ->
                            CustomText(question, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.padding(bottom = 4.dp, top = if (index > 0) 12.dp else 0.dp))
                            BasicTextField(
                                value = answers[index],
                                onValueChange = { answers[index] = it },
                                textStyle = TextStyle(fontSize = 14.sp, color = TextPrimary),
                                cursorBrush = SolidColor(Accent),
                                modifier = Modifier.fillMaxWidth().border(1.dp, Divider, RoundedCornerShape(8.dp)).padding(12.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    CustomTextButton(onClick = {
                        state.onResult(answers.map { it.ifEmpty { "(无回复)" } })
                    }, colors = CustomButtonDefaults.textButtonColors(contentColor = Accent)) {
                        CustomText("发送", fontSize = 14.sp)
                    }
                },
                dismissButton = {
                    CustomTextButton(onClick = {
                        state.onResult(List(state.questions.size) { "(用户取消)" })
                    }, colors = CustomButtonDefaults.textButtonColors(contentColor = TextSecondary)) {
                        CustomText("取消", fontSize = 14.sp)
                    }
                }
            )
        }
    }
}
