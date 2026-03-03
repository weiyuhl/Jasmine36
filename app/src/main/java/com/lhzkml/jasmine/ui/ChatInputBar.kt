package com.lhzkml.jasmine.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.ui.components.CustomDropdownMenu
import com.lhzkml.jasmine.ui.components.CustomDropdownMenuItem
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
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    var modelMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BgPrimary)
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(BgInput, RoundedCornerShape(16.dp))
                .padding(10.dp)
        ) {
            Row(modifier = Modifier.matchParentSize()) {
                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontSize = 15.sp
                    ),
                    cursorBrush = SolidColor(Accent),
                    maxLines = 4,
                    decorationBox = { innerTextField ->
                        Box {
                            if (inputText.isEmpty()) {
                                CustomText("输入消息...", color = TextSecondary, fontSize = 15.sp)
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )

                Column(horizontalAlignment = Alignment.End) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isGenerating) GeneratingGreen else Accent)
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
                        CustomText(
                            text = if (isGenerating) "■" else "↑",
                            color = BgPrimary,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 2.dp, bottom = 2.dp)
            ) {
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
                    alignment = Alignment.TopEnd
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
