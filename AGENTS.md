# Hachimi — Agent Guide

## 项目定位

网易云音乐无损下载工具，**平台是 Android**（Kotlin + Jetpack Compose + Miuix）。

## 快速开始

```bash
# Android Debug APK
.\gradlew.bat assembleDebug
```

要求：Java 17+

## 目录结构

- `app/` — Android 主工程，所有核心逻辑在此
- `ico/` — 图标资源

## Android 架构

分层：`data/api/`（网络请求）→ `data/repository/`（数据层）→ `ui/screens/`（Compose UI）

- `data/api/` — OkHttp 客户端 + EAPI 加密封装（NeteaseCrypto）
- `data/model/` — 数据类（kotlinx.serialization）
- `data/local/` — Cookie 持久化
- `downloader/` — 多线程分片下载引擎，支持断点续传
- `ui/` — Miuix 主题 + 底部 Tab 导航（发现/下载/设置）

## UI 组件规范 🎨

**本项目 99% 使用 Miuix 组件库**，遵循 MIUI 设计规范：

| 组件类型 | 使用规范 | 导入路径 |
|---------|---------|---------|
| **基础组件** | Button, Text, Card, Icon, HorizontalDivider | `top.yukonga.miuix.kmp.basic.*` |
| **对话框** | OverlayDialog（替代 Material3 Dialog） | `top.yukonga.miuix.kmp.overlay.OverlayDialog` |
| **设置项** | ArrowPreference, SwitchPreference, OverlayDropdownPreference | `top.yukonga.miuix.kmp.preference.*` |
| **导航** | NavigationBar, NavigationRailItem | `top.yukonga.miuix.kmp.basic.*` |
| **颜色/主题** | MiuixTheme.colorScheme | `top.yukonga.miuix.kmp.theme.*` |

**严禁使用 Material3 组件**（除了 `ripple` 用于 LocalIndication）：
```kotlin
// ❌ 禁止
import androidx.compose.material3.Button
import androidx.compose.material3.Dialog
import androidx.compose.material3.Card

// ✅ 正确
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.basic.Card
```

**关键差异**：
- `ButtonDefaults.buttonColors()`：参数名是 `color` 而非 `containerColor`
- `OverlayDialog`：内置标题/摘要/按钮布局，无需手动包装 Card
- 参考 Miuix 项目获取组件 API 文档

## 关键约定

- 包名：`com.qing.hachimi`
- applicationId：`com.qingyueyin.hachimi`
- UI 框架：Miuix（MIUI 风格 Compose 组件库），非 Material Design
- 网络：OkHttp + EAPI 加密方案
- 序列化：kotlinx.serialization
- 图片加载：Coil
- 构建：Gradle Kotlin DSL + Version Catalog（`gradle/libs.versions.toml`）
- **默认平台：用户未明确指明时，所有问题和修改默认针对 Android 端**

## 代码风格

- 遵循 Kotlin 官方编码规范
- Compose 使用 `@Composable` 函数式风格
- ViewModel 管理屏幕状态
- 使用 kotlinx.serialization 的 `@Serializable` 数据类

## 状态管理

- 使用 MVI 模式（Model-View-Intent）管理 UI 状态
- 每个 Screen 对应一个 ViewModel + UiState 数据类
- 状态变更通过 `StateFlow` 驱动 UI 更新
- 使用 `collectAsState()` 在 Compose 中消费状态

## 网络请求规范

- 所有 API 调用通过 `data/api/` 中的封装进行
- 响应统一包装为 `Result<T>` 类型（成功/失败）
- EAPI 加密由 `NeteaseCrypto` 处理，调用方无需关心加密细节
- Cookie 自动附加，由 `data/local/` 管理

## 下载模块规范

- 使用 `downloader/` 中的分片下载引擎
- 支持断点续传，下载进度通过 `Flow<Int>` 暴露
- 下载任务状态持久化，应用重启可恢复
- 并发下载数可控，默认限制为 3

## 错误处理

- 网络错误统一转换为友好的错误提示文案
- 使用 `try-catch` 包裹可能失败的操作，返回 `Result.failure()`
- UI 层根据错误类型展示不同的错误状态（网络异常/登录过期/资源不存在等）

### 网络请求超时机制

**所有网络请求必须设置超时**，避免无限等待影响用户体验。

**标准实现**：
```kotlin
class XxxViewModel : ViewModel() {
    companion object {
        private const val SEARCH_TIMEOUT_MS = 10_000L   // 搜索/查询操作
        private const val FETCH_TIMEOUT_MS = 15_000L     // 获取详情操作
        private const val LOAD_MORE_TIMEOUT_MS = 10_000L // 加载更多
    }
    
    fun search() {
        val q = uiState.value.searchQuery.trim()
        if (q.isBlank() || uiState.value.isSearching) return  // 状态保护
        
        uiState.value = uiState.value.copy(isSearching = true, statusMessage = null)
        viewModelScope.launch {
            try {
                awaitToken()  // 等待匿名 token 初始化
                withTimeout(SEARCH_TIMEOUT_MS) {
                    repository.searchAll(q).fold(
                        onSuccess = { result ->
                            uiState.value = uiState.value.copy(
                                songs = result.songs,
                                isSearching = false,
                                statusMessage = "找到 ${result.songs.size} 首"
                            )
                        },
                        onFailure = { e ->
                            uiState.value = uiState.value.copy(
                                isSearching = false,
                                statusMessage = "搜索失败: ${e.message}"
                            )
                        }
                    )
                }
            } catch (e: TimeoutCancellationException) {
                uiState.value = uiState.value.copy(
                    isSearching = false,
                    statusMessage = "搜索超时，请检查网络后重试"
                )
            }
        }
    }
}
```

**关键点**：
1. **状态保护**：方法开头检查 `isSearching` / `isLoadingMore`，避免重复请求
2. **超时设置**：
   - 快速操作（搜索、加载更多）：10 秒
   - 慢速操作（获取详情、下载）：15 秒
3. **三层错误处理**：
   - `Result.failure` - API 返回的业务错误
   - `TimeoutCancellationException` - 网络超时
   - 其他异常 - 未预期的错误
4. **友好提示**：超时消息提示"请检查网络后重试"，引导用户排查

**UI 层重试机制**：
```kotlin
// 状态消息中检测错误关键词
state.statusMessage?.let { msg ->
    val isError = msg.contains("失败") || msg.contains("错误") || msg.contains("超时")
    Row {
        Text(text = msg, color = if (isError) colorScheme.error else colorScheme.primary)
        if (isError) {
            Text(
                text = "重试",
                color = colorScheme.primary,
                modifier = Modifier.clickable(onClick = onRetry)
            )
        }
    }
}
```

**智能重试方法**：
```kotlin
fun retry() {
    val s = uiState.value
    when {
        s.playlistUrl.isNotBlank() -> parseUrl()   // 重新解析链接
        s.searchQuery.isNotBlank() -> search()     // 重新搜索
    }
}
```

参考实现：`SearchViewModel.kt` / `SearchTab.kt`

## 命名规范

- ViewModel 命名：`XxxViewModel`
- UiState 命名：`XxxUiState`
- Composable 函数以大写开头：`XxxScreen()`
- Repository 接口命名：`XxxRepository`
- API 服务命名：`XxxApi`



## 参考项目分类

以下为本项目可参考的外部项目，按负责领域分类：

### 网易云 API 层

| 项目 | 说明 |
|---|---|
| **api-enhanced** | 网易云音乐 Node.js API 封装，涵盖所有接口（登录、歌单、专辑、搜索、FM、云盘、榜单、评论等）。各接口对应 module/*.js，加密逻辑在 util/crypto.js |

参考用法：私人 FM 实现见 module/personal_fm.js，云盘接口见 module/user_cloud.js，新专辑分页见 module/album_new.js

### Miuix UI 组件

| 项目 | 说明 |
|---|---|
| **miuix** | Miuix Compose Multiplatform 组件库（top.yukonga.miuix.kmp），提供 MIUI 风格 UI 组件。本项目的所有 UI 均基于此库 |

### Android 音乐应用 (Kotlin + Compose + Miuix)

| 项目 | 说明 |
|---|---|
| **Ella (Halcyon)** | MIUI 风格 Android 音乐播放器，使用 Miuix 组件库。参考多 ViewModel 架构（MainViewModel + PlayerViewModel + LxOnlineViewModel）、底部导航栏、在线音乐功能 |
| **NeriPlayer** | 原生 Android 多源音频播放器。参考多源搜索、播放器架构 |
| **Lyrico** | Android 本地音乐标签编辑与歌词管理工具。参考元数据读写、插件化搜索源 |

### 系统工具/内核

| 项目 | 说明 |
|---|---|
| **SukiSU-Ultra** | KernelSU 管理器（Rust + Android）。其 manager/ 目录为 Android 端，参考 UI 风格与图标设计 |

## 常见问题

### 一二级页面切换动画闪烁问题 ⚠️

**问题描述**：在设置页等一二级页面切换时，如果同时修改 `showXxxSettings` 状态和 `currentTab`，会导致动画闪烁或黑屏。

**根本原因**：
1. Compose 的 `AnimatedContent` 在状态变化时会同时渲染旧内容（退出动画）和新内容（进入动画）
2. 如果在动画过程中**同时改变多个状态**（如 `showAppearanceSettings = false` + 切换 Tab），会导致：
   - 旧内容（二级页）提前消失
   - 新内容（一级页）还没准备好
   - 出现短暂的空白/闪烁

**错误示例**：
```kotlin
// ❌ 错误：同时修改两个状态
BackHandler(enabled = settingsBackEnabled) {
    when {
        showAppearanceSettings -> {
            showAppearanceSettings = false  // 立即关闭二级页
            currentTab = Tab.SETTINGS       // 同时切换 Tab
        }
    }
}
```

**正确做法**：
```kotlin
// ✅ 正确：只修改二级页状态，让动画完成
BackHandler(enabled = settingsBackEnabled) {
    when {
        showAppearanceSettings -> showAppearanceSettings = false
        showDownloadSettings -> showDownloadSettings = false
        showAccountSettings -> showAccountSettings = false
    }
}
```

**核心原则**：
1. **一次只改一个状态**：让 `AnimatedContent` 专注处理当前过渡
2. **不要主动切 Tab**：返回到一级页时，Tab 本身不变，只是内容从二级切回一级
3. **分离关注点**：
   - BackHandler 只负责关闭二级页
   - Tab 切换由用户点击底部导航栏触发
4. **使用 `skipXxxTransition` 临时禁用动画**：如果确实需要跳过动画（如程序化导航），设置标志位并在下一帧重置：
   ```kotlin
   skipTabTransition = true
   currentTab = Tab.DISCOVER
   // LaunchedEffect 会在下一帧重置 skipTabTransition
   ```

**其他场景**：
- **搜索页 → 发现页**：点击发现页内容跳转到搜索页，返回时要回到发现页
  - 使用 `NavigationBackHandler` + `previousSourceMode` 追踪来源
  - 只在 `onBackCompleted` 回调中切换 Tab，不在 ViewModel 里切
- **预览页面**：`MainPredictiveBackPreview` 函数传入 `previewTab` 显示目标页面的预览
  - 不要在预览函数中调用真实的业务逻辑（如 `showImportDialog = true`）
  - 传入空 lambda `{}` 代替

### 云盘歌曲无法加载
- 检查是否已登录（需要 MUSIC_U Cookie）
- 云盘 API 使用 WEAPI 加密，路径为 api/v1/cloud/get
- CloudApi 需要在 DI 中注册（已注册于 AppModule.kt）

### 新专辑分页
- API 路径 api/album/new（WEAPI），参数 limit / offset / area
- 参考 api-enhanced/module/album_new.js
- Android 端分页逻辑在 DiscoverViewModel.loadMoreNewAlbums()

### 私人 FM
- API 路径 v1/radio/get（WEAPI）
- 参考 api-enhanced/module/personal_fm.js
- Android 端逻辑在 DiscoverViewModel.loadFmSongs() / loadNextFmGroup()
- 默认阈值 3 组后自动清理最旧组

### 账户设置页面改造 (2026-06-14)

**改造目标**：去除低利用率的折叠信息卡，改为直接的操作列表

**未登录状态**：
- 标题栏显示"未登录"
- 操作列表：
  1. 浏览器登录（调用 WebView Activity）
  2. Cookie 登录（粘贴完整 Cookie）
  3. MUSIC_U 登录（只粘贴 MUSIC_U）
  4. 导入备份数据

**已登录状态**：
- 顶部显示简洁状态信息（登录凭据类型、可导出备份）
- 操作列表：
  1. 导出备份数据
  2. 退出登录（危险操作样式）

**浏览器登录历史账号功能** ✨：
- **每次点击浏览器登录**都会弹出选择对话框，包含：
  1. **历史账号列表**（最多显示5个）
     - 显示昵称和用户ID
     - 点击账号卡片 → 一键切换到该账号
     - 每个账号右侧有删除按钮
  2. **快速登录** → 使用浏览器缓存（如果可用）
  3. **新账号登录** → 清除浏览器缓存，重新登录

- **账号保存机制**：
  - 浏览器登录成功后自动保存账号信息（昵称、UID、Cookie）
  - 使用 `AccountHistoryManager` 管理历史账号
  - 数据加密存储在 SharedPreferences
  - 最多保存5个历史账号，按最后登录时间排序

- **账号切换**：
  - 点击历史账号 → 自动恢复该账号的 Cookie → 无需重新登录
  - 切换成功后显示 Toast 提示

**技术细节**：
- 新增 `AccountHistoryManager`：管理历史账号的增删查
- 新增 `AccountHistory` 数据模型：包含 userId、nickname、avatarUrl、lastLoginTime、cookieData
- MainViewModel 添加方法：
  - `saveAccountHistory()` - 登录成功后自动保存账号
  - `getAccountHistory()` - 获取历史账号列表
  - `deleteAccountHistory()` - 删除指定账号
  - `switchToAccount()` - 切换到历史账号
- 登录入口拆分为三个独立回调：`onBrowserLoginClick`、`onCookieLoginClick`、`onMusicULoginClick`
- 移除 `SettingsTab` 的 `onCookieClick` 参数（已改为二级页面导航）
- 移除用户 ID 展示（技术信息不适合放在账户页）
- 使用 `NeteaseWebLoginActivity.EXTRA_CLEAR_CACHE` 控制是否清除 WebView Cookie


### Bug 修复 (2026-06-14)

#### 1. ✅ 专辑页面多余的"加载更多"按钮
**问题**：专辑只有固定歌曲数，但显示"加载更多"，加载出无关歌曲
**原因**：`searchResults` 状态残留，`hasMoreSongs` 仍为 true
**修复**：在 `fetchAlbum/fetchPlaylist/fetchSong/loadAlbumFromResult` 中清空 `searchResults`
**文件**：`SearchViewModel.kt`

#### 2. ✅ 下载文件夹分组不工作
**问题**：设置了文件夹命名格式，但所有文件下载到同一目录
**原因**：`DownloadEngine` 没有根据 `folderNamingFormat` 创建子文件夹
**修复**：
- 添加 `buildTargetDirectory()` 根据格式构建目标文件夹
- 添加 `sanitizeFolderName()` 清理非法字符
- 支持按歌手/专辑/歌手-专辑分类
**文件**：`DownloadEngine.kt`
**示例**：
```
下载目录/
├── 周杰伦/
│   ├── 稻香 - 周杰伦 [无损].flac
│   └── 晴天 - 周杰伦 [无损].flac
├── 林俊杰/
    ├── 江南 - 林俊杰 [无损].flac
```
