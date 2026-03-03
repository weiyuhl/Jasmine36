package com.lhzkml.jasmine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lhzkml.jasmine.ui.theme.JasmineTheme

@Composable
fun CustomButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = JasmineTheme.colors.accent,
    contentColor: Color = Color.White,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    colors: ButtonColors? = null,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val bg = colors?.containerColor ?: backgroundColor
    val effectiveBg = if (enabled) bg else (colors?.disabledContainerColor ?: bg.copy(alpha = 0.5f))
    val effectiveContentColor = colors?.contentColor ?: contentColor
    
    CompositionLocalProvider(LocalContentColor provides effectiveContentColor) {
        Row(
            modifier = modifier
                .clip(shape)
                .background(effectiveBg)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick
                )
                .padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
fun CustomTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = JasmineTheme.colors.accent,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    colors: TextButtonColors? = null,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val effectiveContentColor = colors?.contentColor ?: contentColor
    
    CompositionLocalProvider(LocalContentColor provides effectiveContentColor) {
        Row(
            modifier = modifier
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick
                )
                .padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

// ButtonDefaults 替代品
object CustomButtonDefaults {
    @Composable
    fun buttonColors(
        containerColor: Color = JasmineTheme.colors.accent,
        contentColor: Color = Color.White,
        disabledContainerColor: Color = JasmineTheme.colors.accent.copy(alpha = 0.5f),
        disabledContentColor: Color = Color.White.copy(alpha = 0.5f)
    ) = ButtonColors(containerColor, contentColor, disabledContainerColor, disabledContentColor)
    
    @Composable
    fun textButtonColors(
        contentColor: Color = JasmineTheme.colors.accent,
        disabledContentColor: Color = JasmineTheme.colors.accent.copy(alpha = 0.5f)
    ) = TextButtonColors(contentColor, disabledContentColor)
}

data class ButtonColors(
    val containerColor: Color,
    val contentColor: Color,
    val disabledContainerColor: Color,
    val disabledContentColor: Color
)

data class TextButtonColors(
    val contentColor: Color,
    val disabledContentColor: Color
)
