package com.lhzkml.jasmine.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.ui.theme.JasmineTheme

val LocalContentColor = staticCompositionLocalOf { Color.Unspecified }

@Composable
fun CustomText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    lineHeight: TextUnit = TextUnit.Unspecified
) {
    val resolvedColor = if (color != Color.Unspecified) color
        else if (LocalContentColor.current != Color.Unspecified) LocalContentColor.current
        else JasmineTheme.colors.textPrimary

    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = resolvedColor,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign ?: TextAlign.Unspecified,
            lineHeight = lineHeight
        ),
        overflow = overflow,
        maxLines = maxLines
    )
}
