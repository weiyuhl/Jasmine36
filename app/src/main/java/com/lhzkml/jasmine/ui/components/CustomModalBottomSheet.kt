package com.lhzkml.jasmine.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lhzkml.jasmine.ui.theme.BgPrimary
import com.lhzkml.jasmine.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val ANIM_DURATION_ENTER = 300
private const val ANIM_DURATION_EXIT = 250
private const val DISMISS_THRESHOLD_FRACTION = 0.35f

/**
 * 自定义 ModalBottomSheet，不依赖 Material 主题。
 *
 * 对齐官方 ModalBottomSheet 的核心行为：
 * - 底部弹出滑入/滑出动画
 * - 半透明遮罩层（scrim）随 sheet 动画渐变
 * - 向下滑动手势关闭（超过 35% 高度阈值松手即关闭）
 * - 顶部拖拽手柄
 * - 返回键关闭
 * - 点击遮罩关闭
 * - 内容超出最大高度时可滚动
 *
 * @param onDismissRequest 关闭回调（点击遮罩、返回键、下滑关闭时触发）
 * @param modifier 应用于 sheet 容器的 Modifier
 * @param sheetMaxHeightFraction sheet 最大高度占屏幕比例（0f~1f，默认 0.9f）
 * @param showDragHandle 是否显示顶部拖拽条
 * @param scrimColor 遮罩颜色（默认半透明黑）
 * @param sheetColor 面板背景色
 * @param content sheet 内容
 */
@Composable
fun CustomModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetMaxHeightFraction: Float = 0.9f,
    sheetMaxHeight: Dp = Dp.Unspecified,
    showDragHandle: Boolean = true,
    scrimColor: Color = Color.Black.copy(alpha = 0.5f),
    sheetColor: Color = BgPrimary,
    content: @Composable ColumnScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // 0f = fully visible, 1f = fully hidden (off-screen below)
    val slideProgress = remember { Animatable(1f) }
    var sheetHeightPx by remember { mutableFloatStateOf(0f) }
    var dismissing by remember { mutableStateOf(false) }

    fun animateDismiss() {
        if (dismissing) return
        dismissing = true
        scope.launch {
            slideProgress.animateTo(1f, tween(ANIM_DURATION_EXIT))
            onDismissRequest()
        }
    }

    LaunchedEffect(Unit) {
        slideProgress.animateTo(0f, tween(ANIM_DURATION_ENTER))
    }

    val scrimAlpha = scrimColor.alpha * (1f - slideProgress.value)

    Dialog(
        onDismissRequest = { animateDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val maxHPx = with(density) {
                if (sheetMaxHeight != Dp.Unspecified) sheetMaxHeight.toPx()
                else maxHeight.toPx() * sheetMaxHeightFraction
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimColor.copy(alpha = scrimAlpha))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { animateDismiss() }
            )

            Column(
                modifier = modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .onGloballyPositioned { coords ->
                        sheetHeightPx = coords.size.height.toFloat()
                    }
                    .offset {
                        IntOffset(0, (sheetHeightPx * slideProgress.value).roundToInt())
                    }
                    .pointerInput(Unit) {
                        var totalDrag = 0f
                        detectVerticalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                totalDrag += dragAmount
                                if (totalDrag < 0f) totalDrag = 0f
                                val progress = if (sheetHeightPx > 0f) {
                                    (totalDrag / sheetHeightPx).coerceIn(0f, 1f)
                                } else 0f
                                scope.launch {
                                    slideProgress.snapTo(progress)
                                }
                            },
                            onDragEnd = {
                                val current = slideProgress.value
                                if (current > DISMISS_THRESHOLD_FRACTION) {
                                    animateDismiss()
                                } else {
                                    scope.launch {
                                        slideProgress.animateTo(0f, tween(ANIM_DURATION_ENTER))
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    slideProgress.animateTo(0f, tween(ANIM_DURATION_ENTER))
                                }
                            }
                        )
                    }
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(sheetColor)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {}
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (showDragHandle) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 8.dp)
                            .height(4.dp)
                            .fillMaxWidth(0.1f)
                            .clip(RoundedCornerShape(2.dp))
                            .background(TextSecondary.copy(alpha = 0.4f))
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    content()
                }
            }
        }
    }
}
