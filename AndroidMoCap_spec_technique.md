# AndroidMoCap — Technical specification

*🇫🇷 Version française : [AndroidMoCap_spec_technique_FR.md](AndroidMoCap_spec_technique_FR.md)*

*Reference document on the current architecture and implementation. Describes how the app is
built, at technical detail level -- not a changelog, not a decision history, not a roadmap. For
user-facing functional scope, see `AndroidMoCap_spec_fonctionnelle.md`.*

## 1. Overview

Native Android application (Kotlin, Jetpack Compose), layered architecture, single screen
(full-screen camera + Compose overlays), MVVM with a central `MainViewModel`/`MainUiState`.

```
app/src/main/java/com/guyiome/androidmocap/
  MainActivity.kt      Camera permission, Compose entry point
  capabilities/         Device capability detection (ARCore, GPU, RAM, thermal)
  tracking/             Tier selection, MediaPipe Face Landmarker wrapper, rotation math
  camera/                CameraX driving (front camera -> MPImage, bitmap pool)
  sensors/               Phone orientation, HUD icons, battery
  network/               OSC/UDP sending (VMC) and UDP sending (iFacialMocap)
  settings/              Settings persistence (DataStore)
  ui/                    ViewModel + Compose screens (HUD, settings, mesh overlay)
```

Stack: Kotlin, AGP 9.x, Jetpack Compose, CameraX, MediaPipe Tasks Vision (Face Landmarker), ARCore
(Augmented Faces, `OPTIMAL` tier only), JavaOSC (VMC protocol), nv-websocket-client +
kotlinx.serialization (VTube Studio Plugin API), AndroidX DataStore (Preferences).

`minSdk = 30` (Android 11), `compileSdk = targetSdk = 37` (required by recent AndroidX
dependencies).

## 2. Capture pipeline

`CameraController` drives CameraX with two separate use cases: `ImageAnalysis` (bound permanently
from startup, never unbound) feeds frames to MediaPipe; `Preview` (the displayed preview) is
added/removed independently based on power-save mode, without ever interrupting analysis.

Per accepted frame (after target-rate filtering, see §3): conversion to a `Bitmap` via a reused pool
(avoids a per-frame allocation), rotation applied if needed (cached matrix, since the angle stays
constant as long as the app remains portrait-locked -- see §7), then handed to
`FaceLandmarkerHelper.detectAsync` in `LIVE_STREAM` mode. The bitmap is tracked by frame timestamp
(not by object reference, since MediaPipe may wrap the image internally) and reclaimed into the pool
via `releaseFrame()` once the MediaPipe result for that exact timestamp is received.

`FaceLandmarkerHelper` first attempts the GPU delegate if `TierConfig.preferGpuDelegate`, falling
back to CPU on initialization failure. Per-frame output (`FaceTrackingResult`): the blendshape list
(`BlendshapeScore`), head rotation matrix/angles, per-eye gaze angles (computed by a pure function,
`computeEyeGazeDegrees`, testable in the JVM), and the 478-point mesh (also consumed by the
EAR-based blink correction below).

**Blink reliability**: `tracking/EyeAspectRatio.kt` computes a geometric Eye Aspect Ratio per eye
from the mesh. MediaPipe's own `eyeBlink` blendshape leaks between eyes (a deliberate wink on one
side also raises the other eye's score) -- `tracking/EyeBlinkCorrection.kt` damps one eye's score
when its EAR indicates it's still clearly open, applied in `MainViewModel.handleTrackingResult()`
before any network sending or display. `EyeOpennessSmoother` smooths the EAR's rise (instant attack,
~3s release) so that landmark-tracking drift during a held closure isn't mistaken for a genuine
reopening. The "closed" reference (`AdaptiveEarFloor`) is self-adaptive per eye rather than a fixed
constant, since a reference measured face-on doesn't hold at other camera angles.

The calibration button (`MainHud`) turns red if a drift in the calibrated pose is detected (face at
rest but pose not returning close to zero, or a loss of face detection followed by redetection) --
purely informational, never an automatic action, only resolved by an explicit new calibration. Pure,
JVM-tested logic: `tracking/CalibrationAnomaly.kt`/`BlendshapeStability.kt`.

## 3. Tier selection (`TrackingTier`)

`DeviceCapabilityDetector.detect()` builds a snapshot of device capabilities (ARCore support, the
official Android performance class if available, core count, total RAM) at startup.
`TrackingTierSelector` derives a tier from it:

- **`COMPATIBLE`** -- entry-level devices, lowest target rate, CPU delegate.
- **`STANDARD`** -- mid-range profile.
- **`OPTIMAL`** -- high-end devices with ARCore support, highest target rate, uses ARCore head pose
  (`useArCorePose`), see §4.

The target rate (`targetFps`) per tier is applied in `CameraController.processFrame()`: excess
frames are dropped before any allocation/rotation, before even reaching MediaPipe.

`DeviceCapabilityDetector.isThermalThrottling()` (watches `PowerManager.currentThermalStatus`) is
polled continuously during capture (`MainViewModel.startThermalPolling()`, every 5 seconds, active
between `ON_START`/`ON_STOP`): under heating, the target rate is halved (10 fps floor) via the same
hot-swap points as the per-tier rate above (`CameraController`/`ArCoreHeadPoseTracker.setTargetFps`),
and ramps back up automatically once heating subsides. Deliberately limited to the target rate --
neither the GPU/CPU delegate nor the camera source (ARCore/CameraX) changes at runtime, only a full
tier change (never done mid-session) would touch those. Pure, JVM-tested logic:
`tracking/ThermalThrottle.kt` (`ThermalThrottleState.next()`).

Alongside the manual tier selector (`tierOverride`, diagnostics only), a hidden debug mock panel
(`DiagnosticsScreen`, unlocked by multi-tapping the app's version row) allows forcing three
behaviors that are hard to trigger naturally on a given device: thermal throttling (live), ARCore
unavailable, and GPU delegate unavailable (the latter two on next launch, same constraint as
`tierOverride` -- see §6).

## 4. ARCore fusion (`OPTIMAL` tier)

ARCore Augmented Faces manages camera access itself internally and cannot coexist with CameraX's
`ImageAnalysis` on the same front camera (only one active Camera2 client at a time). For this tier
only, `Session` (ARCore) drives the camera via `ArCoreHeadPoseTracker`, frames are retrieved via
`Frame.acquireCameraImage()` (always YUV_420_888 on the ARCore side, no RGBA option) then manually
converted to an ARGB_8888 `Bitmap` (`yuv420ToBitmap()`) before `BitmapImageBuilder` -- not
`MediaImageBuilder` directly, which requires RGBA -- to keep feeding MediaPipe for blendshapes; head
pose uses `AugmentedFace.centerPose` (retrieved via a dedicated callback in `MainViewModel`, GL
thread) instead of `facialTransformationMatrixes()`. Automatic, silent fallback to CameraX +
`CameraController` (same as the `STANDARD` tier) if Augmented Faces turns out unavailable at
runtime, decided synchronously as early as `MainViewModel.initializeTracking()`.

**Live camera background**: this tier's `GLSurfaceView` displays a full-screen textured quad using
the OES texture ARCore already feeds via `Session#setCameraTextureName`, filling the gap left by
this tier's lack of a live preview compared to CameraX's `PreviewView` -- the mesh overlay follows
the same setting (`faceMeshOverlayEnabled`) as CameraX (`ui/MeshOverlayVisibility.kt`), it is not
forced on this tier. Hidden in power-save mode just like the CameraX preview
(`ArCoreHeadPoseTracker.setBackgroundRenderingEnabled`). The quad's rotation is fixed, independent of
the phone's physical orientation, exactly like `cameraRotationDegrees` on the CPU side; laterality is
deliberately never mirrored (native/anatomical behavior, this tier has no `PreviewView` equivalent to
stay consistent with, unlike the mesh, which stays mirrored for CameraX).

Dedicated classes: `ArCoreHeadPoseTracker`, `ArCoreFaceSelector` (primary-face selection when
several are detected, pure function tested in the JVM: `pickPrimary`). Mirroring is deliberately
never applied to the image sent to MediaPipe, only to the display, same convention as
`CameraController`.

**Known limitations**: `DeviceCapabilityDetector.detect()` reads
`ArCoreApk.checkAvailability().isSupported` synchronously at startup (detailed in
`ArCoreHeadPoseTracker.kt`'s class kdoc); no bitmap pool for the ARCore path (unlike
`CameraController` on the CameraX side).

## 5. Network protocols

### VMC/OSC (`VmcOscSender`)

Target: Blender, Unity -- not VTube Studio (which doesn't accept the VMC/OSC protocol as input). A
single `OSCBundle` is sent per frame, grouping one `/VMC/Ext/Blend/Val` message per blendshape
(~52) followed by an `/Apply` -- a single network call per frame rather than one UDP packet per
blendshape. Target IP/port entered manually in the app (the phone is the sender, it must know its
destination).

Best-effort disconnection detection (`connect()`-ed socket + dedicated `receive()` probe): relies on
delivery of an ICMP "port unreachable" packet, not guaranteed depending on network configuration --
the "connected" icon can therefore stay lit even without an active receiver on some networks. Never
affects sending itself (best-effort, never blocking).

### iFacialMocap/UDP (`IFacialMocapSender`)

Target: VBridger. Inverted model: the phone listens passively and displays its own local IP in
settings; it's VBridger that initiates the connection to that IP. No network entry required on the
phone side for this path. Same best-effort disconnection detection as VMC above, same limitation.

### VTube Studio Plugin API (`VTubeStudioSender`)

Target: VTube Studio, directly, via its proprietary Plugin API (WebSocket JSON, port 8001 by
default) rather than VMC/OSC. The phone is a WebSocket client connecting to the PC's IP/port (like
VMC, unlike iFacialMocap). Multi-step asynchronous connection cycle
(`VTubeStudioConnectionState`, pure and tested): socket opening, authentication (a token persisted
after a first user-authorization popup in VTube Studio, reused on subsequent connections -- with an
automatic retry via a new popup if the stored token is rejected/revoked, plus a manual "Forget
token" button in the Connection screen), creation of one custom parameter per blendshape (ARKit
names, discovered dynamically on the first frame rather than a hardcoded list -- a name collision
with another already-connected plugin, e.g. VBridger, doesn't abort the whole connection, only that
one parameter is lost), then value injection every frame (`InjectParameterDataRequest`). Pure and
tested JSON encoding/decoding (`VTubeStudioProtocol.kt`) via kotlinx.serialization
(`encodeDefaults = true` is load-bearing -- see its kdoc); WebSocket transport via
**nv-websocket-client** (`VTubeStudioSender.kt`, the only piece not testable in the JVM) -- not
OkHttp, which unconditionally proposes the `permessage-deflate` extension with no public setting to
disable it, incompatible with VTube Studio's `websocket-sharp` server. Created parameters aren't
automatically recognized by an existing Live2D model -- the user must map them once in VTube
Studio's parameter editor.

Requires `res/xml/network_security_config.xml` (`<base-config cleartextTrafficPermitted="true">`,
referenced from `AndroidManifest.xml`): WebSocket libraries honor Android's network security policy
(unencrypted traffic blocked by default since API 28), unlike the raw `DatagramSocket` used by
VMC/iFacialMocap, which isn't subject to it.

The three protocols (VMC, iFacialMocap, VTube Studio) are mutually exclusive at runtime (only one
`ConnectionType` active), the choice persisted via `ConnectionSettingsStore`.

## 6. Settings persistence

`AppSettingsStore` and `ConnectionSettingsStore` (DataStore Preferences): low-battery threshold,
power-save mode (toggle + delay), mesh overlay, connection type and network target (VMC IP, VTube
Studio IP + auth token), and -- optionally -- the selection of blendshapes displayed on the main
screen. The latter is not persisted by default (reset on every launch), but a dedicated setting
(`persistBlendshapeSelectionEnabled`) allows keeping it across sessions.

Two debug mocks (`debugForceArCoreUnavailable`, `debugForceGpuUnavailable` -- see §3) persisted
follow exactly the same pattern as `tierOverride`: read once at launch, a change only applies on the
next restart. The third mock (thermal rate) is deliberately absent from this store -- it's a live
active-session toggle, not a launch-time setting.

## 7. Non-functional constraints

**Portrait-locked** -- the app is locked to portrait orientation, which allows caching the camera
rotation matrix for the whole session (computed once rather than per frame). Known limitation:
starting with Android 16 (API 36), orientation restrictions declared by an app are ignored by
default on screens with a minimum width ≥ 600dp (tablets); Android 17 (API 37) removes the manifest
opt-out entirely -- affects the settings screens' layout on recent tablets. The fixed camera
rotation itself works correctly across all tested physical orientations (portrait, landscape both
ways). A significant change in how the phone is held mid-session requires a new explicit calibration
to keep head pose consistent.

**Battery/heat budget** -- a central concern behind several implementation choices: bitmap pool,
per-tier rate throttling, a power-save mode that cuts the displayed preview without cutting
tracking. The 478-point mesh is extracted every frame whether the overlay is active or not -- deemed
negligible cost, since MediaPipe computes these points internally regardless. Any added feature
(notably the experimental detection cascades) is weighed against this budget, with a user warning if
the device shows signs of throttling.

**Lifecycle** -- sensors (orientation, battery) and tracking initialization are tied to the
Activity's real lifecycle (`ON_START`/`ON_STOP`), not just Compose composition, to avoid needless
listening when the app goes to background without being destroyed.

**Hot/cold Compose state split** -- `MainUiState` (settings, connection, calibration -- changes
rarely) is separated from the `trackingFrame` flow (blendshapes, latency, detection -- changes at
20-60 Hz) to limit Compose recomposition to only the components that actually need per-frame
updates.

## 8. External dependencies and known limitations

**MediaPipe Face Landmarker** -- `.task` model downloaded manually (not versioned, too large),
`LIVE_STREAM` mode, output of 52 ARKit blendshapes. Structural model limitation: `tongueOut` is
never reliably restituted (the tongue doesn't exist in the landmark mesh's topology, which only
models the visible face surface); `cheekPuff` is also unreliable in practice but for a different
reason (the surface deformation is well present in the mesh, a model-coverage gap rather than a
structural impossibility). `tongueOut` has an application-level mitigation built outside the mesh
(geometric gate → color → embedding cascade), sent to the network protocols once calibrated, with an
accepted residual risk of an isolated false positive; `cheekPuff` has no mitigation.

**ARCore** -- used only for Augmented Faces on the `OPTIMAL` tier (see §4), not for a general
augmented-reality use case.

**JavaOSC** -- OSC protocol implementation used for VMC.

**nv-websocket-client** -- WebSocket client used by `VTubeStudioSender` for VTube Studio's Plugin
API, rather than OkHttp: OkHttp unconditionally proposes the `permessage-deflate` extension, with no
public setting to disable it, incompatible with VTube Studio's `websocket-sharp` server.

**kotlinx.serialization** -- pure JSON encoding/decoding for the VTube Studio Plugin API protocol
(`VTubeStudioProtocol.kt`), preferred over `org.json` (already present in the Android SDK) because
the latter is only a stub under this project's JVM test runner (no Robolectric configured) --
would break the "pure, JVM-testable functions" convention followed everywhere else.

**androidx.appcompat** -- required only for `AppCompatActivity`/`AppCompatDelegate`, the in-app
language selector (see §12); the rest of the UI (Compose/Material3) doesn't rely on any classic
support component.

## 9. Tests

Pure JVM unit test suite (`app/src/test/`, no Android/Robolectric dependency),
`./gradlew testDebugUnitTest`. Philosophy: extract into pure, testable functions any computation
that doesn't directly depend on the Android/MediaPipe/CameraX framework -- examples: `RotationMath`
(matrix/quaternion/Euler conversions), `computeEyeGazeDegrees`, `CameraController.rotatedDimensions`,
`ArCoreFaceSelector.pickPrimary`. Function-by-function detail, with explicit reasons for what's
deliberately not covered, in `AndroidMoCap_tests_unitaires.md`.

## 10. Build and distribution

Release signing read from environment variables (never committed), a GitHub Actions workflow
(`.github/workflows/release.yml`) triggered by a `vX.Y.Z` tag: build, MediaPipe model download,
signing, publishing a GitHub Release with the APK attached. No Play Store. `isMinifyEnabled = true`
in the release build (R8 enabled): shrinking + renaming active, `-dontoptimize` deliberately kept
(see `proguard-rules.pro`) -- R8's optimizer disrupts Guava Flogger's caller detection (a transitive
MediaPipe dependency), the source of a launch crash without this option. Without release signing
environment variables, `assembleRelease` falls back to debug signing -- only to allow testing a
minified release build locally (`adb install`), the publishing workflow always has the real
variables.

A second workflow (`.github/workflows/ci.yml`) triggers on every pull request and every push to
`main`: debug build (unsigned, no secret required) + unit tests, to give an objective signal before
any merge decision -- notably on Dependabot PRs.

License: PolyForm Shield 1.0.0 (see `LICENSE`), a CLA in place for external contributions
(`CLA.md`, `CONTRIBUTING.md`).

## 11. Technical debt and known limitations

Known limitations are documented directly in the relevant section rather than in a separate list:
no semi-automatic update check implemented (§10), undefined behavior on large screens/tablets (§7),
no bitmap pool for the ARCore path (§4), FR/EN translations not yet validated by a native speaker
(§12).

## 12. Localization

User-facing text externalized into resources: `res/values/strings.xml` holds English and serves as
the default fallback (any system language without a dedicated folder, including neither French nor
English, falls back to English); `res/values-fr/strings.xml` holds French, used explicitly when the
system language (or the choice made via the per-app selector) is French. `android:localeConfig`
declared in `AndroidManifest.xml` (`res/xml/locales_config.xml`, en/fr order) for the system's
native per-app language selector (system settings, Android 13+ only). On earlier versions, an
in-app selector covers the same need (`DisplaySettingsScreen`, "App language" section):
`MainActivity` extends `AppCompatActivity` (theme `Theme.AppCompat.DayNight.NoActionBar`, required
-- `android:Theme.Material.NoActionBar` alone would prevent
`AppCompatDelegate.setApplicationLocales()` from working under Compose), and the manifest declares
`AppLocalesMetadataHolderService` (`autoStoreLocales="true"`) so the choice persists automatically
across launches on every version (AppCompat's own storage under API 33, delegated to the platform
`LocaleManager` beyond that) -- no DataStore or custom persistence code needed for this project.
Changing the language recreates the Activity (documented AppCompat behavior): a settings screen open
at that moment closes and returns to the main screen, a minor cosmetic effect rather than a bug. The
52 ARKit blendshape names (`jawOpen`, `mouthSmileLeft`...) and technical protocol identifiers
(`ConnectionType.IFACIALMOCAP`...) are deliberately not translated -- protocol vocabulary, not
display text. Error messages emitted outside `@Composable` code (`MainViewModel`,
`CameraController`, `FaceLandmarkerHelper`) are resolved via `Context.getString()`, each of these
classes already having access to a `Context`/`Application`. FR/EN translations not yet validated by
a native speaker.
