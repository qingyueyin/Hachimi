package com.qing.hachimi.ui.screens

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val ParentPageOffsetFraction = 0.24f

/** 预测性返回提交的那一帧置为 true：手势本身就是过渡，tab 内部层级动画直接跳过 */
val LocalSuppressNavTransition = compositionLocalOf { false }

/** Motion for parent/child navigation. Back retraces the exact forward path. */
internal fun hierarchicalNavigationTransition(
    isForward: Boolean,
    layoutDirection: Int,
    suppress: Boolean = false,
): ContentTransform {
    if (suppress) {
        return EnterTransition.None togetherWith ExitTransition.None
    }
    val direction = if (layoutDirection >= 0) 1 else -1
    val spatialSpec = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    val enter = slideInHorizontally(
        animationSpec = spatialSpec,
        initialOffsetX = { width ->
            if (isForward) width * direction
            else (-width * ParentPageOffsetFraction * direction).roundToInt()
        },
    ) + fadeIn(animationSpec = tween(durationMillis = 180))

    val exit = slideOutHorizontally(
        animationSpec = spatialSpec,
        targetOffsetX = { width ->
            if (isForward) (-width * ParentPageOffsetFraction * direction).roundToInt()
            else width * direction
        },
    ) + fadeOut(animationSpec = tween(durationMillis = 140))

    return enter.togetherWith(exit)
}

/** Peer views do not imply a spatial hierarchy. */
internal fun peerContentTransition(): ContentTransform =
    fadeIn(animationSpec = tween(durationMillis = 160))
        .togetherWith(fadeOut(animationSpec = tween(durationMillis = 100)))

/**
 * 同级 Tab 切换：方向感知 + 临界阻尼。
 * - 切到更靠后的 Tab：新页从右侧 20px 进入（alpha 0.78→1），旧页向左侧 12px 退出
 * - 切到更靠前的 Tab：方向相反
 * - 位移/透明度均使用临界阻尼弹簧（mass=1、stiffness=625、damping=50），无回弹；
 *   AnimatedContent 目标变化时动画可中断并重定向到新目标
 * - 开启“减少动态效果”时取消位移，只保留淡入
 */
internal fun directionalTabTransition(
    isForward: Boolean,
    layoutDirection: Int,
    reduceMotion: Boolean,
    density: Density,
): ContentTransform {
    if (reduceMotion) {
        return fadeIn(animationSpec = tween(durationMillis = 120))
            .togetherWith(fadeOut(animationSpec = tween(durationMillis = 120)))
    }
    val sign = if (isForward) 1 else -1
    val direction = sign * layoutDirection
    val enterOffset = with(density) { 20.dp.roundToPx() }
    val exitOffset = with(density) { 12.dp.roundToPx() }
    val tabSpring = spring<IntOffset>(
        dampingRatio = 1f,
        stiffness = 625f,
        visibilityThreshold = IntOffset(1, 1),
    )
    val alphaSpring = spring<Float>(
        dampingRatio = 1f,
        stiffness = 625f,
        visibilityThreshold = 0.001f,
    )
    val enter = slideInHorizontally(
        animationSpec = tabSpring,
        initialOffsetX = { direction * enterOffset },
    ) + fadeIn(animationSpec = alphaSpring, initialAlpha = 0.78f)
    val exit = slideOutHorizontally(
        animationSpec = tabSpring,
        targetOffsetX = { -direction * exitOffset },
    ) + fadeOut(animationSpec = alphaSpring)
    return enter.togetherWith(exit)
}

/** Hide the outgoing page from accessibility while the active page stays on top. */
internal fun Modifier.hideTransitionSemantics(): Modifier = clearAndSetSemantics { }
