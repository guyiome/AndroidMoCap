# AndroidMoCap

*🇬🇧 English: [README.md](README.md) · 🇫🇷 Français : [README_FR.md](README_FR.md) · 🇯🇵 日本語: [README_JA.md](README_JA.md)*

> ⚠️ **机器翻译声明**：本文档由 AI 自动翻译生成，尚未经过人工或母语者校对，可能存在不准确之处。如有疑问，请以英文原版为准：[README.md](README.md)。

在 Android 手机上进行面部动作捕捉，通过局域网实时传输到 Blender、Unity、VBridger，或直接传输到 VTube
Studio。手机变成一个独立的面部追踪器：无需第三方应用，无需云服务，只需前置摄像头和发送到 PC 的
blendshape 数据流。

本项目源于一个观察：目前没有一个持续维护的 Android 版 MeowFace 替代品（该项目已被放弃，其底层追踪库也已
弃用）。本项目旨在填补这一空白，同时面对一个 Android 平台无法完全消除的固有限制——缺少专用深度传感器
（不同于 iPhone 的 TrueDepth），这限制了可达到的精度上限，无论软件质量如何。

个人项目，由一人独立开发和维护，目前正在积极开发中。

## 功能特性

- **自动选择最佳处理管线**，根据设备能力（GPU/CPU、内存、核心数、ARCore 支持情况）自动适配，从高端到
  入门级设备均可运行，无需任何配置；GPU 委托初始化失败时自动回退到 CPU。
- 通过 MediaPipe Face Landmarker 提供 **52 个 ARKit 格式的 blendshape**，以及视线方向估算（MediaPipe
  本身不提供该数据，由眼部相关 blendshape 重建而来）。
- **三种网络输出协议**：VMC/OSC 协议（Blender、Unity）、iFacialMocap/UDP 协议（VBridger），以及通过
  VTube Studio 自有的 Plugin API 直接集成（VTube Studio 不接受 VMC/OSC 作为输入）。
- **按需校准中性姿态**，带倒计时。
- 极简 HUD，无论手机朝向如何都能清晰辨识；详细设置项（可显示 blendshape 选择、低电量阈值、省电模式、
  478 点追踪网格调试叠加层）。
- **省电模式**：闲置一段时间后自动调暗屏幕并隐藏摄像头预览画面，但追踪与数据发送不受影响——专为手机
  放在远处、长时间直播的场景设计。

## 系统要求

- Android 11（API 30）或更高版本。
- 一台带前置摄像头的**真实设备**——Android 模拟器无法提供可用于追踪的摄像头画面。
- 手机与接收端 PC 需处于**同一局域网 Wi-Fi**。

## 安装

本应用未在 Play 商店上架，请从 [GitHub Releases](../../releases) 下载最新 APK 并直接安装。安装时
Android 会提示"未知来源"警告——这是应用商店以外分发的 APK 的正常提示，在安装设置中临时允许即可。

## PC 端连接方式

**Blender / Unity**：使用兼容 VMC 协议的插件/扩展，配置为监听同一端口（默认为 `39539`，可在应用内
修改）。

**VTube Studio**：并不原生支持 VMC/OSC——其设置中没有此类选项。本应用通过 VTube Studio 自有的 Plugin
API 提供直接集成（在连接设置中选择"VTube Studio"，填写 PC 的 IP，端口默认为 8001）：首次连接时 VTube
Studio 会弹出授权提示，随后需要在 VTube Studio 的参数编辑器中手动映射一次新创建的参数，才能驱动模型。

**VBridger**：在应用设置中选择 iFacialMocap 协议，然后按照 VBridger 的说明，指向手机上显示的 IP
地址——由 VBridger 主动连接本应用，手机端无需输入任何 IP。

## 隐私与网络

本应用只与设置中选定的目标进行通信，且仅限于局域网内——不涉及任何第三方服务，不进行任何遥测，除了这条
主动发往接收端 PC 的数据流之外不会发送任何数据。

**日志**：仅保存在本地（应用私有文件，从不自动上传），默认级别为"错误"，可在 设置 > 日志 中调整。日志
可能包含技术信息（错误、连接状态）以及配置的本机 IP 地址——但绝不包含面部追踪数据本身。在开发构建之外，
IP 地址会自动打码。"分享日志"按钮可以发送该文件（例如用于反馈问题）——完全由用户主动发起，目标地址由
用户自行选择。

## 从源码构建

1. **下载 MediaPipe 模型**（必需，因体积过大未纳入版本控制），放置于
   `app/src/main/assets/face_landmarker.task`：
   `https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task`
   如果链接已失效，请从官方
   [Face landmark detection guide for Android](https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker/android)
   页面（"Model" 部分）重新获取。
   - **可选**：用于实验性的吐舌检测功能（第三阶段，默认关闭）的第二个模型，
     `app/src/main/assets/image_embedder.tflite`：
     `https://storage.googleapis.com/mediapipe-models/image_embedder/mobilenet_v3_small/float32/latest/mobilenet_v3_small.tflite`。
     没有这个文件应用也能正常构建和运行——只有这个实验性功能需要它。
2. 在 Android Studio 中打开该目录（`File > Open`）。首次 Gradle 同步会下载 AGP、Kotlin，以及
   `gradle/libs.versions.toml` 中列出的 ARCore/CameraX/MediaPipe/JavaOSC/nv-websocket-client/
   kotlinx.serialization 等依赖。
3. 在**真实设备**上构建并运行（见"系统要求"）——追踪功能无法在模拟器上测试。

## 项目结构

```
app/src/main/java/com/guyiome/androidmocap/
  MainActivity.kt              相机权限 + Compose 入口
  capabilities/                设备能力检测（ARCore、GPU、内存、温度）
  tracking/                    档位选择 + MediaPipe Face Landmarker 封装 + 旋转数学运算
  camera/                      CameraX 驱动（前置摄像头 -> MPImage，位图池）
  sensors/                     手机朝向、HUD 图标、电量
  network/                     OSC/UDP 发送（VMC）、UDP 发送（iFacialMocap）、WebSocket（VTube Studio Plugin API）
  settings/                    设置持久化（DataStore）
  ui/                          ViewModel + Compose 界面（HUD、设置、网格叠加层）
```

## 测试

纯 JVM 单元测试套件（不依赖 Android/Robolectric），位于 `app/src/test/`。逐函数的详细说明，包括
哪些内容有意未覆盖及其原因，见 `docs/AndroidMoCap_tests_unitaires.md`（英文）。运行方式：

```
./gradlew testDebugUnitTest
```

## 文档

以下文档目前仅提供英文/法文版本：

- `docs/AndroidMoCap_spec_fonctionnelle.md` -- 应用当前的功能说明，面向用户。
- `docs/AndroidMoCap_spec_technique.md` -- 架构、采集管线、网络协议、非功能性约束。
- `docs/AndroidMoCap_tests_unitaires.md` -- 测试覆盖详情。

## 路线图

目前仍待完成的主要事项：

- 实验性的鼓腮检测（`cheekPuff`）——与已实现的吐舌检测属于同一类功能，尚处于设计阶段。
- 半自动更新检查（与最新的 GitHub Releases 标签比对，提供直接下载链接而非静默安装——商店外分发无法
  做到静默安装）。
- 设置界面在大屏幕（平板）上适配系统方向。
- 按 blendshape 单独调整权重/增益（+ 可调平滑）。

## 许可证

基于 [PolyForm Shield 1.0.0](https://polyformproject.org/licenses/shield/1.0.0) 许可证发布（见
`LICENSE`；中文翻译仅供参考，见 [LICENSE_ZH.md](LICENSE_ZH.md)，如有出入以英文原版为准）：可自由
使用，包括商业用途，但不得用本软件构建与其竞争的产品。这不是严格意义上（OSI 定义）的"开源"许可证——
源代码对个人使用可见、可修改，但不能作为竞品自由再分发。

## 参与贡献

在提交 pull request 前请先阅读 `docs/CONTRIBUTING_ZH.md`（中文翻译，仅供参考）——任何贡献均意味着
接受贡献者许可协议（`docs/CLA_ZH.md`，中文翻译，仅供参考；具有法律效力的版本为英文版
`docs/CLA.md`）。

## 发布新版本（维护者专用）

签名通过环境变量配置（`RELEASE_KEYSTORE_BASE64`、`RELEASE_KEYSTORE_PASSWORD`、`RELEASE_KEY_ALIAS`、
`RELEASE_KEY_PASSWORD`），本地读取或来自 GitHub Actions secrets——从不提交到仓库。推送标签即可触发
发布：

```
git tag v0.2.0
git push origin v0.2.0
```

工作流（`.github/workflows/release.yml`）会构建 APK（包含下载 MediaPipe 模型的步骤）、签名，并创建
带有 APK 附件的 GitHub Release。

**Beta 频道**：包含 `-beta` 的标签（例如 `v0.3.0-beta.1`）走完全相同的流程，但该 Release 会被标记为
GitHub 的 *prerelease*——更新追踪工具（Obtainium 等）默认会跳过它，除非安装端显式启用。适合分享测试
版本而不让它显示为"推荐更新"。

```
git tag v0.3.0-beta.1
git push origin v0.3.0-beta.1
```
