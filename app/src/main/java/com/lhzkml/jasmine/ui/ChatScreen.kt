package com.lhzkml.jasmine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.MainActivity
import com.lhzkml.jasmine.ui.components.CustomAlertDialog
import com.lhzkml.jasmine.ui.components.CustomText
import com.lhzkml.jasmine.ui.components.CustomTextButton
import com.lhzkml.jasmine.ui.components.CustomButtonDefaults
import com.lhzkml.jasmine.ui.theme.*

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val context = LocalContext.current
    val activity = context as? MainActivity

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
    ) {
        TopBar(
            modeLabelText = viewModel.modeLabelText,
            isAgentMode = viewModel.isAgentMode,
            onMenuClick = { activity?.openDrawerEnd() },
            onFileTreeClick = { activity?.openDrawerStart() }
        )

        if (viewModel.workspaceLabel.isNotEmpty()) {
            WorkspaceBar(
                label = viewModel.workspaceLabel,
                isAgent = viewModel.isAgentMode,
                onClose = { viewModel.closeWorkspace() }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Divider)
        )

        ChatMessageList(
            items = viewModel.chatItems,
            isGenerating = viewModel.isGenerating,
            scrollTrigger = viewModel.scrollToBottomTrigger,
            onUserScrollUp = { viewModel.userScrolledUp = true },
            onReachBottom = { viewModel.userScrolledUp = false },
            modifier = Modifier.weight(1f)
        )

        ChatInputBar(
            isGenerating = viewModel.isGenerating,
            currentModelDisplay = viewModel.currentModelDisplay,
            modelList = viewModel.modelList,
            currentModel = viewModel.currentModel,
            onSend = { viewModel.sendMessage(it) },
            onStop = { viewModel.stopGenerating() },
            onModelSelected = { viewModel.selectModel(it) },
            shortenModelName = { viewModel.shortenModelName(it) }
        )
    }

    viewModel.checkpointRecoveryDialog?.let { state ->
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

    viewModel.startupRecoveryDialog?.let { state ->
        CustomAlertDialog(
            onDismissRequest = { state.onConfirm(false) },
            containerColor = Color.White,
            title = { CustomText(state.title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = { CustomText(state.message, color = TextPrimary, fontSize = 14.sp) },
            confirmButton = {
                CustomTextButton(
                    onClick = { state.onConfirm(true) },
                    colors = CustomButtonDefaults.textButtonColors(contentColor = Color(0xFF2196F3))
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
}

@Composable
fun TopBar(
    modeLabelText: String,
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

        Box(
            modifier = Modifier
                .background(
                    if (isAgentMode) Accent else AccentLight,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            CustomText(
                text = modeLabelText,
                color = if (isAgentMode) BgPrimary else TextSecondary,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

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
