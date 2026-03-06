package com.lhzkml.jasmine.ui

import android.graphics.drawable.AnimationDrawable
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.R
import com.lhzkml.jasmine.ui.components.CustomDropdownMenu
import com.lhzkml.jasmine.ui.components.CustomDropdownMenuItem
import com.lhzkml.jasmine.ui.components.CustomModalBottomSheet
import com.lhzkml.jasmine.ui.components.CustomText
import com.lhzkml.jasmine.ui.theme.*

@Composable
fun ChatInputBar(
    isGenerating: Boolean,
    currentModelDisplay: String,
    modelList: List<String>,
    currentModel: String,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onModelSelected: (String) -> Unit,
    shortenModelName: (String) -> String,
    supportsThinkingMode: Boolean = false,
    isThinkingModeEnabled: Boolean = true,
    onThinkingModeChanged: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BgPrimary)
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(136.dp)
                .border(
                    width = 0.5.dp,
                    color = Divider,
                    shape = RoundedCornerShape(16.dp)
                )
                .background(BgInput, RoundedCornerShape(16.dp))
                .padding(10.dp)
        ) {
            // 顶部：输入框 + 发送按钮（固定顶部，输入框高度受限）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart),
                verticalAlignment = Alignment.Top
            ) {
                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontSize = 15.sp
                    ),
                    cursorBrush = SolidColor(Accent),
                    maxLines = 6,
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 36.dp)
                        ) {
                            if (inputText.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    CustomText("输入消息...", color = TextSecondary, fontSize = 15.sp)
                                }
                            }
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.TopStart
                            ) {
                                innerTextField()
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 36.dp, max = 72.dp)
                        .padding(end = 8.dp)
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .then(if (!isGenerating) Modifier.background(Accent) else Modifier)
                        .clickable {
                            if (isGenerating) {
                                onStop()
                            } else {
                                val msg = inputText.trim()
                                if (msg.isNotEmpty()) {
                                    onSend(msg)
                                    inputText = ""
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isGenerating) {
                        AndroidView(
                            factory = { ctx ->
                                ImageView(ctx).apply {
                                    setImageResource(R.drawable.stop_button_animated)
                                    scaleType = ImageView.ScaleType.CENTER_CROP
                                    (drawable as? AnimationDrawable)?.start()
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    CustomText(
                        text = if (isGenerating) "■" else "↑",
                        color = BgPrimary,
                        fontSize = 16.sp
                    )
                }
            }

            // 底部：加号按钮（固定） + 模型切换（固定底部）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(TextSecondary.copy(alpha = 0.3f))
                        .clickable { showSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    CustomText(text = "+", color = TextPrimary, fontSize = 20.sp)
                }
                Box(modifier = Modifier.padding(end = 2.dp, bottom = 2.dp)) {
                    CustomText(
                        text = currentModelDisplay,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable {
                            if (modelList.size > 1) modelMenuExpanded = true
                        }
                    )
                    CustomDropdownMenu(
                        expanded = modelMenuExpanded,
                        onDismissRequest = { modelMenuExpanded = false },
                        alignment = Alignment.BottomEnd,
                        alignAboveAnchor = true
                    ) {
                        modelList.forEach { model ->
                            CustomDropdownMenuItem(
                                text = {
                                    CustomText(
                                        text = shortenModelName(model),
                                        color = if (model == currentModel) Accent else TextPrimary,
                                        fontSize = 14.sp
                                    )
                                },
                                onClick = {
                                    onModelSelected(model)
                                    modelMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    ChatInputBarSheet(
        showSheet = showSheet,
        onDismissRequest = { showSheet = false },
        supportsThinkingMode = supportsThinkingMode,
        isThinkingModeEnabled = isThinkingModeEnabled,
        isGenerating = isGenerating,
        onThinkingModeChanged = onThinkingModeChanged
    )
}

@Composable
private fun ChatInputBarSheet(
    showSheet: Boolean,
    onDismissRequest: () -> Unit,
    supportsThinkingMode: Boolean,
    isThinkingModeEnabled: Boolean,
    isGenerating: Boolean,
    onThinkingModeChanged: ((Boolean) -> Unit)?
) {
    if (showSheet) {
        CustomModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetMinHeight = 200.dp,
            sheetMaxHeightFraction = 0.92f,
            dragFromContentArea = true
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (supportsThinkingMode && onThinkingModeChanged != null) {
                    Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .border(
                            width = 1.dp,
                            color = if (isThinkingModeEnabled) Accent else TextSecondary.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .background(
                            if (isThinkingModeEnabled) Accent.copy(alpha = 0.15f)
                            else Color.Transparent
                        )
                        .clickable(enabled = !isGenerating) {
                            onThinkingModeChanged(!isThinkingModeEnabled)
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_think),
                        contentDescription = "深度思考",
                        modifier = Modifier.size(18.dp),
                        tint = if (isThinkingModeEnabled) Accent else TextSecondary
                    )
                    CustomText(
                        text = "Thinking",
                        fontSize = 14.sp,
                        color = if (isThinkingModeEnabled) Accent else TextSecondary,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
                }
                if (!supportsThinkingMode) {
                    Spacer(modifier = Modifier.height(16.dp))
                    CustomText(
                        text = "更多功能即将推出",
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
