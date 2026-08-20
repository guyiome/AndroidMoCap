# 安全政策

*🇬🇧 English: [SECURITY.md](SECURITY.md) · 🇫🇷 Français : [SECURITY_FR.md](SECURITY_FR.md) · 🇯🇵 日本語: [SECURITY_JA.md](SECURITY_JA.md)*

> ⚠️ **机器翻译声明**：本文档由 AI 自动翻译生成，尚未经过人工或母语者校对，仅供参考。具有法律效力的
> 版本是英文原版 [SECURITY.md](SECURITY.md)。

## 报告漏洞

请通过 GitHub 的
[私密漏洞报告](https://github.com/guyiome/AndroidMoCap/security/advisories/new)
功能（Security 标签页 → "Report a vulnerability"）私下报告安全漏洞，而不要提交公开 issue。

本项目由一人独立维护——响应为尽力而为，没有正式的 SLA。我会尽快确认收到报告，并在修复过程中及时
告知进展。

## 范围

AndroidMoCap 仅在手机所连接的本地网络（Wi-Fi）上通信。它从不与远程服务器通信，应用本身也从不主动
发起出站连接，唯一的例外是可选的 GitHub Releases 更新检查。评估严重程度时请留意这一点：利用此处
的网络相关问题需要攻击者已经处于同一本地网络中。

与安全审查最相关的部分：

- `network/IFacialMocapSender.kt` —— 被动 UDP 监听器（iFacialMocap/VBridger 线协议），按设计不带
  身份验证，与其实现的第三方协议保持一致。
- `network/VTubeStudioSender.kt` / `VTubeStudioProtocol.kt` —— 针对 VTube Studio 自身 Plugin API
  的 WebSocket 客户端，带有基于令牌的身份验证握手。
- `network/VmcOscSender.kt` —— 仅出站的 OSC/UDP，无监听套接字。
- `logging/AppLog.kt` / `LogFormatting.kt` —— 本地日志文件；IP 地址在非调试构建中会被掩码处理，
  且面部追踪数据从不会以高于 `DEBUG` 的级别被记录。

## 支持的版本

仅支持最新发布的版本——没有长期维护分支。
