# AndroidMoCap — Unit test tracking

*🇫🇷 Version française : [AndroidMoCap_tests_unitaires_FR.md](AndroidMoCap_tests_unitaires_FR.md)*

Living document, updated with every change (TDD workflow: test before code). Lists, module by
module, what's covered by pure JVM unit tests (`app/src/test/`, run via
`./gradlew testDebugUnitTest`, no device/emulator required), and why the rest isn't yet.

Two broad categories recur throughout. **Pure logic** code (math, tier selection, message
formatting, static catalog...) doesn't depend on any Android class and is tested directly in the
JVM -- this is the core of this first pass, and the priority for any new function of this kind.
**Android-framework-bound** code (sensors, camera, native MediaPipe, DataStore, Compose) needs a
device/emulator (instrumented test) or Robolectric to simulate the Android environment -- not
covered for now, the precise reason is given each time rather than a plain "not tested".

## tracking/

| File | Covered element | Test(s) | Status |
|---|---|---|---|
| `RotationMath.kt` | `multiply`, `transpose`, `rotation3x3FromColumnMajor4x4`, `toEulerDegrees`, `mirrorEulerDegrees`, `composeCalibratedEuler`, `rotation3x3FromQuaternion` | `RotationMathTest.kt` | ✅ Covered (15 tests) -- the app's trickiest class (source of an axis-inversion issue encountered previously), top priority. `rotation3x3FromQuaternion` is the building block for ARCore fusion: converts ARCore's `Pose#getRotationQuaternion()` quaternion to the same 3x3 format as the rest of the pipeline. `toEulerDegrees` no longer mirrors yaw by default (native/anatomical behavior) -- `mirrorEulerDegrees` now carries mirror mode explicitly, tested in isolation. |
| `TrackingTier.kt` | `TrackingTierSelector.select()` (including the `override` parameter) | `TrackingTierSelectorTest.kt` | ✅ Covered (pure `DeviceCapabilities -> TierConfig` function; a non-null `override` short-circuits automatic selection, a null `override` behaves like its absence -- manual tier forcing from Diagnostics). |
| `BlendshapeCatalog.kt` | `all`, `byCategory`, `unreliable` | `BlendshapeCatalogTest.kt` | ✅ Covered (structural consistency: 52 entries, no duplicates, category/full-list overlap, `unreliable` names all present in `all`). |
| `FaceLandmarkerHelper.kt` | `computeEyeGazeDegrees()` (extracted `internal`, pure) | `FaceLandmarkerHelperTest.kt` | ✅ Covered. |
| `FaceLandmarkerHelper.kt` | `setup()`, `tryCreateLandmarker()`, `detectAsync()`, `onLiveStreamResult()` (including extracting `faceLandmarks()`) | -- | ❌ Not testable in pure JUnit: wraps the native MediaPipe engine (`FaceLandmarker.createFromOptions`, real GPU/CPU delegate) -- needs a device/emulator. `FaceLandmarkerResult` also can't be constructed by hand in a test (its `create()` factory is package-private on the MediaPipe side). An instrumented test is worth considering if a bug shows up here. |
| `FaceTrackingResult.kt` | Data class (`FaceTrackingResult`, `BlendshapeScore`) | -- | ➖ No logic of its own, serves as a fixture for other tests (see `IFacialMocapSenderTest`, `FaceLandmarkerHelperTest`). |
| `ThermalThrottle.kt` | `ThermalThrottleState.next()` | `ThermalThrottleTest.kt` | ✅ Covered (pure function, no real-timing dependency -- rate reduction/ramp-up, floor, consecutive-poll threshold for `downgradeSuggested`, sticky behavior, short bursts that don't trigger the suggestion). |
| `CalibrationAnomaly.kt` | `CalibrationAnomalyState.next()`, `maxAbsEulerDegrees()` | `CalibrationAnomalyTest.kt` | ✅ Covered (pure function, no real-timing dependency -- sticky behavior, both signals (rest+drift, face redetection), rest/pose gating, short bursts that don't trigger the anomaly, active expression vs. genuine stillness, redetection independent of rest). |
| `BlendshapeStability.kt` | `meanAbsoluteBlendshapeDelta()` | `BlendshapeStabilityTest.kt` | ✅ Covered (pure function -- sentinel value when comparison is impossible, correct delta, pairing by name rather than position, robust to a different order between the two lists). |
| `EyeAspectRatio.kt` | `eyeAspectRatio()`, `eyeAspectRatioFromLandmarks()` | `EyeAspectRatioTest.kt` | ✅ Covered (pure function -- open eye within the expected range, closed eye near zero, division-by-zero guard for coincident corners, reading the right indices from a landmark list, too-short list -> 0). Used by `EyeBlinkCorrection.kt`. |
| `EyeBlinkCorrection.kt` | `earOpenness()`, `correctBlinkScore()`, `correctEyeBlinkScores()` | `EyeBlinkCorrectionTest.kt` | ✅ Covered (10 tests -- damping a left/right leak vs. an unchanged pass-through of a genuine closure, end-to-end orchestration including a hold under realistic drift, and a real blink at an unusual camera angle that improves after a few episodes). Corrects the `eyeBlinkLeft/Right` blendshape before any network sending. Temporal smoothing (`EyeOpennessSmoother`, removed) is now `OneEuroFilter.kt` below. The "closed" reference is self-adaptive (`AdaptiveEarFloor`, next row). |
| `EyeBlinkCorrection.kt` | `AdaptiveEarFloor.next()` -- self-adaptive per-eye "closed" EAR reference | `AdaptiveEarFloorTest.kt` | ✅ Covered (6 tests -- initial value, no change below the activity threshold with no episode in progress, minimum retained across the whole episode even if EAR rises mid-way, partial blending at the end of the episode, convergence across several episodes toward the true minimum, an isolated polluted episode that doesn't redefine the reference on its own). Corrects a real wink crushed at a low-angle camera position. |
| `BrowRaise.kt` | `browRaiseRatio()`, `browRaiseRatioFromLandmarks()` -- same principle as EAR applied to eyebrows | `BrowRaiseTest.kt` | ✅ Covered (5 tests -- raised brow > lowered brow, computation on known geometry, coincident eye corners -> zero, reading the right indices, too-short list -> zero). Left/right index correspondence ([BrowLandmarkIndices]) inferred by analogy, **not yet device-confirmed** -- a temporary diagnostic is in place (`BrowDiag`). |
| `Mirroring.kt` | `mirrorBlendshapeName()`, `mirrorBlendshapes()`, `mirrorFaceTrackingResult()` -- mirror mode (head + left/right blendshapes + per-eye gaze) | `MirroringTest.kt` | ✅ Covered (8 tests -- name swapping, unpaired blendshapes/`FaceTrackingResult` left unchanged, systematic coverage of the real catalog, involution). Fixes a real, device-confirmed inconsistency (mirrored head, non-mirrored blendshapes). |
| `OneEuroFilter.kt` | Adaptive-cutoff low-pass filter (`minCutoff`, `beta`, `dCutoff`) | `OneEuroFilterTest.kt` | ✅ Covered (8 tests -- first sample unsmoothed, stable constant signal, damped isolated jump, high `beta` reduces lag on a sustained movement, low `minCutoff` smooths a noisy signal more, convergence on a held target). Integrated into `EyeBlinkCorrection.kt` and device-confirmed. |
| `TongueOutGate.kt` | `jawOpenGateOpen()`, `mouthGeometricGateOpen()` -- stage 1 of the tongue-out cascade | `TongueOutGateTest.kt` | ✅ Covered (9 tests -- under/at/above the threshold, 0f/1f bounds, for both gates). `jawOpenGateOpen()` confirmed reliable on device (CameraX + ARCore). `mouthGeometricGateOpen()` was later removed from the hard gate: a genuine "tongue out" hold can produce the same geometric signature as a pressed mouth (near-total overlap) -- the function is kept pure/tested/logged for diagnostics, but only `jawOpenGateOpen()` still blocks stage 1; stage 3 proved able to resolve the "pressed mouth" case on its own on device. |
| `LipLandmarks.kt` | `mouthOpennessRatio()`, `mouthCropRegion()` | `LipLandmarksTest.kt` | ✅ Covered (9 tests -- known geometry, coincident corners -> null/0, too-short list, invalid image dimensions, bounds clamping, `MIN_CROP_DIMENSION_PX` degenerate-case guard). `LipLandmarkIndices` **device-confirmed** (raw coordinates logged and compared -- the initial mapping was inverted, fixed). `MIN_CROP_DIMENSION_PX` (degenerate-case guard) stays in place but is no longer the main line of defense against the "pressed mouth" false positive -- see `mouthGeometricGateOpen()` above. |
| `MouthColorAnalysis.kt` | `rgbToHsv()`, `isTongueColoredPixel()`, `tonguePixelRatio()`, `colorGateOpen()` -- stage 2 | `MouthColorAnalysisTest.kt` | ✅ Covered (18 tests, hand-derived HSV values). Mechanically confirmed functional on device (CameraX + ARCore) but the color classifier itself is **not reliable** in practice (the threshold drifts too much session to session) -- see `TongueColorBaseline.kt` below. **Removed from stage 3's hard filter**: confirmed on device as the dominant bottleneck once stage 1 was relaxed (63% of frames blocked before stage 3) -- functions kept pure/tested/logged, but `colorGateOpen()`/`adaptiveFired` no longer gate stage 3's triggering. |
| `TongueColorBaseline.kt` | `TongueColorBaseline.next()`, `isElevated()` -- adaptive "no tongue" reference (same principle as `AdaptiveEarFloor`) | `TongueColorBaselineTest.kt` | ✅ Covered (6 tests -- first sample, EMA drift in the normal zone, no drift in the elevated zone, `isElevated` below/above the margin). Improves discrimination over a fixed threshold without making it reliable. Same as `MouthColorAnalysis.kt` above: no longer used as a blocking filter, diagnostics only. |
| `InferenceLoadMonitor.kt` | `InferenceLoadState.next()` (EMA), `isRunningHigh()` | `InferenceLoadMonitorTest.kt` | ✅ Covered (6 tests -- raw first sample, EMA convergence, `isRunningHigh` below/above the margin, false before the first sample). Feeds the tongue-out detection load warning. |
| `TongueEmbeddingClassifier.kt` | `cosineSimilarity()`, `classifyTongueState()` -- stage 3 | `TongueEmbeddingClassifierTest.kt` | ✅ Covered (9 tests -- identical/orthogonal/opposite vectors, zero vector without NaN, classification picks the nearest reference, `UNDECIDED` if the reference is empty or the margin isn't met). Device-confirmed: a "tongue in + jaw movement" overlap causing false `TONGUE_OUT` was substantially reduced by widening the calibration (`TongueCalibrationRecordingState.kt`), the remaining residual filtered by the injection debounce (`TongueOutInjectionGate.kt`) -- network injection enabled. |
| `TongueEmbeddingHelper.kt` | `setup()`, `embed()` (GPU/CPU, `RunningMode.IMAGE`) | -- | ❌ Not testable in pure JUnit: wraps MediaPipe's native `ImageEmbedder`, same reason as `FaceLandmarkerHelper.setup()` above. |
| `TongueCalibrationAveraging.kt` | `EmbeddingAccumulator.accumulate()`, `average()` | `TongueCalibrationAveragingTest.kt` | ✅ Covered (5 tests -- single sample, hand-derived per-dimension average, zero samples -> null, inconsistent size -> exception). |
| `TongueCalibrationRecordingState.kt` | `tick()`, `result()` -- calibration state machine (`PREPARE_TONGUE_OUT` → `RECORDING_TONGUE_OUT` → `PREPARE_TONGUE_IN` → `RECORDING_TONGUE_IN` → `DONE`) | `TongueCalibrationRecordingStateTest.kt` | ✅ Covered (20 tests -- start, phase-isolated accumulation, both `PREPARE_*` phases never accumulate, transitions at the exact boundaries of each phase (`prepareDurationMs`/`durationMs`/`tongueInDurationMs`), `result()` before/after `DONE`, no-op after `DONE`/on `IDLE`, default values). "Tongue in" phase duration doubled (`tongueInDurationMs`, `TONGUE_IN_RECORDING_MULTIPLIER`) to cover several jaw-movement cycles -- device-confirmed. "Tongue out" phase received the same duration treatment (`tongueOutDurationMs`, `TONGUE_OUT_RECORDING_MULTIPLIER`) once stages 1/2 were removed from the hard filter. A `PREPARE_TONGUE_OUT` pause (new enum value) gives time to get into position before recording starts, rather than starting directly on button press -- explicit user feedback. `DEFAULT_CALIBRATION_PREPARE_DURATION_MS` raised from 2000 to 3000ms, now shared by both pauses. |
| `TongueOutInjectionGate.kt` | `TongueOutInjectionState.next()`, `.confirmed()`, `tongueOutInjectionShouldUpdate()` -- debounce before network injection (requires several consecutive `TONGUE_OUT` classifications) | `TongueOutInjectionGateTest.kt` | ✅ Covered (15 tests -- initial state, increment/reset per classification type, default and custom thresholds, reproduction of two observed residuals (an isolated frame, a consecutive pair) confirmed insufficient to confirm, a sustained hold stays confirmed continuously). Added to allow enabling network injection despite a residual of isolated false positives. `tongueOutInjectionShouldUpdate()` (+5 tests) fixes a real bug where `next()` was called on every camera frame including ones where the `embed()` throttle had skipped classification, wrongly resetting the counter and making confirmation nearly impossible at normal camera rate. |
| `TongueOutDisplaySmoothing.kt` | `TongueOutDisplayState.next()` -- smoothing of the value displayed locally AND actually injected over the network (a hold countdown after the last positive detection, fed by the signal already debounced by `TongueOutInjectionGate.kt`, not the raw classification) | `TongueOutDisplaySmoothingTest.kt` | ✅ Covered (9 tests -- initial state, hold while the delay hasn't elapsed, expiry, accumulation over several non-positive frames, reset by a new positive, `TONGUE_IN`/`UNDECIDED`/`null` all treated identically as non-positive). Added against a fast 1/0 flicker observed during a hold -- doesn't touch any decision of the cascade itself (the pure function is unchanged), only what's passed into it. |

## logging/

| File | Covered element | Test(s) | Status |
|---|---|---|---|
| `LogFormatting.kt` | `formatLogLine()`, `maskIpAddresses()`, `appendWithRotation()`, `shouldPersist()` | `LogFormattingTest.kt` | ✅ Covered (13 tests -- line format with/without a call stack, IPv4 masking standalone/embedded in a message/multiple/absent, rotation under and beyond the size limit (keeps recent entries, never a partial leading line), level-threshold filtering). |
| `LogLevel.kt` | Enum (`VERBOSE`...`ERROR`, ordinal) | Exercised indirectly by `LogFormattingTest.kt` (`shouldPersist`) | ➖ No logic of its own to isolate, no invalid value possible for a Kotlin enum. |
| `AppLog.kt` | `v()`/`d()`/`i()`/`w()`/`e()` (real sink: `android.util.Log` + file) | -- | ❌ Depends on `BuildConfig.DEBUG`, `android.util.Log` and real disk access -- same category as the rest of this project's Android glue code. All the at-risk logic (formatting, masking, rotation, level filtering) is already covered separately in `LogFormatting.kt`; `AppLog` only orchestrates these pure functions before writing. |

## network/

| File | Covered element | Test(s) | Status |
|---|---|---|---|
| `IFacialMocapSender.kt` | `toIFacialMocapName()`, `buildMessage()` (extracted `internal`, pure) | `IFacialMocapSenderTest.kt` | ✅ Covered -- this format was already responsible for a silent bug (eyeSquint naming) before this coverage was added. |
| `IFacialMocapSender.kt` | `startListening()`, `send()`/`openSendSocket()` (socket part), `stopListening()` | -- | ❌ Depends on a real `DatagramSocket`/network thread -- needs an instrumented test or a fake socket. Not a priority, the at-risk part (the message format) is already covered separately. Includes disconnection detection (`connect()`/`startUdpLivenessProbe`) -- device-confirmed but **not reliable depending on the network** (see `NetworkUtils.kt` below), never affects sending itself. |
| `VmcOscSender.kt` | `buildBundle()`, `serializeToBytes()` (extracted `internal`, pure) | `VmcOscSenderTest.kt` | ✅ Covered -- `buildBundle()` groups the `/Val` + `/Apply` messages into a single `OSCBundle`. `serializeToBytes()` (added as a fix for an Android 11/API 30 `NoSuchMethodError` crash) is tested with a full round trip: serializes a bundle then reparses it with `OSCParser` and compares the content -- JVM-level proof that the `OSCPortOut` replacement produces valid bytes, not just that it compiles. |
| `VmcOscSender.kt` | `send()` (socket part), `connect()` | -- | ❌ Depends on a real `DatagramSocket`/UDP -- needs an instrumented test or a fake socket. The at-risk part (the bundle content AND its byte serialization) is already covered separately via `serializeToBytes()`. Same disconnection-detection note as `IFacialMocapSender.kt` above. |
| `NetworkUtils.kt` | `getLocalIpAddress()` | -- | ❌ Depends on the device's real network interfaces (`NetworkInterface.getNetworkInterfaces()`) -- environment-dependent, low value to mock for a simple local-IP read. |
| `NetworkUtils.kt` | `startUdpLivenessProbe()` (disconnection-detection `receive()` probe, shared by `VmcOscSender`/`IFacialMocapSender`) | -- | ❌ Depends on a real `DatagramSocket`/thread -- same limitation as the rest of this module. **Device-confirmed but its effect (detecting an unreachable target) doesn't trigger reliably depending on the network** -- two surfacing mechanisms were tried (`send()`, then this `receive()` probe), neither reliably sees the ICMP error on every network. Assumed best-effort, never blocks actual sending. |
| `VTubeStudioProtocol.kt` | JSON encoding/decoding of every VTube Studio Plugin API message (auth, parameter creation, injection) -- pure, kotlinx.serialization | `VTubeStudioProtocolTest.kt` | ✅ Covered. Includes decoding real server payloads (copied from the official docs) and tolerance to unknown fields (`ignoreUnknownKeys`). |
| `VTubeStudioConnectionState.kt` | `nextVTubeStudioConnectionState()` (pure state machine) | `VTubeStudioConnectionStateTest.kt` | ✅ Covered. All valid transitions, plus out-of-sequence events (ignored without crashing) and `Disconnect`/`SocketFailed` valid from any state. |
| `VTubeStudioSender.kt` | Real WebSocket socket (nv-websocket-client), driving the state machine in response to incoming messages | -- | ❌ Depends on a real `WebSocket`/VTube Studio server -- same limitation as `VmcOscSender.send()`/`connect()`. The at-risk part (JSON protocol, state transitions) is already covered separately. Confirmed functional end-to-end on device. |

## camera/

| File | Covered element | Test(s) | Status |
|---|---|---|---|
| `CameraController.kt` | `rotatedDimensions()` (extracted `internal`, pure) | `CameraControllerTest.kt` | ✅ Covered. |
| `CameraController.kt` | `bindImageAnalysis()`, `bindPreview()`, `processFrame()`, `acquirePooledBitmap()`, `releaseFrame()`, `peekPooledBitmap()` | -- | ❌ Depends on CameraX (`ProcessCameraProvider`, `ImageProxy`) and `android.graphics.Bitmap/Canvas/Matrix` -- needs an instrumented test (CameraX ships a test `CameraXConfig`, but it's still a device/emulator test) or Robolectric with its graphics shadows. The bitmap pool has already caused a real bug (identity comparison on `MPImage`, fixed): a good instrumented-test candidate if the app grows further here. `peekPooledBitmap()` feeds stage 2 of the tongue-out cascade on the CameraX tier. |
| `ArCoreHeadPoseTracker.kt` | ARCore session, YUV->Bitmap conversion, `peekLastBitmap()`, `releaseFrame()`, live camera background GL (`setupBackgroundProgram`/`drawCameraBackground`) | -- | ❌ Depends on ARCore (`Session`, `Frame`) and `GLSurfaceView.Renderer` -- same limitation as `CameraController.kt`, needs an instrumented test or manual on-device verification. `peekLastBitmap()`/`releaseFrame()` give stage 2 of the tongue-out cascade pixel access on the ARCore tier, which previously had no bitmap pool at all. The live camera background (textured GL quad, shader/buffers/draw call) is the same category of code -- shader compilation and `glDraw*` calls aren't exercisable in the JVM -- device-confirmed across all four holds (portrait, both landscape directions, upside-down); `backgroundQuadCoords` is a simple fixed quad (`val`), no runtime rotation logic remains to cover. |

## capabilities/

| File | Covered element | Test(s) | Status |
|---|---|---|---|
| `DeviceCapabilities.kt` | `DeviceCapabilities.looksHighEnd` | Exercised indirectly by `TrackingTierSelectorTest.kt` | ⚠️ Covered in practice (the tier-selection tests build `DeviceCapabilities` and check the resulting tier), but no dedicated test isolating `looksHighEnd` on its own. Worth adding if this heuristic grows more complex. |
| `DeviceCapabilities.kt` | `DeviceCapabilityDetector.detect()`, `isThermalThrottling()` | -- | ❌ Depends on `ArCoreApk`, `ActivityManager`, `PowerManager` -- needs Robolectric or an instrumented test. Polled continuously (`MainViewModel.startThermalPolling()`); the logic that consumes its result (rate reduction/ramp-up) is extracted into a pure function and tested separately, see `ThermalThrottle.kt` below. |

## sensors/

| File | Covered element | Test(s) | Status |
|---|---|---|---|
| `DeviceOrientationTracker.kt` | `rotationDeltaMatrix()`, `snapshotRotationMatrix()`, `start()`/`stop()` | -- | ❌ Depends on real `SensorManager`/`SensorEvent` to be exercised end-to-end. The math part (multiplication/transpose) they delegate to `RotationMath` is already covered by `RotationMathTest`. Low value to mock `SensorManager` just for this glue code. `start()`/`stop()` are called by `MainViewModel` on the lifecycle's `ON_START`/`ON_STOP` rather than once at init -- same note, non-testable glue code without Robolectric. |
| `IconOrientationTracker.kt` | `onOrientationChanged()` | -- | ❌ Direct wrapper around Android's `OrientationEventListener`, no logic of its own to isolate. |
| `BatteryMonitor.kt` | `onReceive()` | -- | ❌ Direct wrapper around `BroadcastReceiver`/`ACTION_BATTERY_CHANGED`, needs an instrumented test to simulate a real broadcast. |

## settings/

| File | Covered element | Test(s) | Status |
|---|---|---|---|
| `AppSettingsStore.kt`, `ConnectionSettingsStore.kt` | DataStore getters/setters (including `faceMeshOverlayEnabled`, `persistBlendshapeSelectionEnabled`, `persistedBlendshapeSelectionNames`, `debugForceArCoreUnavailable`, `debugForceGpuUnavailable`, `tongueOutDetectionEnabled`, `tongueReferencesCalibrated`, `tongueCalibrationRecordingDurationMs`, `tongueClassificationMargin`) | -- | ❌ Depend on `Context`/DataStore Preferences -- need Robolectric (with an in-memory `PreferenceDataStore`) or an instrumented test. A good candidate for a future pass: the logic (read a key, default value if absent, write) is simple but entirely uncovered today. |
| `TongueCalibrationStore.kt` | `load()`, `save()`, `clear()` -- CSV persistence of calibration references, stage 3 | `TongueCalibrationStoreTest.kt` | ✅ Covered (5 tests -- depends only on `java.io.File`, testable in pure JVM despite living in the `settings/` package, unlike `AppSettingsStore`/DataStore). Found a real bug: `save()` formatted floats with `"%.8f".format(it)` with no explicit locale -- under French locale this writes a decimal comma, which collides with the CSV separator and doubles the reloaded vector's length, silently breaking the similarity comparison (`simOut`/`simIn` stuck at 0.0, permanent `UNDECIDED`) after any app restart. Fixed (explicit `Locale.ROOT`), a regression test under French locale is included. |
| `AppLanguage.kt` | `appLanguageFromTag()` (pure tag/enum mapping) | `AppLanguageTest.kt` | ✅ Covered. Building the real `LocaleListCompat` and calling `AppCompatDelegate.setApplicationLocales()` (in `MainViewModel.setAppLanguage()`) remain untested, same limitation as the other Android-framework touch points listed in this document. |

## ui/ (Compose)

| File | Covered element | Test(s) | Status |
|---|---|---|---|
| `MainViewModel.kt` | Orchestration (camera, sensors, network, DataStore, power-save timer...) | -- | ❌ Depends on `AndroidViewModel`/`Application` and every component above. Deliberately kept "thin": its only non-trivial logic (calibration composition) has been extracted into `RotationMath.composeCalibratedEuler`, tested separately -- a strategy to keep following for any new logic added to the ViewModel. |
| `MainViewModel.kt` | `initializeTracking()` idempotency (`trackingInitialized` guard) | -- | ❌ A meaningful test would require instantiating a real `MainViewModel` (Application, ArCoreApk, ActivityManager...) and calling `initializeTracking()` twice -- needs Robolectric. A one-line guard, low regression risk; worth covering if Robolectric is introduced for other needs (e.g. `AppSettingsStore`). |
| `MainScreen.kt` | Attaching `IconOrientationTracker`/`BatteryMonitor` to the lifecycle (`ON_START`/`ON_STOP`) | -- | ❌ A Compose composable observing a real `Lifecycle` -- needs a Compose UI test or Robolectric to simulate lifecycle transitions. Verified manually. |
| `MainScreen.kt`, `MainHud.kt`, `SettingsScreen.kt`, `DiagnosticsScreen.kt`, `ConnectionSettingsScreen.kt`, `DisplaySettingsScreen.kt`, `ExperimentalFeaturesScreen.kt`, `LoggingSettingsScreen.kt`, `BlendshapeSelectionScreen.kt`, `BlendshapePanel.kt`, `PowerSaveOverlay.kt`, `LowBatteryAlert.kt`, `FaceMeshOverlay.kt`, `Theme.kt` | Composables | -- | ❌ Out of scope for JVM unit tests: need either Compose UI tests (`androidx.compose.ui.test`, instrumented or Robolectric) or manual verification. A separate topic from "unit tests" if pursued later. |
| `LandmarkProjection.kt` | `toScreenPoint()` (normalized -> screen projection, `PreviewView`-style centered crop when the image/canvas ratio differs, with/without mirroring, fallback if image dimensions are unknown) | `LandmarkProjectionTest.kt` | ✅ Covered -- the only math part of the mesh overlay (see `FaceMeshOverlay.kt` above, which uses it but stays itself out of JUnit scope). Extended to fix a mesh offset/squash observed on a device whose screen aspect ratio differs from the analyzed camera image. |
| `DebugPanelUnlock.kt` | `DebugPanelUnlockState.registerTap()` | `DebugPanelUnlockTest.kt` | ✅ Covered (pure function, no Android dependency -- consecutive-tap counting within a time window, reset outside the window, inclusive bound, no-op once unlocked). |
| `RotationBucket.kt` | `snapToRotationBucket()` | `RotationBucketTest.kt` | ✅ Covered (6 tests -- the 4 tier boundaries {0,90,180,270} and normalizing out-of-[0,360) values). Used for the blendshape panel's cosmetic rotation (`MainScreen.kt`). |

## Root

| File | Covered element | Test(s) | Status |
|---|---|---|---|
| `MainActivity.kt`, `MoCapApplication.kt` | Android lifecycle (`MoCapApplication.onCreate()` also initializes `AppLog` -- a direct call with no logic of its own to isolate) | -- | ❌ Pure Android entry points, testable only via an instrumented test. |

## Current state

35 test files, all pure JVM (no device/emulator required): `RotationMathTest`,
`TrackingTierSelectorTest`, `BlendshapeCatalogTest`, `FaceLandmarkerHelperTest`,
`IFacialMocapSenderTest`, `CameraControllerTest`, `VmcOscSenderTest`, `LandmarkProjectionTest`,
`ArCoreFaceSelectorTest`, `MeshOverlayVisibilityTest`, `ThermalThrottleTest`, `DebugPanelUnlockTest`,
`CalibrationAnomalyTest`, `BlendshapeStabilityTest`, `VTubeStudioProtocolTest`,
`VTubeStudioConnectionStateTest`, `AppLanguageTest`, `EyeAspectRatioTest`, `EyeBlinkCorrectionTest`,
`OneEuroFilterTest`, `AdaptiveEarFloorTest`, `LogFormattingTest`, `BrowRaiseTest`, `MirroringTest`,
`TongueOutGateTest`, `LipLandmarksTest`, `MouthColorAnalysisTest`, `TongueColorBaselineTest`,
`InferenceLoadMonitorTest`, `TongueEmbeddingClassifierTest`, `TongueCalibrationAveragingTest`,
`TongueCalibrationRecordingStateTest`, `TongueCalibrationStoreTest`, `TongueOutDisplaySmoothingTest`,
`TongueOutInjectionGateTest`. They cover the entirety of the logic identified as the most fragile
math/formatting (rotation/calibration -- including mirror mode, blendshape name mapping, eye gaze,
tier selection (including its manual override), camera rotation dimensions, OSC message grouping,
mesh screen projection, ARCore primary-face selection, overlay visibility, thermal-throttling
response, debug-mock-panel unlock, calibration-anomaly detection, VTube Studio Plugin API protocol
and state machine, in-app language mapping, Eye Aspect Ratio and its anti-leak/smoothing correction
for blink reliability, generic adaptive-cutoff filter, self-adaptive per-eye "closed" EAR reference,
log formatting/masking/rotation, geometric brow height, the tongue-out detection cascade -- jawOpen
gate, mouth crop, color analysis, adaptive color reference, inference-load monitor, embedding
classification and stage-3 personal calibration), relying on a few pure-function extractions
(`internal` visibility) that change no behavior.

## Tracking mesh overlay (toggleable option)

Optional overlay of MediaPipe's 478-point facial mesh onto the camera preview, toggleable from
settings (persisted). Follows the same principle as the rest of the app: the only math part -- the
projection of a normalized point into screen space, with a mirror effect to match the preview -- was
written TDD-first (`LandmarkProjectionTest.kt` before `LandmarkProjection.kt`) and isolated from the
composable that uses it (`FaceMeshOverlay.kt`, not testable in pure JUnit). A generic-avatar 3D
preview remains a separate, unimplemented idea for later.

Manual on-device verification note: the projection assumes the layer fills the whole screen at the
same width/height ratio as the image analyzed by MediaPipe, without compensating for any
`PreviewView` cropping -- if the points don't line up exactly with the on-screen face (a slight
offset or scale mismatch), this is the first place to look.

## Lifecycle and double-init-call -- no dedicated test, reason documented

Two topics -- sensors/battery not tied to the lifecycle, and `initializeTracking()` not guarded
against a double call -- contained no pure logic to extract, only Android glue code (`Lifecycle`
observation, a guard on an instance boolean). Implemented directly, without a red/green JUnit step
beforehand, with the reason documented here instead of silence. Manual verification recommended
after relaunching the app: background the app for a few seconds then return to it, and confirm
tracking resumes normally (sensors properly restarted) without a full restart.

## ARCore fusion -- integrated and confirmed functional on device

The pure math building block was prepared first, as with the rest of the app (extract the at-risk
logic into a pure function, cover it in JUnit, before touching the surrounding Android code):
`RotationMath.rotation3x3FromQuaternion()`, tested with two cases (identity quaternion, and a 90°
rotation around Z compared against the Z rotation matrix already used elsewhere in the tests).

What's covered in JUnit:
- `ArCoreFaceSelector.pickPrimary()` -- primary-face selection among tracked candidates, 4 cases
  (`ArCoreFaceSelectorTest.kt`).
- `computeMeshOverlayVisible()` (`ui/MeshOverlayVisibility.kt`) -- full truth table of the 4 boolean
  inputs (ARCore active, overlay setting, power-save mode, keep-in-power-save), 8 cases
  (`MeshOverlayVisibilityTest.kt`).

What's **not** covered, deliberately, for the same reason as the rest of this app's Android glue
code (`DeviceCapabilityDetector`, `CameraController`...): `ArCoreHeadPoseTracker` depends directly on
the ARCore SDK (`Session`, `GLSurfaceView.Renderer`, `Frame.acquireCameraImage()`) and on the
camera/lifecycle wiring in `MainViewModel` -- would need Robolectric or an instrumented test for
limited gain given the nature of the code (orchestration, no non-trivial isolable decision). This
lack of JUnit coverage doesn't reflect the feature's actual working state: ARCore fusion is confirmed
functional on device (see `AndroidMoCap_spec_technique.md`, §4) -- it's only this test-tracking file
that can't exercise it automatically.

## Rule going forward (TDD)

For any new feature or fix: 1) write the test describing the expected behavior first (it must fail),
2) write the minimal code to make it pass, 3) update this document (a new row in the relevant
module's table, or a new section). If a function contains non-trivial logic mixed with Android code
(as was the case for calibration or eye gaze), extract it into a pure function first (`internal` if
it's not part of the public API) before writing it -- this is what made it possible to cover almost
all of this app's at-risk logic without Robolectric or an instrumented test.
