# 审查记录 2026-11-10：音质升级 / 下载链路

承接 `overnight-review-2026-11-09.md`。上一份留的 6 条里，前 3 条已在 `0b56593`
（Cookie 死代码清理 + 正则笔误 + 匿名 token 等待）修掉，当前代码里已无
`cookie_time` / `needsRefresh` 残留。本轮看音质升级与下载引擎，找到 5 条。

## 待修（按影响排序）

### 1. 升级失败只报「升级文件下载失败」，真实原因被丢弃
`UpgradeService.awaitDownloadedFile`（约 390 行）只看 `DownloadStatus.FAILED`
就抛固定文案；引擎其实已经把原因记在 `DownloadProgress.errorMessage`
（`DownloadFailure.NO_URL / NETWORK / STORAGE / INTERRUPTED`，见 `DownloadEngine.kt:82`），
但没有任何对外读取入口，`getStatus()` 只返回状态。
影响：11-10 那次「3 首全失败」只能翻日志才知道是 http 明文被拦。
另外 `DownloadEngine.kt:607` 把所有 `IOException` 归成「无法保存文件，请检查存储权限」，
TLS／明文拦截／HTTP 错误会被指向错误方向。
建议：给引擎加 `getErrorMessage(songId)`，升级文案拼上原因；分类补
`UnknownServiceException` / `SSLException`（网络安全策略）一类。

### 2. 升级快路径可能把 A 歌的音质套到 B 歌上（本轮新代码的漏洞）
`UpgradeService.downloadHistoryIds()` 只按文件路径把下载历史认成 songId。
用户手动改名后路径不变、ID 依旧匹配，但文件已经是另一首歌；此时升级会把
B 覆盖成 A（有 `.backup` 兜底，但仍是错歌）。
建议：比对下载记录里的 `songName` 与本地解析出的歌名（用现成的
`UpgradeApi.matchConfidence` 或字符串相似度，阈值 0.7），不一致就退回搜索。

### 3. 保留原文件模式升级后，列表里歌名变形
`UpgradeModels.kt:79` 的 `applyUpgrades` 用
`newFile.nameWithoutExtension.substringBefore(" - ")` 重算歌名。
保留模式下新文件叫 `Song [lossless].flac`，于是列表显示「Song [lossless]」，
再点检查会拿这个怪名字去搜索，必然搜不到。替换模式因为原名不变，没有这个问题。
建议：升级不改歌名和专辑，`song.name` / `song.album` 原样保留。

### 4. 下载失败后点「重试」会丢掉 `.part`，反而不能续传
`DownloadEngine.resumeDownload` 的 FAILED/CANCELLED 分支（约 830 行）先
`cleanupChunksForFile(file)` 再重启；`downloadWithChunks` 分片失败时（537-540 行）
也会清掉全部分片。
而从歌曲页重新下载同一首却会复用 `.part` 续传（472 行注释即此意）。
同一个文件，两种入口行为相反。PROGRESS 里「暂停/重试保留 .part」只对暂停成立。
建议：重试时保留分片（与重新下载一致）。若担心服务端换文件导致错位，
靠 `downloadChunk` 的越界校验 + 合并后大小校验兜底。

### 5. 升级历史 JSON 没有兜底，一条坏记录会让整个升级页不可用
`UpgradeHistoryManager.getRecords()` 直接 `JSONArray(json)` + `getString("status")` +
`UpgradeStatus.valueOf(...)`，任一异常都会冒到 `scanFoldersIncremental`
（`upgradedFilePaths()` 在扫描入口调用），界面只显示「扫描失败: …」，
且每次扫描都会再失败一次，用户没法自救。
建议：整个解析包一层 `runCatching`，坏记录跳过并记日志。

## 已确认不是问题

- 分片文件按名字排序合并（`mergeChunks`）：`chunkCount` 固定 4，`.part0..part3`
  字典序与数字序一致；将来若调到 ≥11 会错序，届时需要补零命名。
- `tasks` 只在 `startDownload` 里创建，进程被杀后未完成任务没有恢复入口；
  但用户重新下载同一首会复用 `.part` 续传，属于可接受行为。
