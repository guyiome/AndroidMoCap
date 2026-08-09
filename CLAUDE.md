# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

AndroidMoCap turns an Android phone into a standalone facial motion-capture tracker for VTubing:
front camera → MediaPipe Face Landmarker (52 ARKit blendshapes + gaze/head pose) → streamed live
over the local network to Blender/Unity (VMC/OSC, confirmed end-to-end on device — see revue
technique point 38), VBridger (iFacialMocap-compatible UDP protocol), or directly to VTube Studio
via its own Plugin API (WebSocket/JSON, not VMC — confirmed end-to-end on device, see point 39).
No cloud, no third-party app dependency, single-developer personal project.

Read `README.md` first for features, prerequisites and the PC-side connection setup. For anything
beyond a quick orientation, three living docs are the source of truth and take precedence over
assumptions made from reading code alone:

- `AndroidMoCap_spec_fonctionnelle.md` — current user-facing scope (what the app does today).
- `AndroidMoCap_spec_technique.md` — architecture, capture pipeline, network protocols, non-functional
  constraints.
- `AndroidMoCap_revue_technique.md` — chronological decision log + backlog with full reasoning
  (alternatives considered, open questions), including a numbered index of accepted-but-unimplemented
  decisions. **Read this before starting any non-trivial work**: it documents in-flight design threads
  (e.g. ARCore fusion, experimental tongue/cheek detection) and a coordination protocol with a
  separate non-code "reflection" conversation that also edits these files — update the relevant
  "Statut" entries here (and mirror into the specs) after implementing anything discussed there.
- `AndroidMoCap_tests_unitaires.md` — file-by-file test coverage map, with explicit reasons for what's
  intentionally *not* unit-tested rather than silence.

## Commands

Requires a physical Android device for anything touching the camera/tracking pipeline — the emulator
provides no usable camera feed. Pure-logic changes (see Architecture below) can be verified without a
device via the JVM unit test suite.

```bash
# Unit tests (JVM-only, no device/emulator needed)
./gradlew testDebugUnitTest

# Run a single test class
./gradlew testDebugUnitTest --tests "com.guyiome.androidmocap.ui.LandmarkProjectionTest"

# Debug build (unsigned)
./gradlew assembleDebug

# Release build — minified (R8, see point 8). Signed with the real release key if the env vars
# below are set, otherwise falls back to the debug key so the minified APK can still be installed
# and tested locally (adb install) — the real publish workflow always has the env vars.
./gradlew assembleRelease
```

Before building, the MediaPipe model must be downloaded manually (too large to version — see
README "Compiler depuis les sources"):

```bash
mkdir -p app/src/main/assets
curl -fL -o app/src/main/assets/face_landmarker.task \
  https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task
```

Release signing is via environment variables only, never committed files: `RELEASE_KEYSTORE_PATH`,
`RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. Absent in normal dev
(`assembleDebug`/`installDebug`) — no error, `assembleRelease` just falls back to the debug signing
key (see build.gradle.kts) instead of failing. CI (`.github/workflows/`) mirrors this: `ci.yml` runs
`testDebugUnitTest assembleDebug` on every PR/push to `main` (no secrets, safe on external/Dependabot
PRs); `release.yml` only builds+signs+publishes a GitHub Release on a `vX.Y.Z` tag push, reconstituting
the keystore from a base64 GitHub secret — always has the real env vars, never uses the local fallback.

## Architecture

**Adaptive tracking tiers.** `capabilities/DeviceCapabilities.kt` (`DeviceCapabilityDetector.detect()`)
inspects ARCore support, Android performance class, CPU cores and RAM once at startup, with no manual
configuration by default. `tracking/TrackingTier.kt` (`TrackingTierSelector.select()`, pure function)
maps that to one of `COMPATIBLE` / `STANDARD` / `OPTIMAL`, each fixing a `TierConfig` (GPU delegate
preference, target FPS, whether ARCore head pose fusion is used) — takes an optional `override`
parameter that bypasses the automatic decision entirely, wired to a diagnostics-only manual tier
picker (`DiagnosticsScreen`, persisted via `AppSettingsStore.tierOverride`, applied on next app
launch, not a live pipeline rebuild) added so devices that never naturally qualify for a given tier
can still be used to test it. `OPTIMAL`'s ARCore fusion is merged into `main` and confirmed working
on device (revue technique point 3/13) — camera source switching (CameraX ↔ ARCore), rotation
correction and the image-processing thread split are all implemented; a few minor items (a native
MediaPipe warning of unconfirmed cause, no Bitmap pooling for the ARCore path) remain open, see the
revue technique. `isThermalThrottling()` is polled continuously during capture
(`MainViewModel.startThermalPolling()`, every 5s, tied to `ON_START`/`ON_STOP`) and halves the
target FPS while throttling (`tracking/ThermalThrottle.kt`, pure and tested), ramping back up once
it clears — confirmed working end-to-end on device via the debug mock panel (see below), the real
`PowerManager` thermal sensor itself hasn't fired yet on the test device (revue technique point 34).
A calibration-anomaly detector (`tracking/CalibrationAnomaly.kt` + `BlendshapeStability.kt`, pure
and tested) tints the existing calibrate button red when the head pose seems to have drifted since
the last calibration — sticky until the next explicit calibration, never automatic — confirmed
working on device across all three tiers (revue technique point 19). A hidden debug-mock panel in
`DiagnosticsScreen` (7-tap unlock on a version-number row, see `ui/DebugPanelUnlock.kt`) lets these
hard-to-trigger-naturally paths (thermal throttling, ARCore unavailable, GPU delegate unavailable)
be forced for testing — confirmed working on device (revue technique point 35).

**Capture pipeline.** `camera/CameraController.kt` drives CameraX (front camera → `MPImage`), with a
bitmap pool (`acquirePooledBitmap`) to avoid a per-frame allocation, frame-rate throttling against
`TierConfig.targetFps` applied *before* handing frames to MediaPipe, and `rotatedDimensions()` (pure,
tested) handling the 90°/270° width/height swap. `tracking/FaceLandmarkerHelper.kt` wraps
`FaceLandmarker`, attempting GPU delegate first per `TierConfig.preferGpuDelegate` and falling back to
CPU on init failure; its `onLiveStreamResult()` callback builds `FaceTrackingResult` — blendshapes,
head rotation matrix/euler (via `tracking/RotationMath.kt`), per-eye gaze angles (reconstructed from
directional blendshapes, MediaPipe doesn't provide gaze natively — `computeEyeGazeDegrees()`, pure,
tested), the 478-point face mesh (extracted unconditionally since point 28 — previously gated behind
the mesh overlay setting, now also consumed by the eyeBlink correction below, and MediaPipe computes
these points internally regardless of whether the app reads them), and the analyzed image's own pixel
dimensions (needed by the projection math below).

**Eye blink reliability.** `tracking/EyeAspectRatio.kt` computes a geometric Eye Aspect Ratio (EAR)
per eye from the landmark mesh (`EyeLandmarkIndices.LEFT_EYE`/`RIGHT_EYE`, confirmed against real
`eyeBlinkLeft`/`eyeBlinkRight` device tests — see revue technique point 28/45, don't trust community
tutorials' left/right convention blindly, it's inconsistent across sources). MediaPipe's own
`eyeBlink` blendshape leaks between eyes (a deliberate wink on one side still raises the other eye's
score) — `tracking/EyeBlinkCorrection.kt` (`correctEyeBlinkScores()`, called from
`MainViewModel.handleTrackingResult()` before any sender/display consumes the blendshapes) damps a
blendshape score when its EAR says the eye is still clearly open, confirmed on device to suppress
cross-eye leakage without touching genuine closures. The EAR openness signal is smoothed by
`tracking/OneEuroFilter.kt` (Casiez, Roussel & Vogel, CHI 2012 — adaptive-cutoff low-pass, general
reusable utility, see revue technique point 46 for why it was picked over Kalman/Savitzky-Golay/a
fixed EMA) before being used to damp: without smoothing, a sustained held-shut eye collapsed back to
"open" within ~9s because MediaPipe's own landmark tracking drifts during a held pose; confirmed on
device that the filter fixes this (a 9s hold now tracks the raw score with no visible decay).
Diagnostic logging (`MainViewModel.EAR_DIAGNOSTIC_LOGGING`, tag `EarDiag`) is off by default, flip it
back on to debug a
future blink issue rather than re-deriving this from scratch.

**Hot vs. cold Compose state.** `ui/MainViewModel.kt` deliberately splits state into two flows:
`MainUiState` (settings/connection/calibration — changes at human interaction speed) and
`TrackingFrame` (blendshapes/pose/mesh — changes at 20-60Hz). Composables that don't need per-frame
data collect only `MainUiState`, avoiding needless recomposition. `initializeTracking()` is
idempotent (`trackingInitialized` guard) since it's called from a lifecycle-bound entry point that can
fire more than once.

**Mesh overlay projection.** `ui/LandmarkProjection.kt` (pure, tested) maps MediaPipe's normalized
`[0,1]` mesh coordinates to screen pixels, reproducing `PreviewView`'s default centered "FILL_CENTER"
crop using the analyzed image's actual dimensions (falls back to naive per-axis stretch if dimensions
are still unknown, e.g. before the first frame). This must stay in sync with whatever
`FaceLandmarkerHelper` reports as `imageWidthPx`/`imageHeightPx` — get it wrong and the mesh
overlay silently drifts from the face on any device whose camera aspect ratio differs from its screen
(see revue technique point 27 for the device bug this fixed).

**Network senders.** Three mutually-exclusive senders, all driven off the same `FaceTrackingResult`:
`network/VmcOscSender.kt` batches an entire frame's blendshapes into a single `OSCBundle`
(`buildBundle()`, pure, tested — one UDP packet per frame, not per blendshape) for Blender/Unity —
**not VTube Studio**, which doesn't receive VMC/OSC as input (see revue technique point 39);
confirmed end-to-end on device including packet content, not just connectivity (point 38).
`network/IFacialMocapSender.kt` implements the iFacialMocap wire protocol for VBridger
(`buildMessage()`, pure, tested), listening passively for the PC software to connect in rather than
dialing out. `network/VTubeStudioSender.kt` talks directly to VTube Studio's own Plugin API
(WebSocket/JSON, port 8001 by default) instead of VMC — protocol encoding (`VTubeStudioProtocol.kt`,
`encodeDefaults = true` is load-bearing, see its kdoc) and the connection state machine
(`VTubeStudioConnectionState.kt`) are pure and tested; the socket itself isn't, confirmed
end-to-end on device instead (point 39). WebSocket client is **nv-websocket-client, not OkHttp**:
OkHttp proposes the `permessage-deflate` extension unconditionally with no public way to disable
it, incompatible with VTube Studio's `websocket-sharp` server (see point 39 for the full diagnosis
— don't reintroduce OkHttp here without re-reading it). A stored auth token that gets rejected
triggers one automatic re-request before giving up; a stale-parameter-name collision with another
plugin (e.g. VBridger, same ARKit names) no longer aborts the whole connection, just that one
parameter. UI-facing naming for iFacialMocap was deliberately decoupled from its internal naming
(chip reads "UDP / VBridger", not "iFacialMocap") to avoid implying a connection to the third-party
app — don't propagate that rename into the protocol-level identifiers (`ConnectionType.IFACIALMOCAP`,
class name, handshake constants), which are intentionally left as-is.

**Settings persistence.** `settings/AppSettingsStore.kt` and `settings/ConnectionSettingsStore.kt`
wrap Jetpack DataStore Preferences. Blendshape selection persistence across sessions is opt-in
(`persistBlendshapeSelectionEnabled`, default off — historic reset-on-launch behavior preserved
unless the user turns it on).

**UI navigation.** `SettingsScreen.kt` is a 4-category menu (`DiagnosticsScreen`,
`ConnectionSettingsScreen`, `DisplaySettingsScreen`, `ExperimentalFeaturesScreen`, the last currently a
placeholder) plus `BlendshapeSelectionScreen`, all overlay screens closable via a standard back arrow,
the hardware back button, or the predictive-back gesture (`BackHandler`) — all three trigger the same
`onClose`.

**In-app language selector** (point 30, `DisplaySettingsScreen` — "Langue de l'app"), confirmed
working on device including persistence across a full restart. Requires `MainActivity` to extend
`AppCompatActivity` (not `ComponentActivity`) with a `Theme.AppCompat.*` theme — both load-bearing
for `AppCompatDelegate.setApplicationLocales()` under Compose, don't revert either without re-reading
point 30/43. `AndroidManifest.xml`'s `AppLocalesMetadataHolderService` (`autoStoreLocales="true"`)
makes the choice persist automatically on every Android version, no custom DataStore code needed —
this is the in-app counterpart to the system per-app language picker (`android:localeConfig`),
which only exists on Android 13+. Calling `setApplicationLocales()` recreates the Activity (documented
AppCompat behavior): a settings screen open at that moment closes back to the main screen, a known
cosmetic quirk, not a bug.

## Conventions specific to this repo

- **One commit per feature/fix** when a request touches more than one thing, unless the two are
  tightly coupled (one breaks without the other) or it's an explicit global refactor (single
  "refactor" commit is fine then). Concise title, details in the commit body.
- **Dependency/Dependabot PRs: never use GitHub's "Merge" button.** Read the PR (diff, changelog),
  reproduce the same change as a local commit in this repo, test what's testable locally, let it be
  pushed normally, then close/mark the PR as handled. A server-side GitHub merge creates a commit this
  local clone doesn't have, which has already caused a real local/`origin` divergence once (see revue
  technique, PR #6) — local is meant to stay the single source of truth.
- Update `AndroidMoCap_revue_technique.md` (status of the relevant point, or a new numbered section)
  and mirror into the specs whenever you land something discussed there — this is the only sync
  mechanism with the parallel non-code "reflection" conversation that also edits these files.
