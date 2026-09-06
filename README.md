# Hachimi

<p align="center">
  <img src="ico/猫_256px.png" alt="Hachimi Logo" width="128" height="128">
</p>

<p align="center">
  <b>Android 网易云音乐下载工具</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android" alt="Platform">
  <img src="https://badgen.net/github/release/qingyueyin/Hachimi?icon=github" alt="Version">
  <img src="https://img.shields.io/github/downloads/qingyueyin/Hachimi/total?style=flat-square" alt="Downloads">
  <img src="https://img.shields.io/badge/License-MIT-green?style=flat-square" alt="License">
  <img src="https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?style=flat-square&logo=kotlin" alt="Kotlin">
  <a href="https://github.com/qingyueyin/Hachimi/stargazers"><img src="https://img.shields.io/github/stars/qingyueyin/Hachimi?style=flat-square" alt="Stars"></a>
  <a href="https://linux.do"><img src="https://img.shields.io/badge/LINUX_DO-%E7%A4%BE%E5%8C%BA%20%E9%93%BE%E6%8E%A5-blue?style=flat-square" alt="LINUX DO"></a>
</p>

技术栈：Kotlin + Jetpack Compose + [Miuix](https://github.com/compose-miuix-org/miuix)。

> **本项目与网易云音乐及其关联公司无任何合作或授权关系。** 仅供学习交流与界面测试。使用前请阅读 [使用前须知](#使用前须知) 和 [LICENSE](LICENSE)。

## 目录

- [使用前须知](#使用前须知)
- [功能](#功能)
- [环境](#环境)
- [构建](#构建)
- [测试](#测试)
- [签名](#签名)
- [目录结构](#目录结构)
- [文档](#文档)
- [贡献](#贡献)
- [License](#license)

## 使用前须知

1. **用途限制** — 不得用于商业运营、批量下载、传播或售卖受版权保护的内容
2. **及时删除** — 体验后尽快删除本软件及保存的音频、歌词与封面，建议不超过 24 小时
3. **版权与账号** — 只使用本人合法持有的网易云音乐账号；下载内容版权归原权利人
4. **权限与数据** — 写入公共目录需要所有文件访问权限；导出日志请勿公开分享
5. **风险自负** — 账号异常、数据丢失、接口变更及法律风险由使用者自行承担
6. **获取渠道** — 只从 [GitHub Releases](https://github.com/qingyueyin/Hachimi/releases) 安装官方包，不要买来路不明的 APK

应用首次启动会弹出上述条款，须勾选确认后才能进入。之后可在 **设置 → 使用前须知** 再次查看。完整约定见 [LICENSE](LICENSE)。

官方安装包用项目密钥签名。设置页会显示「官方 / 非官方构建」。名称与图标「Hachimi」仅限本仓库使用。

**不要**在 Issue、PR 或日志里粘贴 Cookie、`MUSIC_U` 或其他登录凭据。

## 功能

- **搜索与解析** — 歌曲 / 歌手 / 专辑 / 歌单 / 播客；粘贴 `music.163.com` 链接自动识别
- **发现 / 我的** — 推荐、新碟、私人 FM、红心、云盘、听歌记录（部分功能需登录）
- **登录** — 浏览器登录、Cookie / MUSIC_U、历史账号切换
- **下载** — 分片、断点续传、并发控制、通知栏进度、文件夹分组、歌词与标签
- **音质升级** — 扫描本地文件并匹配更高音质
- **深度链接** — 打开 `https://music.163.com/...` 并解析

## 环境

| 项 | 值 |
|------|------|
| Java | 17+ |
| Android | 13+（minSdk 33） |
| ABI | 仅 arm64-v8a |
| 包名 | `com.qingyueyin.hachimi`（Debug 为 `.debug`） |
| Kotlin | 2.3.21 |
| AGP | 9.1.0 |
| compileSdk / targetSdk | 37 / 33 |
| Miuix | 0.9.3 |
| OkHttp | 5.3.2 |
| Coil | 3.4.0 |

需要「所有文件访问权限」才能写入公共下载目录。适合自行安装，不适合上架 Google Play。

## 构建

```bash
git clone https://github.com/qingyueyin/Hachimi.git
cd Hachimi

# Debug APK
.\gradlew.bat assembleDebug
# → app\build\outputs\apk\debug\app-debug.apk

# Release APK
.\gradlew.bat assembleRelease
```

未配置签名凭据时，`assembleRelease` 生成未签名 APK。

`versionName` 为 `1.0.0` 加上当前 Git 短哈希，`versionCode` 为提交总数（与 CloudX 相同）。首次正式签名后执行：

```bash
.\gradlew.bat :app:printReleaseCertSha256
```

把输出的 SHA-256 写进 `~/.gradle/gradle.properties` 的 `HACHIMI_CERT_SHA256=`，之后官方包会显示「官方」。

## 测试

```bash
.\gradlew.bat :app:testDebugUnitTest
```

## 签名

发布签名从环境变量或用户级 `~/.gradle/gradle.properties` 读取，**不要**把密码或 `*.keystore` 写进仓库：

```properties
HACHIMI_STORE_PASSWORD=...
HACHIMI_KEY_PASSWORD=...
HACHIMI_KEY_ALIAS=hachimi
```

密钥库默认路径：`app/release.keystore`（已被 `.gitignore` 忽略）。

## 目录结构

```
app/src/main/java/com/qing/hachimi/
├── MainActivity.kt
├── LegalNotice.kt     # 使用前须知（与 LICENSE / 首次启动弹窗共用）
├── data/              # API、本地存储、Repository
├── downloader/        # 分片下载与前台服务
├── service/           # 音质升级
├── ui/screens/        # Compose 界面与 ViewModel
└── util/
```

## 文档

| 文件 | 说明 |
|------|------|
| [LICENSE](LICENSE) | MIT 源码许可 + 免责声明 |
| [THIRD_PARTY.md](THIRD_PARTY.md) | 第三方组件许可证 |
| [CONTRIBUTING.md](CONTRIBUTING.md) | 如何构建、测试和提交 |
| [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) | 行为公约 |
| [SECURITY.md](SECURITY.md) | 安全漏洞报告 |
| [SUPPORT.md](SUPPORT.md) | 提问与反馈 |
| [CHANGELOG.md](CHANGELOG.md) | 版本记录 |

## 贡献

请先读 [CONTRIBUTING.md](CONTRIBUTING.md) 和 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)。UI 使用 Miuix，不要引入 Material3 组件（`ripple` 除外）。

## License

源码以 [MIT License](LICENSE) 发布。MIT 约束的是源代码再分发；使用本应用访问第三方服务、下载与传播内容须遵守 [使用前须知](#使用前须知)。
