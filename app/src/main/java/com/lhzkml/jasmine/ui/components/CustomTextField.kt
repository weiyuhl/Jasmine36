package com.lhzkml.jasmine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.ui.theme.TextPrimary
import com.lhzkml.jasmine.ui.theme.TextSecondary

@Composable
fun CustomOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    textStyle: TextStyle = TextStyle(
        color = TextPrimary,
        fontSize = 14.sp
    ),
    focusedBorderColor: Color = TextPrimary,
    unfocusedBorderColor: Color = TextSecondary,
    backgroundColor: Color = Color.Transparent,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        visualTransformation = visualTransformation,
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .border(
                width = 1.dp,
                color = if (isFocused) focusedBorderColor else unfocusedBorderColor,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        textStyle = textStyle,
        singleLine = singleLine,
        maxLines = maxLines,
        cursorBrush = SolidColor(TextPrimary),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty() && placeholder != null) {
                    placeholder()
                }
                innerTextField()
            }
        }
    )
}

object CustomOutlinedTextFieldDefaults {
    @Composable
    fun colors(
        focusedTextColor: Color = TextPrimary,
        unfocusedTextColor: Color = TextPrimary,
        focusedBorderColor: Color = TextPrimary,
        unfocusedBorderColor: Color = TextSecondary,
        cursorColor: Color = TextPrimary
    ) = CustomTextFieldColors(
        focusedTextColor = focusedTextColor,
        unfocusedTextColor = unfocusedTextColor,
        focusedBorderColor = focusedBorderColor,
        unfocusedBorderColor = unfocusedBorderColor,
        cursorColor = cursorColor
    )
}

data class CustomTextFieldColors(
    val focusedTextColor: Color,
    val unfocusedTextColor: Color,
    val focusedBorderColor: Color,
    val unfocusedBorderColor: Color,
    val cursorColor: Color
)
