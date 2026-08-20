# Security Policy

*🇫🇷 Français : [SECURITY_FR.md](SECURITY_FR.md) · 🇨🇳 简体中文: [SECURITY_ZH.md](SECURITY_ZH.md) · 🇯🇵 日本語: [SECURITY_JA.md](SECURITY_JA.md)*

## Reporting a vulnerability

Please report security vulnerabilities privately through GitHub's
[private vulnerability reporting](https://github.com/guyiome/AndroidMoCap/security/advisories/new)
(Security tab → "Report a vulnerability"), not as a public issue.

This project is maintained by one person — expect a best-effort response, not a formal SLA. I'll
acknowledge reports as soon as I can and keep you updated while a fix is worked on.

## Scope

AndroidMoCap only communicates on the local network the phone is connected to (Wi-Fi). It never
talks to a remote server, and the app itself never initiates an outbound connection beyond an
optional GitHub Releases check for updates. Keep that in mind when assessing severity: exploiting
a network-facing issue here requires an attacker already present on the same local network.

Areas most relevant to a security review:

- `network/IFacialMocapSender.kt` — passive UDP listener (iFacialMocap/VBridger wire protocol),
  unauthenticated by design, matching the third-party protocol it implements.
- `network/VTubeStudioSender.kt` / `VTubeStudioProtocol.kt` — WebSocket client with a token-based
  auth handshake against VTube Studio's own Plugin API.
- `network/VmcOscSender.kt` — outbound-only OSC/UDP, no listening socket.
- `logging/AppLog.kt` / `LogFormatting.kt` — local log file; IP addresses are masked outside debug
  builds, and no face-tracking data is ever logged above `DEBUG` level.

## Supported versions

Only the latest published release is supported — there is no long-term maintenance branch.
