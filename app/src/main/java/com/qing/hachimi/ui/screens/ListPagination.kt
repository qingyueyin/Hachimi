package com.qing.hachimi.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

internal const val LIST_PAGE_SIZE = 10

internal fun listPageCount(itemCount: Int, pageSize: Int = LIST_PAGE_SIZE): Int =
    if (itemCount <= 0) 0 else (itemCount + pageSize - 1) / pageSize

internal fun safeListPage(itemCount: Int, page: Int, pageSize: Int = LIST_PAGE_SIZE): Int {
    val pageCount = listPageCount(itemCount, pageSize)
    return if (pageCount == 0) 0 else page.coerceIn(0, pageCount - 1)
}

internal fun <T> listPage(items: List<T>, page: Int, pageSize: Int = LIST_PAGE_SIZE): List<T> {
    if (items.isEmpty()) return emptyList()
    val safePage = safeListPage(items.size, page, pageSize)
    val start = safePage * pageSize
    return items.subList(start, minOf(start + pageSize, items.size))
}

@Composable
internal fun ListPageNavigator(
    currentPage: Int,
    itemCount: Int,
    onPageChange: (Int) -> Unit,
    stateKey: Any?,
    modifier: Modifier = Modifier,
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
) {
    val pageCount = listPageCount(itemCount)
    val safePage = safeListPage(itemCount, currentPage)
    var pendingAdvance by remember(stateKey) { mutableStateOf(false) }
    var requestedItemCount by remember(stateKey) { mutableIntStateOf(0) }

    LaunchedEffect(pageCount, safePage) {
        if (currentPage != safePage) onPageChange(safePage)
    }
    LaunchedEffect(itemCount, isLoadingMore) {
        if (!pendingAdvance) return@LaunchedEffect
        if (itemCount > requestedItemCount) {
            onPageChange((safePage + 1).coerceAtMost(listPageCount(itemCount) - 1))
            pendingAdvance = false
        } else if (!isLoadingMore) {
            pendingAdvance = false
        }
    }

    if (pageCount == 0 || (pageCount == 1 && !hasMore)) return

    val hasLocalNext = safePage < pageCount - 1
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { onPageChange(safePage - 1) },
            enabled = safePage > 0 && !isLoadingMore,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(MiuixIcons.ChevronBackward, "上一页", Modifier.size(18.dp))
        }
        Text(
            text = "${safePage + 1} / $pageCount",
            modifier = Modifier.width(52.dp),
            color = colorScheme.onSurfaceVariantActions,
            style = MiuixTheme.textStyles.body2,
            textAlign = TextAlign.Center,
        )
        IconButton(
            onClick = {
                if (hasLocalNext) {
                    onPageChange(safePage + 1)
                } else if (hasMore) {
                    requestedItemCount = itemCount
                    pendingAdvance = true
                    onLoadMore()
                }
            },
            enabled = (hasLocalNext || hasMore) && !isLoadingMore,
            modifier = Modifier.size(32.dp),
        ) {
            if (isLoadingMore && !hasLocalNext) {
                CircularProgressIndicator(size = 18.dp, strokeWidth = 2.dp)
            } else {
                Icon(MiuixIcons.ChevronForward, "下一页", Modifier.size(18.dp))
            }
        }
    }
}
