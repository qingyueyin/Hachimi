# 贡献指南

提交代码前请阅读 [LICENSE](LICENSE) 中的使用前须知，并遵守 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)。

## 能做什么、不能做什么

本仓库接受：崩溃修复、UI/无障碍、构建与测试、文档勘误。

不接受：绕过网易云风控或版权保护的补丁、把 Cookie 或密钥写进仓库、把本应用改成面向传播/售卖下载内容的产品。

Issue 和 PR 里不要粘贴 Cookie、`MUSIC_U`、完整日志或密钥库密码。

## 版本号

`versionName` 为 `1.0.0-<git 短哈希>`，`versionCode` 为 `git rev-list --count HEAD`。不要在 `build.gradle.kts` 里手写死这两个值。

首次打出带签名的 Release 后，运行 `:app:printReleaseCertSha256`，把 SHA-256 配到 `HACHIMI_CERT_SHA256`，官方包才会显示「官方」。

## 开发环境

- Java 17+
- Android SDK（compileSdk 37）
- Windows 上用 `.\gradlew.bat`，其他系统用 `./gradlew`

```bash
.\gradlew.bat assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

## 代码约定

- 包名 `com.qing.hachimi`，发布 applicationId `com.qingyueyin.hachimi`
- UI 用 Miuix（`top.yukonga.miuix.kmp.*`），不要用 Material3 组件。`ripple` 作为 `LocalIndication` 可以保留
- `ButtonDefaults.buttonColors()` 的参数名是 `color`，不是 `containerColor`
- 对话框用 `WindowDialog`，不要用 Material `Dialog`
- 使用前须知文案改 `LegalNotice.kt`，并同步 LICENSE / README，不要只改一处

## 不要提交

- `*.keystore` / `*.jks`、签名密码
- `local.properties`、APK、`.so` 以外的构建产物（`liblyrico_taglib.so` 已纳入 jniLibs）
- Cookie、账号备份、真实用户日志
- `.claude/`、`.reasonix/` 等本地代理目录（已在 `.gitignore`）

## Pull Request

1. 基于当前 `main` 开分支
2. 改动能测的补或更新单元测试
3. 用仓库里的 [PR 模板](.github/PULL_REQUEST_TEMPLATE.md) 说明改了什么、怎么验证
4. 一个 PR 只做一件事

安全相关问题走 [SECURITY.md](SECURITY.md)，不要开公开 Issue 贴利用细节。

仓库地址：<https://github.com/qingyueyin/Hachimi>
