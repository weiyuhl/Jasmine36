package com.lhzkml.jasmine.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.ui.theme.JasmineTheme

@Composable
fun CustomSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checkedTrackColor: Color = JasmineTheme.colors.accent,
    uncheckedTrackColor: Color = Color(0xFFE0E0E0),
    checkedThumbColor: Color = Color.White,
    uncheckedThumbColor: Color = Color.White,
    colors: SwitchColors? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        label = "thumbOffset"
    )

    val trackColor = if (checked) {
        colors?.checkedTrackColor ?: checkedTrackColor
    } else {
        colors?.uncheckedTrackColor ?: uncheckedTrackColor
    }
    val thumbColor = if (checked) {
        colors?.checkedThumbColor ?: checkedThumbColor
    } else {
        colors?.uncheckedThumbColor ?: uncheckedThumbColor
    }

    Box(
        modifier = modifier
            .width(52.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(trackColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = { onCheckedChange(!checked) }
            )
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .offset(x = thumbOffset)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}

object CustomSwitchDefaults {
    @Composable
    fun colors(
        checkedThumbColor: Color = Color.White,
        checkedTrackColor: Color = JasmineTheme.colors.accent,
        uncheckedThumbColor: Color = Color.White,
        uncheckedTrackColor: Color = Color(0xFFE0E0E0),
        uncheckedBorderColor: Color = Color.Transparent
    ) = SwitchColors(checkedThumbColor, checkedTrackColor, uncheckedThumbColor, uncheckedTrackColor, uncheckedBorderColor)
}

data class SwitchColors(
    val checkedThumbColor: Color,
    val checkedTrackColor: Color,
    val uncheckedThumbColor: Color,
    val uncheckedTrackColor: Color,
    val uncheckedBorderColor: Color = Color.Transparent
)
