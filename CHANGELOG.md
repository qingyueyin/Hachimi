# 更新记录

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [1.0.0] - 2026-09-06

### 新增

- Android 端独立发布（Kotlin / Compose / Miuix）
- 首次启动使用前须知：须勾选后才能进入，设置里可再读
- 发现、搜索、我的、下载、音质升级
- 浏览器 / Cookie / MUSIC_U 登录与历史账号
- 分片下载、断点续传、通知栏进度、文件夹分组、标签写入
- 底部导航液态玻璃效果（Miuix 0.9.3）

### 安全

- Cookie 加密存储
- FileProvider 限定目录
- Release 关闭 debug 日志，HTTP 日志不输出 Cookie 值
- 签名密钥不进仓库
- 设置页显示官方 / 非官方签名；非官方包会提示到 GitHub Releases 下载
