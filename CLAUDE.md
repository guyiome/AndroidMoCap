# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

AndroidMoCap turns an Android phone into a standalone facial motion-capture tracker for VTubing:
front camera → MediaPipe Face Landmarker (52 ARKit blendshapes + gaze/head pose) → streamed live
over the local network to VTube Studio/Blender/Unity (VMC/OSC) or VBridger (iFacialMocap-compatible
UDP protocol). No cloud, no third-party app dependency, single-developer personal project.

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

# Release build — only meaningful with signing env vars set (see below); otherwise produces an
# unsigned release APK
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
(`assembleDebug`/`installDebug`) — no error, just an unsigned build. CI (`.github/workflows/`) mirrors
this: `ci.yml` runs `testDebugUnitTest assembleDebug` on every PR/push to `main` (no secrets, safe on
external/Dependabot PRs); `release.yml` only builds+signs+publishes a GitHub Release on a `vX.Y.Z` tag
push, reconstituting the keystore from a base64 GitHub secret.

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
revue technique. `isThermalThrottling()` is now polled continuously during capture
(`MainViewModel.startThermalPolling()`, every 5s, tied to `ON_START`/`ON_STOP`) and halves the
target FPS while throttling (`tracking/ThermalThrottle.kt`, pure and tested), ramping back up once
it clears — not yet confirmed on device, see revue technique point 34.

**Capture pipeline.** `camera/CameraController.kt` drives CameraX (front camera → `MPImage`), with a
bitmap pool (`acquirePooledBitmap`) to avoid a per-frame allocation, frame-rate throttling against
`TierConfig.targetFps` applied *before* handing frames to MediaPipe, and `rotatedDimensions()` (pure,
tested) handling the 90°/270° width/height swap. `tracking/FaceLandmarkerHelper.kt` wraps
`FaceLandmarker`, attempting GPU delegate first per `TierConfig.preferGpuDelegate` and falling back to
CPU on init failure; its `onLiveStreamResult()` callback builds `FaceTrackingResult` — blendshapes,
head rotation matrix/euler (via `tracking/RotationMath.kt`), per-eye gaze angles (reconstructed from
directional blendshapes, MediaPipe doesn't provide gaze natively — `computeEyeGazeDegrees()`, pure,
tested), the 478-point face mesh (only extracted when an overlay consumer needs it — see
`setLandmarksNeeded`), and the analyzed image's own pixel dimensions (needed by the projection math
below).

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

**Network senders.** Two mutually-exclusive, protocol-level-independent senders, both driven off the
same `FaceTrackingResult`: `network/VmcOscSender.kt` batches an entire frame's blendshapes into a
single `OSCBundle` (`buildBundle()`, pure, tested — one UDP packet per frame, not per blendshape) for
VTube Studio/Blender/Unity; `network/IFacialMocapSender.kt` implements the iFacialMocap wire protocol
for VBridger (`buildMessage()`, pure, tested), listening passively for the PC software to connect in
rather than dialing out. UI-facing naming was deliberately decoupled from this internal naming (chip
reads "UDP / VBridger", not "iFacialMocap") to avoid implying a connection to the third-party app —
don't propagate that rename into the protocol-level identifiers (`ConnectionType.IFACIALMOCAP`, class
name, handshake constants), which are intentionally left as-is.

**Settings persistence.** `settings/AppSettingsStore.kt` and `settings/ConnectionSettingsStore.kt`
wrap Jetpack DataStore Preferences. Blendshape selection persistence across sessions is opt-in
(`persistBlendshapeSelectionEnabled`, default off — historic reset-on-launch behavior preserved
unless the user turns it on).

**UI navigation.** `SettingsScreen.kt` is a 4-category menu (`DiagnosticsScreen`,
`ConnectionSettingsScreen`, `DisplaySettingsScreen`, `ExperimentalFeaturesScreen`, the last currently a
placeholder) plus `BlendshapeSelectionScreen`, all overlay screens closable via a standard back arrow,
the hardware back button, or the predictive-back gesture (`BackHandler`) — all three trigger the same
`onClose`.

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
