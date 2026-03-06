package com.lhzkml.jasmine.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.lhzkml.jasmine.ui.theme.BgPrimary
import com.lhzkml.jasmine.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val ANIM_DURATION = 280
private const val DISMISS_THRESHOLD = 0.4f
private const val COLLAPSE_THRESHOLD_PX = 60f

/** 展开档位：1=最小, 2=半屏, 3=全屏 */
private const val STATE_PEEK = 1
private const val STATE_HALF = 2
private const val STATE_FULL = 3

/**
 * 自定义 ModalBottomSheet，支持多档位展开与下滑收回。
 *
 * - 初次以「最小」档位显示
 * - 上滑展开至半屏或全屏
 * - 下滑折叠或关闭（最小档下滑即关闭）
 * - 内容可滚动
 * @param dragFromContentArea 为 true 时，支持通过拖动内容区（非仅小横条）来展开/收起 Sheet
 */
@Composable
fun CustomModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetMinHeight: Dp = 200.dp,
    sheetMaxHeight: Dp = Dp.Unspecified,
    sheetMaxHeightFraction: Float = 0.92f,
    showDragHandle: Boolean = true,
    sheetColor: Color = BgPrimary,
    dragFromContentArea: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var expansionState by remember { mutableIntStateOf(STATE_PEEK) }
    val slideOffsetPx = remember { Animatable(0f) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var expandOffsetPx by remember { mutableFloatStateOf(0f) }
    var dismissing by remember { mutableStateOf(false) }

    fun dismiss(animateToPx: Float = 1200f) {
        if (dismissing) return
        dismissing = true
        scope.launch {
            slideOffsetPx.animateTo(animateToPx, tween(ANIM_DURATION))
            onDismissRequest()
        }
    }

    LaunchedEffect(Unit) {
        slideOffsetPx.snapTo(0f)
    }

    Dialog(
        onDismissRequest = { dismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        val view = LocalView.current
        DisposableEffect(Unit) {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.setDimAmount(0.1f)
            onDispose { }
        }
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { dismiss() }
            )
            val maxHPx = with(density) { maxHeight.toPx() }
            val minHPx = with(density) { sheetMinHeight.toPx() }
            val maxSheetPx = with(density) {
                if (sheetMaxHeight != Dp.Unspecified) sheetMaxHeight.toPx()
                else maxHPx * sheetMaxHeightFraction
            }
            val halfHPx = maxHPx * 0.5f

            val targetHeightPx = when (expansionState) {
                STATE_PEEK -> minHPx.coerceAtMost(maxSheetPx)
                STATE_HALF -> halfHPx.coerceIn(minHPx, maxSheetPx)
                STATE_FULL -> maxSheetPx
                else -> minHPx
            }

            val maxExpandPx = (maxSheetPx - targetHeightPx).coerceAtLeast(0f)
            val currentHeightPx = targetHeightPx + expandOffsetPx.coerceIn(0f, maxExpandPx)
            val offsetYPx = slideOffsetPx.value + dragOffsetPx
            val scrollState = rememberScrollState()

            val contentNestedConnection = remember(dragFromContentArea) {
                if (!dragFromContentArea) null
                else object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (scrollState.value == 0 && available.y > 0) {
                            dragOffsetPx = (dragOffsetPx + available.y).coerceIn(0f, currentHeightPx)
                            return Offset(0f, available.y)
                        }
                        return Offset.Zero
                    }
                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                        if (available.y == 0f) return Offset.Zero
                        if (available.y < 0) {
                            expandOffsetPx = (expandOffsetPx - available.y).coerceIn(0f, maxExpandPx)
                            return Offset(0f, available.y)
                        }
                        if (scrollState.value >= scrollState.maxValue && available.y > 0) {
                            dragOffsetPx = (dragOffsetPx + available.y).coerceIn(0f, currentHeightPx)
                            return Offset(0f, available.y)
                        }
                        return Offset.Zero
                    }
                }
            }

            fun snapToNearestState(currentH: Float) {
                val distances = listOf(
                    STATE_PEEK to kotlin.math.abs(currentH - minHPx),
                    STATE_HALF to kotlin.math.abs(currentH - halfHPx),
                    STATE_FULL to kotlin.math.abs(currentH - maxSheetPx)
                )
                expansionState = distances.minByOrNull { it.second }!!.first
            }

            val sheetModifier = if (contentNestedConnection != null) {
                Modifier.nestedScroll(contentNestedConnection)
            } else Modifier

            Column(
                modifier = sheetModifier
                    .then(modifier)
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(with(density) { currentHeightPx.toDp() })
                    .offset { IntOffset(0, offsetYPx.roundToInt()) }
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(sheetColor)
                    .pointerInput(expansionState, targetHeightPx, maxExpandPx) {
                        detectVerticalDragGestures(
                            onDragStart = { _ ->
                                dragOffsetPx = 0f
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                if (dragAmount > 0) {
                                    if (expandOffsetPx > 0) {
                                        val reduce = minOf(expandOffsetPx, dragAmount)
                                        expandOffsetPx -= reduce
                                        val leftover = dragAmount - reduce
                                        if (leftover > 0f) {
                                            dragOffsetPx = (dragOffsetPx + leftover).coerceIn(0f, currentHeightPx)
                                        }
                                    } else {
                                        dragOffsetPx = (dragOffsetPx + dragAmount).coerceIn(0f, currentHeightPx)
                                    }
                                } else {
                                    val expand = -dragAmount
                                    expandOffsetPx = (expandOffsetPx + expand).coerceIn(0f, maxExpandPx)
                                }
                            },
                            onDragEnd = {
                                if (dragOffsetPx > COLLAPSE_THRESHOLD_PX) {
                                    when {
                                        dragOffsetPx / currentHeightPx > DISMISS_THRESHOLD && expansionState == STATE_PEEK ->
                                            dismiss(animateToPx = currentHeightPx + 100f)
                                        expansionState > STATE_PEEK -> {
                                            expansionState = when (expansionState) {
                                                STATE_FULL -> STATE_HALF
                                                STATE_HALF -> STATE_PEEK
                                                else -> expansionState
                                            }
                                            dragOffsetPx = 0f
                                        }
                                        else -> dragOffsetPx = 0f
                                    }
                                } else {
                                    val snapH = targetHeightPx + expandOffsetPx.coerceIn(0f, maxExpandPx)
                                    snapToNearestState(snapH)
                                    expandOffsetPx = 0f
                                    dragOffsetPx = 0f
                                }
                            },
                            onDragCancel = {
                                dragOffsetPx = 0f
                                expandOffsetPx = 0f
                            }
                        )
                    }
                    .padding(horizontal = 16.dp)
            ) {
                if (showDragHandle) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.18f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(TextSecondary.copy(alpha = 0.4f))
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(bottom = 16.dp)
                ) {
                    content()
                }
            }
        }
    }
}
