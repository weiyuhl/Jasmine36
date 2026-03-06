package com.lhzkml.jasmine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lhzkml.jasmine.ui.theme.BgPrimary
import com.lhzkml.jasmine.ui.theme.TextSecondary

/**
 * 自定义 ModalBottomSheet，不依赖 Material 主题。
 * 使用纯 Compose 实现：遮罩层 + 底部弹出的面板。
 *
 * @param onDismissRequest 点击遮罩或返回键时回调
 * @param modifier 应用于 sheet 容器的 Modifier
 * @param sheetMaxHeight 面板最大高度（默认屏幕 90%）
 * @param showDragHandle 是否显示顶部拖拽条
 * @param scrimColor 遮罩颜色
 * @param sheetColor 面板背景色
 * @param content sheet 内容（超出最大高度时可滚动）
 */
@Composable
fun CustomModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetMaxHeight: Dp = Dp.Unspecified,
    showDragHandle: Boolean = true,
    scrimColor: Color = Color.Black.copy(alpha = 0.5f),
    sheetColor: Color = BgPrimary,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val maxH = if (sheetMaxHeight != Dp.Unspecified) sheetMaxHeight else maxHeight * 0.9f

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimColor)
                    .clickable { onDismissRequest() }
            )

            Column(
                modifier = modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(max = maxH)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(sheetColor)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (showDragHandle) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 8.dp)
                            .height(4.dp)
                            .fillMaxWidth(0.15f)
                            .clip(RoundedCornerShape(2.dp))
                            .background(TextSecondary.copy(alpha = 0.5f))
                    )
                }
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * 可固定高度的 sheet（内容不滚动时使用）。
 * 适合选项列表等固定内容。
 */
@Composable
fun CustomModalBottomSheetFixed(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetHeight: androidx.compose.ui.unit.Dp = 300.dp,
    showDragHandle: Boolean = true,
    scrimColor: Color = Color.Black.copy(alpha = 0.5f),
    sheetColor: Color = BgPrimary,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimColor)
                    .clickable { onDismissRequest() }
            )

            Column(
                modifier = modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(sheetHeight)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(sheetColor)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (showDragHandle) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 8.dp)
                            .height(4.dp)
                            .fillMaxWidth(0.15f)
                            .clip(RoundedCornerShape(2.dp))
                            .background(TextSecondary.copy(alpha = 0.5f))
                    )
                }
                content()
            }
        }
    }
}
