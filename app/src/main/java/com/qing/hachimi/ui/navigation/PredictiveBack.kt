package com.qing.hachimi.ui.navigation

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.flow.collect

// SukiSU 使用的 NavTransitionEasing，基于自定义的贝塞尔曲线
internal val NavTransitionEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

@Composable
fun AppPredictiveBackHandler(
    enabled: Boolean,
    onBackProgress: (BackEventCompat) -> Unit = {},
    onBackSettled: () -> Unit = {},
    onBack: () -> Unit,
) {
    val currentOnBackProgress by rememberUpdatedState(onBackProgress)
    val currentOnBackSettled by rememberUpdatedState(onBackSettled)
    val currentOnBack by rememberUpdatedState(onBack)

    PredictiveBackHandler(enabled = enabled) { backEvents ->
        try {
            backEvents.collect { backEvent ->
                currentOnBackProgress(backEvent)
            }
            currentOnBack()
        } finally {
            currentOnBackSettled()
        }
    }
}

fun predictiveBackTranslationDirection(swipeEdge: Int): Float =
    if (swipeEdge == BackEventCompat.EDGE_RIGHT) 1f else -1f

/**
 * 预测性返回前景位置：
 * - 手势中 snapTo 紧跟手指；
 * - 手势完成 progress 归零时直接 snapTo(0)，不额外动画。
 *   因为内容切换已由上层瞬间完成（如 AnimatedVisibility exit=None），
 *   前景 snap 回原位与内容切换发生在同一帧，用户只看到手势→最终态一个过渡。
 */
@Composable
fun Modifier.predictiveBackTransform(
    progress: Float,
    swipeEdge: Int,
): Modifier {
    val animatable = remember { Animatable(0f) }
    val coerced = progress.coerceIn(0f, 1f)

    LaunchedEffect(coerced) {
        animatable.snapTo(coerced)
    }

    val displayProgress = animatable.value
    if (displayProgress == 0f) return this

    return graphicsLayer {
        val direction = if (swipeEdge == BackEventCompat.EDGE_RIGHT) 1f else -1f
        val screenWidth = android.content.res.Resources.getSystem().displayMetrics.widthPixels.toFloat()
        translationX = screenWidth * direction * NavTransitionEasing.transform(displayProgress)
    }
}

@Composable
fun Modifier.predictiveBackBackgroundTransform(
    progress: Float,
    swipeEdge: Int,
): Modifier {
    val coercedProgress = progress.coerceIn(0f, 1f)
    if (coercedProgress == 0f) return this

    return graphicsLayer {
        val direction = if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
        val screenWidth = android.content.res.Resources.getSystem().displayMetrics.widthPixels.toFloat()
        translationX = screenWidth * direction * (1f - NavTransitionEasing.transform(coercedProgress))
    }
}
