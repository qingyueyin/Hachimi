package com.qing.hachimi.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// ── MIUIX 设计令牌体系 ──

/**
 * 间距系统 — 基于 4dp 网格
 * MIUIX 标准间距：xs=4, sm=8, md=12, lg=16, xl=20, xxl=24
 */
object AppSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
}

/**
 * 圆角系统 — 对齐 MIUIX 规范的圆角层级
 * - small:  小元素（标签、缩略图、输入框）
 * - medium: 卡片、面板
 * - large:  对话框、底部弹窗
 * - pill:   胶囊形（完全圆角）
 * - circle: 圆形（头像等）
 */
object AppShapes {
    val small = RoundedCornerShape(8.dp)
    val medium = RoundedCornerShape(12.dp)
    val large = RoundedCornerShape(16.dp)
    val xlarge = RoundedCornerShape(20.dp)
    val pill = RoundedCornerShape(percent = 50)
    val circle = CircleShape
}

/**
 * MIUIX 风格图标 Chip — 用于筛选/分类标签
 * 选中态使用 primary 色，未选中使用 surfaceVariant
 */
@Composable
fun AppChip(
    text: String,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colorScheme = MiuixTheme.colorScheme
    Box(
        modifier = modifier
            .clip(AppShapes.pill)
            .background(if (selected) colorScheme.primary else colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) colorScheme.onPrimary else colorScheme.onSurfaceVariantActions,
            style = MiuixTheme.textStyles.body2,
        )
    }
}

/**
 * MIUIX 风格图标 Chip — 带图标的筛选标签
 * 用于来源选择、下载状态筛选等场景
 */
@Composable
fun AppIconChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colorScheme = MiuixTheme.colorScheme
    val bgColor = if (selected) colorScheme.primary else colorScheme.surfaceVariant
    val contentColor = if (selected) colorScheme.onPrimary else colorScheme.onSurface

    Row(
        modifier = modifier
            .clip(AppShapes.pill)
            .background(bgColor)
            .clickable { onClick() }
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(16.dp),
            tint = contentColor
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = contentColor,
            style = MiuixTheme.textStyles.body2,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/**
 * MIUIX 风格 Surface 卡片容器
 * 使用 surfaceVariant 色作为背景，提供统一的卡片样式
 */
@Composable
fun AppSurfaceCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(AppSpacing.md),
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceVariant),
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}
