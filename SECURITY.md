# 安全政策

## 支持的版本

| 版本 | 支持 |
|------|------|
| 1.0.x | 是 |

## 报告漏洞

请使用 GitHub 的 [Privately report a vulnerability](https://github.com/qingyueyin/Hachimi/security/advisories/new)，不要开公开 Issue 贴利用细节、PoC 或用户数据。

报告里可以写：

- 受影响的版本 / commit
- 复现步骤
- 实际影响（例如本地 Cookie 是否会被读出、文件是否会被任意分享）

## 请勿随报告发送

- 完整 Cookie、`MUSIC_U`、账号备份
- 未脱敏的 `app.log` / `crash.log`
- 签名密钥库或密码

维护者收到后会确认是否受理。修复发布前，请勿公开讨论该漏洞。

## 不在安全范围

- 网易云官方接口变更、风控、账号被封
- 用户自己把 Cookie 发给第三人
- 依赖「所有文件访问权限」才能写入公共目录——这是产品设计，不是漏洞
- 第三方播放器打开下载文件时的行为

应用安全相关实现（供审计时对照）：

- Cookie 使用 EncryptedSharedPreferences
- `FileProvider` 不暴露整块外部存储
- Release 构建关闭 debug 日志；HTTP 日志不输出 Cookie 值
