package com.lhzkml.jasmine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.lhzkml.jasmine.ui.theme.TextPrimary

@Composable
fun CustomDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.BottomEnd,
    /** 为 true 时，菜单底部对齐锚点顶部，菜单向上展开，不遮挡按钮 */
    alignAboveAnchor: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    if (expanded) {
        if (alignAboveAnchor) {
            Popup(
                popupPositionProvider = object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize
                    ): IntOffset {
                        return IntOffset(
                            x = anchorBounds.right - popupContentSize.width,
                            y = anchorBounds.top - popupContentSize.height
                        )
                    }
                },
                onDismissRequest = onDismissRequest,
                properties = PopupProperties(focusable = true)
            ) {
                CustomDropdownMenuContent(modifier, content)
            }
        } else {
            Popup(
                alignment = alignment,
                onDismissRequest = onDismissRequest,
                properties = PopupProperties(focusable = true)
            ) {
                CustomDropdownMenuContent(modifier, content)
            }
        }
    }
}

@Composable
private fun CustomDropdownMenuContent(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .width(IntrinsicSize.Max)
            .widthIn(max = 240.dp)
            .heightIn(max = 300.dp)
            .shadow(8.dp, RoundedCornerShape(8.dp))
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        content()
    }
}

@Composable
fun CustomDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        text()
    }
}
