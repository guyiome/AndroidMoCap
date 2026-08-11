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
A tag containing `-beta` (e.g. `v0.3.0-beta.1`) publishes as a GitHub prerelease instead — same build,
just flagged so update-tracking tools (Obtainium etc.) skip it by default — see revue technique point 49.
The repo is currently **private**, which blocks the in-app update checker (point 14 — GitHub Releases
API needs auth for a private repo, and an embedded token is a rejected approach, decompilable) —
that point stays parked until the repo goes public, don't build a throwaway workaround for it.

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

**Brow raise geometry.** `tracking/BrowRaise.kt` (`browRaiseRatio()`, same shape as EAR below: two
landmark distances normalized by eye width) — a first cut at applying the same principle to
`browOuterUpLeft/Right`/`browDownLeft/Right`, prompted by comparing against NVIDIA Broadcast's
face-tracking pipeline (which does the same landmark-derived-expression approach, just with more
targeted training). `BrowLandmarkIndices` L/R mapping is a **hypothesis, not yet device-confirmed**
— inferred by analogy with the already-confirmed eye L/R inversion from the same source blog, not
measured directly. A temporary diagnostic (`MainViewModel.BROW_DIAGNOSTIC_LOGGING`, tag `BrowDiag`)
is in place to verify it before wiring any actual correction — don't treat the mapping as trustworthy
until that's done.

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
The "closed" EAR reference (`EAR_CLOSED_REFERENCE`) is a fixed constant measured face-on, which
turned out wrong at other camera angles — at a shallow upward angle (phone below the screen), a
genuine closure's EAR never dropped as low as that constant assumed, so the leak-suppression damping
was crushing real closures too (confirmed on device, ~0.55 raw corrected down to ~0.03-0.07). Fixed
by `AdaptiveEarFloor` (same file): tracks the closed reference per eye dynamically, gated on the raw
blendshape score crossing an activity threshold (an independent signal from EAR) so it only samples
during a plausible blink episode, taking that episode's EAR minimum (not the last frame's, which
protects against the same landmark-drift issue as above) and blending it in gradually across
episodes rather than jumping to the latest one. Confirmed on device: a problem angle's right-eye
score went from crushed to passing through within a handful of repeated blinks, without leaking to
the other eye — see revue technique point 48.
Diagnostic logging (`MainViewModel.EAR_DIAGNOSTIC_LOGGING`, tag `EarDiag`) is off by default, flip it
back on to debug a
future blink issue rather than re-deriving this from scratch.

**Hot vs. cold Compose state.** `ui/MainViewModel.kt` deliberately splits state into two flows:
`MainUiState` (settings/connection/calibration — changes at human interaction speed) and
`TrackingFrame` (blendshapes/pose/mesh — changes at 20-60Hz). Composables that don't need per-frame
data collect only `MainUiState`, avoiding needless recomposition. `initializeTracking()` is
idempotent (`trackingInitialized` guard) since it's called from a lifecycle-bound entry point that can
fire more than once.

**Logging.** `logging/AppLog.kt` is the single entry point — replaces direct `android.util.Log`
calls everywhere else in the app (revue technique point 50). `v`/`d` are no-ops outside
`BuildConfig.DEBUG` (neither logcat nor the file — explicit code gate, not an R8
`-assumenosideeffects` rule, whose effect would be uncertain given `-dontoptimize`, point 8).
`i`/`w`/`e` always reach logcat, and reach a rotating file (`filesDir/logs/app_logs.txt`, ~1.5MB cap,
`logging/LogFormatting.kt`, pure/tested) if `AppSettingsStore.logLevel` allows it (ERROR by default,
selectable up to WARN/INFO — VERBOSE/DEBUG are deliberately not selectable, matching Android's own
convention that they shouldn't survive into release). IPs in the message are masked
(`maskIpAddresses()`, last two octets → `x`) outside debug builds before the line is written — the
one piece of user-identifiable-ish data these logs realistically contain (no face-tracking values
above DEBUG, ever — keep it that way for any future log call). The file is shareable via
`LoggingSettingsScreen` (`MainViewModel.buildShareLogsIntent()`, `FileProvider` scoped to
`filesDir/logs/` only, see `res/xml/file_paths.xml`) — deliberately **not** hidden behind
`DiagnosticsScreen`'s debug unlock, since any user (not just the developer) should be able to report
an issue with it. **Startup-ordering gotcha, confirmed on device and fixed**: don't rely on the
reactive `appSettingsStore.logLevel` collector in `MainViewModel.init{}` alone for anything logged
very early — it has no guaranteed ordering against other startup work, and an early log can lose
that race and get silently dropped under the still-default `ERROR` level. `initializeTracking()`
eagerly awaits `appSettingsStore.logLevel.first()` and calls `AppLog.setMinimumPersistedLevel()`
as its very first line specifically to close this gap for every startup log that follows (tier
selection, ARCore/CameraX bind) — add new early-startup logs after that point, not before.

**Mesh overlay projection.** `ui/LandmarkProjection.kt` (pure, tested) maps MediaPipe's normalized
`[0,1]` mesh coordinates to screen pixels, reproducing `PreviewView`'s default centered "FILL_CENTER"
crop using the analyzed image's actual dimensions (falls back to naive per-axis stretch if dimensions
are still unknown, e.g. before the first frame). This must stay in sync with whatever
`FaceLandmarkerHelper` reports as `imageWidthPx`/`imageHeightPx` — get it wrong and the mesh
overlay silently drifts from the face on any device whose camera aspect ratio differs from its screen
(see revue technique point 27 for the device bug this fixed).

**Mirror mode** (revue technique point 51, `tracking/Mirroring.kt` + `RotationMath.mirrorEulerDegrees`,
`AppSettingsStore.mirrorModeEnabled`, on by default). `RotationMath.toEulerDegrees` used to negate head
yaw unconditionally — an old, undocumented "worked on device" fix that, in hindsight, silently baked in
mirrored head behavior, while every left/right blendshape (`eyeBlinkLeft/Right`, gaze, brows, mouth...)
kept shipping anatomically — confirmed on device (10 Aug 2026) as an incoherence: turn your head to show
one side + wink that side's eye, and the two disagreed on VBridger. Fixed at the root: `toEulerDegrees`
is raw/native now (no hidden flip); `mirrorEulerDegrees()` (negates yaw+roll, preserves pitch — the
actual geometry of a left-right reflection, roll was never mirrored even under the old behavior) and
`mirrorFaceTrackingResult()` (blendshapes + head + **both eye gaze arrays, swapped between L/R slots
AND mirrored individually**) apply together, in one place, at the very end of
`MainViewModel.handleTrackingResult()`, after EAR correction (which stays anatomy-based, untouched by
this setting). Don't ever mirror the head alone again — same bug, just moved. The on-screen
preview/mesh mirroring (`FaceMeshOverlay`, always on, CameraX's own selfie-view convention) is
deliberately **not** wired to this setting — different concern (the user's own self-monitoring comfort
vs. what the avatar receives), conflating them was considered and rejected.

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

**Tongue-out detection cascade** (revue technique point 15, phase 1 confirmed on device 11 Aug 2026).
MediaPipe's face mesh can't see inside the mouth, so `tongueOut` is always 0 — a 3-stage cascade
reads the camera pixels directly instead. Stage 1 (`tracking/TongueOutGate.kt`, `jawOpenGateOpen()`)
gates on the existing `jawOpen` blendshape, confirmed reliable on both CameraX and ARCore. Stage 2
(`tracking/LipLandmarks.kt` for the mouth crop region, `tracking/MouthColorAnalysis.kt` for the
color heuristic) reads real pixels via `CameraController.peekPooledBitmap()` (CameraX) or
`ArCoreHeadPoseTracker.peekLastBitmap()` (ARCore, added this session — ARCore allocates a fresh,
unpooled `Bitmap` per frame already, so this just retains the last one an extra beat rather than
building a real pool). **`LipLandmarkIndices` mapping is now device-confirmed** (raw coordinates
logged and compared — the initial hypothesis had corners/lip-center swapped, fixed). Stage 2's own
color classifier, however, is **not reliable** even with an adaptive baseline
(`tracking/TongueColorBaseline.kt`, same `next()`/gated-update shape as `AdaptiveEarFloor`) — a
fixed threshold drifts too much session-to-session, and on the CameraX tier the raw ratio barely
separates tongue-out from mouth-open-only at all. This isn't a tuning gap, it's confirmation that
color alone can't carry the decision — stage 3 (embedding classification + personal calibration,
not yet built) is genuinely required, exactly as the original design assumed; stage 2 only exists to
filter obviously-negative frames before it. `tongueOut` is therefore never injected in phase 1 —
`TONGUE_DIAGNOSTIC_LOGGING` (off by default) is the only way to see any of this cascade's output.
Separately, while debugging stage 2's crop, a full-frame debug image (`saveTongueDebugCrop()`)
revealed the ARCore camera bitmap is rotated ~90° counter-clockwise from reality — confirmed by the
user looking at the image, not just inferred — a pre-existing bug in
`ArCoreHeadPoseTracker`'s rotation formula (already flagged in its own kdoc as "never visually
verified"), independent of point 15 and potentially affecting OPTIMAL-tier tracking accuracy more
broadly. Not yet fixed — noted in the private backlog.

**Settings persistence.** `settings/AppSettingsStore.kt` and `settings/ConnectionSettingsStore.kt`
wrap Jetpack DataStore Preferences. Blendshape selection persistence across sessions is opt-in
(`persistBlendshapeSelectionEnabled`, default off — historic reset-on-launch behavior preserved
unless the user turns it on).

**UI navigation.** `SettingsScreen.kt` is a 5-category menu (`DiagnosticsScreen`,
`ConnectionSettingsScreen`, `DisplaySettingsScreen`, `ExperimentalFeaturesScreen` — a toggle for the
tongue-out detection diagnostic (point 15) plus a placeholder for cheek-puff detection (point 16),
`LoggingSettingsScreen`) plus `BlendshapeSelectionScreen`, all overlay screens closable via a
standard back arrow, the hardware back button, or the predictive-back gesture (`BackHandler`) — all
three trigger the same `onClose`.

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
