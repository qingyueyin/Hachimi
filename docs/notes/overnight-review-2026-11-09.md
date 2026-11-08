# 夜间代码审查报告（2026-11-09）

分支 `review/overnight`，从 `main`（`dc82d2e`）切出。三个修复提交，均未推送。

## 已修并验证

### `6f1ed93` 下载链路
1. **扩展名靠猜**：CDN 链接常常没有扩展名，旧代码按 URL 猜成 `.mp3`，无损文件也存成 mp3，升级功能后面的音质检测直接失效。现在 `getSongUrlWithType` 带出接口的 `type` 字段（flac/mp3/m4a/aac/wav），`DownloadEngine.startDownload` 新增 `expectedFormat` 优先使用；升级流程也从下载链接后缀兜底。
2. **HEAD 失败任务卡死**：HEAD 请求写在 try 外面，网络异常直接冒出去，任务永远停在 PENDING。整段包进 `try/use`，失败标 FAILED 并给“网络”原因。
3. **断点续传形同虚设**：重试时先 `cleanupChunksForFile` 删掉所有 `.part` 再重下。改成保留分片，`downloadChunk` 按字节范围续传；分片大小超过本段长度时（服务端换了文件）才丢弃重下。
4. **多分片暂停误报 FAILED**：分片模式下暂停后 `deferred.await()` 拿到未完成结果就 `chunkSuccess = false`，直接标失败。改成暂停时等 `awaitRunnable`，恢复后继续。
5. **合并后没校验总长度**：补了“合并文件大小必须等于 HEAD 报的长度”，对不上就报错，不当成功。
6. 顺手删掉一段永远执行不到的“大小不符是否仍接受”死代码；`updateMaxConcurrent` 会换新 Semaphore 导致许可记账错乱，改为只记日志不生效（当前无调用方）。

测试：`DownloadEngineTest` 9 个全过，新增“链接没扩展名时按接口格式命名”用例。

### `96904c9` 我的页返回栈
- 原来的问题：ViewModel 的 `section/isPlaylistDetail` 和 Tab 内的返回栈是两份状态，各改各的；切到别的 Tab 再回来层级被重置；系统返回和顶栏返回走两条路，容易出现“页面退了一层、状态没退”。
- 现在：`MainScreen` 持有 `myNavStack`，同步规则抽成 `MyNavSync.kt` 的 `syncMyNavStack()`（纯函数，可单测），`MainScreen` 和 `MyTab` 共用这一份规则；`MyTab` 内所有返回只负责出栈。切 Tab 不再丢层级。
- 删掉了一个多余回调（`onClearPlaylistDetail` 与 `onBackToList` 最终是同一个函数）。
- 测试：新增 `MyNavStackSyncTest` 5 个用例（进详情、退详情、回首页清栈、换 section 替换而非叠层、状态不变不动栈）。

### `bc21a83` 搜索联想 + 升级临时目录
- **搜索框清空后一直显示“加载中”**：清空输入走的分支没复位 `isLoadingSuggestions`。已修，新增回归测试（先失败后通过）。
- **升级中断残留 `upgrade_temp` 目录**：取消或异常时临时目录没删。改为记录本次创建的目录，取消/异常时清掉；新增测试验证（未修复前该测试确实失败）。

## 验证结果
- `:app:testDebugUnitTest` 全量 165 个通过（原基线 1 个失败已在前面修掉）
- `:app:lintDebug`：只剩 2 条存量警告（ScopedStorage、GetInstance）
- `:app:compileDebugKotlin --rerun-tasks`：0 warning（5 条 Koin DSL 弃用提示为存量）
- `:app:assembleRelease`：`app-release.apk` 6.76 MB，`versionName 1.1.0-bc21a83`

## 发现但没改的
1. **`CookieManager.sanitizeCookies` 正则多一个 `$`**：`"^[...]+$"` 写成了 `"^[...]+${'$'}"`，静态上看只允许单字符的 cookie 名。只在“粘贴完整 Cookie 登录”时生效，浏览器登录不经过这里。因为真实行为没改，没法提交回归测试，等确认后一行改掉。
2. **登录 7 天过期逻辑实际不生效**：`invalidateIfExpired()` 看 `cookie_time`，但 `touchCookieTime()` 全项目没人调用，`cookie_time` 永远不存在，所以永远返回“未过期”。要么在登录成功/启动时写一次时间，要么删掉这段逻辑，取决于你想要的策略。
3. **`needsRefresh()` 同样是死代码**（无人调用）。
4. **发现页 FM 请求没有 session 判重**：`loadFmSongs` 用 `isLoadingFm` 挡并发，`loadNextFmGroup` 只在入口检查，两次快速调用可能各自覆盖结果（影响小，只影响“换一批”节奏）。
5. **歌单搜索接口 `cloudsearch/get/web` 返回 `code=50000005`**：之前已记录，本轮未处理。
6. 本机无 adb 设备，所有改动**没做真机验证**；也没找到 >1000 首的线上歌单做端到端验证。
