package com.qing.hachimi.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qing.hachimi.data.local.AccountHistory
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.window.WindowDialog

// ═════════════════════════════════════════════════════════════════════════════
// 账号相关对话框（状态由 MainScreen 持有，这里只负责界面）
// ═════════════════════════════════════════════════════════════════════════════

@Composable
internal fun ReloginDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onRelogin: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = "已登录",
        summary = "是否清除 WebView 缓存重新登录？",
        onDismissRequest = onDismiss
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                text = "取消",
                onClick = onDismiss
            )
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onRelogin
            ) {
                Text("重新登录")
            }
        }
    }
}

@Composable
internal fun LogoutDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = "退出登录",
        summary = "将清除本机保存的登录状态和当前账户内容。下载记录不会受影响。",
        onDismissRequest = onDismiss
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                text = "取消",
                onClick = onDismiss
            )
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    color = colorScheme.error,
                    contentColor = colorScheme.onError,
                )
            ) {
                Text("退出")
            }
        }
    }
}

@Composable
internal fun StoragePermissionDialog(
    show: Boolean,
    storageCompatLayer: String?,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = "存储权限",
        summary = "保存到公共目录需要「所有文件访问权限」。请在接下来的系统页面中开启此权限。未授权时下载会失败。" +
            (storageCompatLayer?.let { "\n\n检测到 $it 环境：若找不到对应开关，请到 $it 的应用设置中开启。" } ?: ""),
        onDismissRequest = onDismiss
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                text = "稍后",
                onClick = onDismiss
            )
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onOpenSettings
            ) {
                Text("去授权")
            }
        }
    }
}

@Composable
internal fun ExportAccountDialog(
    show: Boolean,
    exportedData: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    WindowDialog(
        show = show,
        title = "导出账户数据",
        summary = "请妥善保管，不要泄露给他人",
        summaryColor = colorScheme.error,
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            // Scrollable text area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorScheme.surfaceContainer)
                    .padding(12.dp),
            ) {
                Text(
                    text = exportedData,
                    style = MiuixTheme.textStyles.body2,
                    color = colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row {
                    // Copy button
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Hachimi账户数据", exportedData))
                        }
                    ) {
                        Text("复制")
                    }
                    Spacer(Modifier.width(12.dp))
                    // Share button
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, exportedData)
                            }
                            context.startActivity(Intent.createChooser(intent, "分享账户数据"))
                        }
                    ) {
                        Text("分享")
                    }
                }
                TextButton(
                    text = "关闭",
                    onClick = onDismiss
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun ImportAccountDialog(
    show: Boolean,
    input: String,
    error: String?,
    onInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = "导入账户数据",
        summary = "粘贴之前导出的完整账户数据",
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            TextField(
                value = input,
                onValueChange = onInputChange,
                label = error ?: "粘贴导出的数据",
                modifier = Modifier.fillMaxWidth(),
                minLines = 8,
                maxLines = 12
            )

            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    error,
                    color = colorScheme.error,
                    style = MiuixTheme.textStyles.body2
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    text = "取消",
                    onClick = onCancel
                )
                Spacer(Modifier.width(12.dp))
                Button(onClick = onConfirm) {
                    Text("导入")
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun CookieLoginDialog(
    show: Boolean,
    loginMode: Int,
    input: String,
    error: String?,
    onInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = if (loginMode == 0) "MUSIC_U 登录" else "Cookie 登录",
        summary = if (loginMode == 0) "直接粘贴 MUSIC_U 值即可" else "从浏览器复制完整 Cookie 后粘贴",
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            TextField(
                value = input,
                onValueChange = onInputChange,
                label = when {
                    error != null -> error
                    loginMode == 0 -> "粘贴 MUSIC_U 值"
                    else -> "MUSIC_U=xxx; __csrf=xxx; ..."
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )

            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    error,
                    color = colorScheme.error,
                    style = MiuixTheme.textStyles.body2
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    text = "取消",
                    onClick = onCancel
                )
                Spacer(Modifier.width(12.dp))
                Button(onClick = onConfirm) {
                    Text("登录")
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun BrowserLoginDialog(
    show: Boolean,
    accounts: List<AccountHistory>,
    onDismiss: () -> Unit,
    onSwitchAccount: (AccountHistory) -> Unit,
    onDeleteAccount: (AccountHistory) -> Unit,
    onQuickLogin: () -> Unit,
    onNewLogin: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = "浏览器登录",
        summary = "选择账号或新建登录",
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .heightIn(max = 500.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (accounts.isNotEmpty()) {
                Text(
                    "历史账号",
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface
                )

                Spacer(Modifier.height(4.dp))

                // 大屏对话框限高为窗口 2/3：列表按剩余空间伸缩，保证底部登录按钮不被裁掉
                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(accounts.size) { index ->
                        val account = accounts[index]
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colorScheme.secondaryContainer, RoundedCornerShape(12.dp))
                                .clickable { onSwitchAccount(account) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        account.nickname,
                                        fontWeight = FontWeight.Medium,
                                        color = colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        "UID: ${account.userId}",
                                        style = MiuixTheme.textStyles.body2,
                                        color = colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                                IconButton(onClick = { onDeleteAccount(account) }) {
                                    Icon(
                                        imageVector = MiuixIcons.Delete,
                                        contentDescription = "删除",
                                        tint = colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = colorScheme.outline.copy(alpha = 0.3f)
                )
            }

            // 快速登录（保留缓存）
            Button(
                onClick = onQuickLogin,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("快速登录", fontWeight = FontWeight.Medium)
                    Text(
                        "使用浏览器缓存（如果可用）",
                        style = MiuixTheme.textStyles.body2,
                        color = colorScheme.onPrimary.copy(alpha = 0.95f)
                    )
                }
            }

            // 清除缓存重新登录
            Button(
                onClick = onNewLogin,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    color = colorScheme.secondaryContainer,
                    contentColor = colorScheme.onSecondaryContainer
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("新账号登录", fontWeight = FontWeight.Medium)
                    Text(
                        "清除浏览器缓存，重新登录",
                        style = MiuixTheme.textStyles.body2,
                        color = colorScheme.onSecondaryContainer.copy(alpha = 0.95f)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
