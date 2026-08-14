# AndroidMoCap

*🇫🇷 Français : [README_FR.md](README_FR.md) · 🇨🇳 简体中文: [README_ZH.md](README_ZH.md) · 🇯🇵 日本語: [README_JA.md](README_JA.md)*

Facial motion capture on Android, streamed live to Blender, Unity, VBridger, or directly to VTube
Studio over the local network. The phone becomes a standalone facial tracker: no third-party app, no
cloud service, just the front camera and a blendshape stream sent to the PC.

Born from the observation that there is currently no maintained Android alternative to MeowFace
(abandoned, its underlying tracking library having been deprecated): this project aims to fill that
gap, with an Android-specific constraint that no app in this space can fully erase -- the lack of a
dedicated depth sensor (unlike iPhone/TrueDepth) caps the achievable accuracy, regardless of
software quality.

Personal project, developed and maintained by a single person, in active development.

## Features

- **Automatic best-pipeline selection** based on device capabilities (GPU/CPU, RAM, cores, ARCore
  support) -- nothing to configure, the app adapts from high-end to entry-level, with automatic
  GPU → CPU fallback if the GPU delegate fails.
- **52 ARKit blendshapes** via MediaPipe Face Landmarker, plus gaze-direction estimation (not
  natively provided by MediaPipe, reconstructed from eye blendshapes).
- **Triple network output**: VMC/OSC protocol (Blender, Unity), iFacialMocap/UDP protocol
  (VBridger), and direct VTube Studio integration via its own proprietary Plugin API (VTube Studio
  doesn't accept VMC/OSC as input).
- **On-demand neutral-pose calibration**, with a countdown.
- Minimal HUD readable regardless of phone orientation, detailed settings (displayed-blendshape
  selection, low-battery threshold, power-save mode, 478-point tracking-mesh debug overlay).
- **Power-save mode**: dims the screen and cuts the camera preview after inactivity without
  interrupting tracking or sending -- designed for long streaming sessions with the phone sitting
  away from the user.

## Requirements

- Android 11 (API 30) or later.
- A **physical device** with a front camera -- the Android emulator doesn't provide a usable camera
  feed for tracking.
- Phone and receiving PC on the **same local Wi-Fi network**.

## Installation

The app isn't distributed on the Play Store. Download the latest APK from
[GitHub Releases](../../releases) and install it directly. Android will show an "unknown source"
warning at install time -- normal for an APK distributed outside a store, to be allowed once in
settings during installation.

## PC-side connection

**Blender / Unity**: use a VMC-compatible addon/package, configured to listen on the same port
(`39539` by default, configurable on the app side).

**VTube Studio**: doesn't accept VMC/OSC natively -- no setting of that kind in its options. The app
offers direct integration via VTube Studio's proprietary Plugin API (choose "VTube Studio" in the
connection settings, PC IP + port 8001 by default): an authorization popup appears in VTube Studio
on first connection, then the created parameters must be mapped once in VTube Studio's parameter
editor to animate the model.

**VBridger**: select the iFacialMocap protocol in the app's settings, then follow VBridger's
instructions pointing to the IP shown on the phone -- it's VBridger that connects in to the app, no
IP to enter on the phone side for this path.

## Privacy and network

The app only communicates with the target chosen in settings, on the local network -- no
third-party service, no telemetry, no data sent outside this voluntary stream to the receiving PC.

**Logs**: kept locally (a file private to the app, never transmitted automatically), "Error" level
by default, adjustable in Settings > Logging. May contain technical information (errors, connection
status) and the configured local IP address -- never face-tracking data. IP addresses are
automatically masked outside development builds. A "Share logs" button lets you send this file
(e.g. to report an issue) -- entirely at the user's initiative, who chooses the destination.

## Building from source

1. **Download the MediaPipe model** (required, too large to be versioned) and place it at
   `app/src/main/assets/face_landmarker.task`:
   `https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task`
   If the link has changed, start from the official
   [Face landmark detection guide for Android](https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker/android)
   page (the "Model" section).
   - **Optional**: for experimental tongue-out detection (stage 3, off by default), a second model,
     `app/src/main/assets/image_embedder.tflite`:
     `https://storage.googleapis.com/mediapipe-models/image_embedder/mobilenet_v3_small/float32/latest/mobilenet_v3_small.tflite`.
     The app builds and runs normally without it -- only this experimental feature needs it.
2. Open the folder in Android Studio (`File > Open`). The first Gradle sync downloads AGP, Kotlin
   and the ARCore/CameraX/MediaPipe/JavaOSC/nv-websocket-client/kotlinx.serialization dependencies
   listed in `gradle/libs.versions.toml`.
3. Build and run on a **physical device** (see Requirements) -- no emulator possible for testing
   tracking.

## Project structure

```
app/src/main/java/com/guyiome/androidmocap/
  MainActivity.kt              Camera permission + Compose entry point
  capabilities/                Device capability detection (ARCore, GPU, RAM, thermal)
  tracking/                    Tier selection + MediaPipe Face Landmarker wrapper + rotation math
  camera/                      CameraX driving (front camera -> MPImage, bitmap pool)
  sensors/                     Phone orientation, HUD icons, battery
  network/                     OSC/UDP sending (VMC), UDP sending (iFacialMocap), WebSocket (VTube Studio Plugin API)
  settings/                    Settings persistence (DataStore)
  ui/                          ViewModel + Compose screens (HUD, settings, mesh overlay)
```

## Tests

Pure JVM unit test suite (no Android/Robolectric dependency) under `app/src/test/`.
Function-by-function detail, with explicit reasons for what's deliberately not covered, in
`docs/AndroidMoCap_unit_tests.md`. Run with:

```
./gradlew testDebugUnitTest
```

## Documentation

- `docs/AndroidMoCap_functional_spec.md` -- what the app does today, from the user's side.
- `docs/AndroidMoCap_technical_spec.md` -- architecture, capture pipeline, network protocols,
  non-functional constraints.
- `docs/AndroidMoCap_unit_tests.md` -- test coverage detail.

## Roadmap

Main items still open:

- Experimental puffed-cheek detection (`cheekPuff`) -- same family as the already-implemented
  tongue-out detection, still at the design stage.
- Semi-automatic update check (comparison against the latest GitHub Releases tag, a direct link
  rather than a silent install -- impossible outside a store).
- Settings screens' adaptation to system orientation on large screens (tablet).
- Per-blendshape weight/gain adjustment (+ adjustable smoothing).

## License

Distributed under the [PolyForm Shield 1.0.0](https://polyformproject.org/licenses/shield/1.0.0)
license (see `LICENSE` -- the only legally authoritative version; unofficial, unreviewed machine
translations exist for reference in [LICENSE_ZH.md](LICENSE_ZH.md) and
[LICENSE_JA.md](LICENSE_JA.md)): free use, including commercial, except building a product that
would compete with the software itself. This is not an "open source" license in the strict (OSI)
sense -- source code visible and modifiable for personal use, but not freely redistributable as a
competing product.

## Contributing

See `docs/CONTRIBUTING.md` before opening a pull request -- any contribution implies
acceptance of the contributor license agreement (`docs/CLA.md`).

## Publishing a release (maintainer)

Signing configured via environment variables (`RELEASE_KEYSTORE_BASE64`,
`RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`), read locally or from
GitHub Actions secrets -- never committed. Pushing a tag triggers publishing:

```
git tag v0.2.0
git push origin v0.2.0
```

The workflow (`.github/workflows/release.yml`) builds the APK (including the MediaPipe model
download), signs it, and creates a GitHub Release with the APK attached.

**Beta channel**: a tag containing `-beta` (e.g. `v0.3.0-beta.1`) follows exactly the same path, but
the Release is published as a GitHub *prerelease* -- skipped by default by update-tracking tools
(Obtainium and similar) unless explicitly enabled on the installer's side. Handy for sharing a test
build without it showing up as a "recommended update".

```
git tag v0.3.0-beta.1
git push origin v0.3.0-beta.1
```
