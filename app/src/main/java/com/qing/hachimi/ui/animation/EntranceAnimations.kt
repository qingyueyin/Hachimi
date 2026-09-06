package com.qing.hachimi.ui.animation

import android.content.ContentResolver
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * 滚动方向：DOWN = 用户向下滚动（新条目出现在视口底部），UP 反之。
 */
enum class ScrollDirection { NONE, DOWN, UP }

/**
 * 列表当前的滚动方向。列表容器通过 [rememberScrollDirection] 更新它，
 * 列表项在首次组合时读取该值决定入场方向。
 */
val LocalScrollDirection = staticCompositionLocalOf { ScrollDirection.DOWN }

/**
 * 临界阻尼弹簧：mass=1、stiffness=625、damping=50。
 * Compose spring 的 dampingRatio=1（临界阻尼）时，阻尼系数 c = 2·√(k·m) = 2·√625 = 50，
 * 与规范中的 damping=50 完全一致：无回弹、无二次晃动，一次到位即停。
 */
val CriticallyDampedSpring: SpringSpec<Float> = spring(
    dampingRatio = 1f,
    stiffness = 625f,
    visibilityThreshold = 0.001f,
)

/**
 * 系统“移除动画”（开发者选项 ANIMATOR_DURATION_SCALE == 0）是否开启。
 */
@Composable
fun rememberIsReduceMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        isReduceMotionEnabled(context.contentResolver)
    }
}

private fun isReduceMotionEnabled(resolver: ContentResolver): Boolean =
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/**
 * 基于 [LazyListState] 追踪真实滚动方向。
 *
 * 拖动、惯性滚动、程序化滚动都会改变 (firstVisibleItemIndex, firstVisibleItemScrollOffset)，
 * 用两者的词典序增量判断方向，无需关心具体滚动速度。
 *
 * 注意：LaunchedEffect 启动时先记录当前位置，因此恢复滚动位置不会产生虚假方向。
 */
@Composable
fun rememberScrollDirection(listState: LazyListState): State<ScrollDirection> {
    val direction = remember(listState) { mutableStateOf(ScrollDirection.NONE) }
    LaunchedEffect(listState) {
        var previousIndex = listState.firstVisibleItemIndex
        var previousOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            val next = when {
                index > previousIndex -> ScrollDirection.DOWN
                index < previousIndex -> ScrollDirection.UP
                offset > previousOffset -> ScrollDirection.DOWN
                offset < previousOffset -> ScrollDirection.UP
                else -> direction.value
            }
            previousIndex = index
            previousOffset = offset
            if (next != direction.value) {
                direction.value = next
            }
        }
    }
    return direction
}

@Composable
fun rememberScrollDirection(gridState: LazyGridState): State<ScrollDirection> {
    val direction = remember(gridState) { mutableStateOf(ScrollDirection.NONE) }
    LaunchedEffect(gridState) {
        var previousIndex = gridState.firstVisibleItemIndex
        var previousOffset = gridState.firstVisibleItemScrollOffset
        snapshotFlow {
            gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            val next = when {
                index > previousIndex -> ScrollDirection.DOWN
                index < previousIndex -> ScrollDirection.UP
                offset > previousOffset -> ScrollDirection.DOWN
                offset < previousOffset -> ScrollDirection.UP
                else -> direction.value
            }
            previousIndex = index
            previousOffset = offset
            if (next != direction.value) direction.value = next
        }
    }
    return direction
}

/**
 * 列表项入场动画：只作用于绘制层（graphicsLayer 的 transform/alpha），
 * 绝不改变项目尺寸、间距或列表布局。
 *
 * - 初始 alpha 0.78 → 1；位移 12px → 0（根据 [LocalScrollDirection] 决定从下方还是上方进入）
 * - 位移使用 [CriticallyDampedSpring] 临界阻尼弹簧，无回弹
 * - 开启“减少动态效果”时取消位移，只保留淡入
 *
 * @param key 列表项的稳定 key（如 song.id / album.id），key 变化或重新进入视口时重新播放。
 */
@Composable
fun Modifier.directionalEntrance(key: Any?): Modifier = composed {
    val direction = LocalScrollDirection.current
    val reduceMotion = rememberIsReduceMotionEnabled()
    val progress = remember(key) { Animatable(0f, visibilityThreshold = 0.001f) }
    LaunchedEffect(key, reduceMotion) {
        progress.snapTo(0f)
        if (reduceMotion) {
            progress.animateTo(1f, tween(durationMillis = 120))
        } else {
            progress.animateTo(1f, CriticallyDampedSpring)
        }
    }
    graphicsLayer {
        val p = progress.value
        alpha = 0.78f + 0.22f * p
        if (!reduceMotion && p < 1f) {
            val travel = 12.dp.toPx() * (1f - p)
            translationY = when (direction) {
                ScrollDirection.DOWN -> travel // 从下方 12px 向上归位
                ScrollDirection.UP -> -travel // 从上方 12px 向下归位
                ScrollDirection.NONE -> 0f
            }
        }
    }
}
