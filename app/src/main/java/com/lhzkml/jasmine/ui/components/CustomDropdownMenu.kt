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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.lhzkml.jasmine.ui.theme.TextPrimary

@Composable
fun CustomDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.BottomEnd,
    content: @Composable ColumnScope.() -> Unit
) {
    if (expanded) {
        Popup(
            alignment = alignment,
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(focusable = true)
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
