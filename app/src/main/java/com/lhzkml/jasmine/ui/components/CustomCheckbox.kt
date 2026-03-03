package com.lhzkml.jasmine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.ui.theme.JasmineTheme

@Composable
fun CustomCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checkedColor: Color = JasmineTheme.colors.accent,
    uncheckedColor: Color = Color(0xFFE0E0E0)
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(4.dp)
    
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(shape)
            .background(if (checked) checkedColor else Color.Transparent)
            .border(
                width = 2.dp,
                color = if (checked) checkedColor else uncheckedColor,
                shape = shape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = { onCheckedChange(!checked) }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            CustomText(text = "✓", color = Color.White, fontSize = 14.sp)
        }
    }
}
