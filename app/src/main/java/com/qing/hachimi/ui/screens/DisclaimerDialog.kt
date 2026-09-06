package com.qing.hachimi.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qing.hachimi.LegalNotice
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun DisclaimerDialog(
    show: Boolean,
    requireAccept: Boolean,
    onDismiss: () -> Unit,
    onAccept: () -> Unit = onDismiss,
) {
    if (!show) return
    var confirmed by remember(show, requireAccept) { mutableStateOf(!requireAccept) }

    WindowDialog(
        show = true,
        title = LegalNotice.TITLE,
        summary = LegalNotice.INTRO,
        onDismissRequest = if (requireAccept) {
            { /* 首次必须明确同意，不能点遮罩关掉 */ }
        } else {
            onDismiss
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                LegalNotice.CLAUSES.forEachIndexed { index, (title, body) ->
                    Text(
                        text = "${index + 1}. $title",
                        fontSize = 15.sp,
                        color = colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = body,
                        fontSize = 13.sp,
                        color = colorScheme.onSurfaceVariantActions,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Text(
                    text = LegalNotice.ACCEPT_HINT,
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantActions,
                )
            }

            if (requireAccept) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        state = if (confirmed) ToggleableState.On else ToggleableState.Off,
                        onClick = { confirmed = !confirmed },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "我已阅读并理解上述条款",
                        fontSize = 14.sp,
                        color = colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { confirmed = !confirmed },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                if (requireAccept) {
                    Button(
                        enabled = confirmed,
                        onClick = onAccept,
                    ) {
                        Text("我已阅读并同意")
                    }
                } else {
                    Button(onClick = onDismiss) {
                        Text("关闭")
                    }
                }
            }
        }
    }
}
