package com.lhzkml.jasmine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lhzkml.jasmine.ui.theme.*

/**
 * WakeLock 控制按钮
 */
@Composable
fun WakeLockButton(
    isHeld: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isHeld) AccentLight else BgInput)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 图标
        CustomText(
            text = if (isHeld) "🔓" else "🔒",
            fontSize = 16.sp
        )
        
        // 文本
        CustomText(
            text = if (isHeld) "释放唤醒锁" else "获取唤醒锁",
            fontSize = 13.sp,
            fontWeight = if (isHeld) FontWeight.Bold else FontWeight.Normal,
            color = if (isHeld) Accent else TextSecondary
        )
    }
}

/**
 * WakeLock 状态指示器（用于顶部栏）
 */
@Composable
fun WakeLockIndicator(
    isHeld: Boolean,
    modifier: Modifier = Modifier
) {
    if (isHeld) {
        Box(
            modifier = modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Accent)
        )
    }
}
