# AndroidMoCap — Unit test tracking

*🇫🇷 Version française : [AndroidMoCap_unit_tests_FR.md](AndroidMoCap_unit_tests_FR.md)*

Reference document on the current state of test coverage -- not a changelog, not a history of
fixes. Lists, module by module, what's covered by pure JVM unit tests (`app/src/test/`, run via
`./gradlew testDebugUnitTest`, no device/emulator required), and why the rest isn't.

Two broad categories recur throughout. **Pure logic** code (math, tier selection, message
formatting, static catalog...) doesn't depend on any Android class and is tested directly in the
JVM -- this is the core of the current coverage, and the priority for any new function of this
kind. **Android-framework-bound** code (sensors, camera, native MediaPipe, DataStore, Compose)
needs a device/emulator (instrumented test) or Robolectric to simulate the Android environment --
not covered, the precise reason is given each time rather than a plain "not tested".

## tracking/

| File | Covered element | Test(s) | Status |
|---|---|---|---|
| `RotationMath.kt` | `multiply`, `transpose`, `rotation3x3FromColumnMajor4x4`, `toEulerDegrees`, `mirrorEulerDegrees`, `composeCalibratedEuler`, `rotation3x3FromQuaternion` | `RotationMathTest.kt` | ✅ Covered (15 tests) -- the app's trickiest class (a known axis-inversion risk), top priority. `rotation3x3FromQuaternion` is the building block for ARCore fusion: converts ARCore's `Pose#getRotationQuaternion()` quaternion to the same 3x3 format as the rest of the pipeline. `toEulerDegrees` doesn't mirror yaw (native/anatomical behavior) -- `mirrorEulerDegrees` carries mirror mode explicitly, tested in isolation. |
| `TrackingTier.kt` | `TrackingTierSelector.select()` (including the `override` parameter) | `TrackingTierSelectorTest.kt` | ✅ Covered (pure `DeviceCapabilities -> TierConfig` function; a non-null `override` short-circuits automatic selection, a null `override` behaves like its absence -- manual tier forcing from Diagnostics). |
| `BlendshapeCatalog.kt` | `all`, `byCategory`, `unreliable` | `BlendshapeCatalogTest.kt` | ✅ Covered (structural consistency: 52 entries, no duplicates, category/full-list overlap, `unreliable` names all present in `all`). |
| `FaceLandmarkerHelper.kt` | `computeEyeGazeDegrees()` (extracted `internal`, pure) | `FaceLandmarkerHelperTest.kt` | ✅ Covered. |
| `FaceLandmarkerHelper.kt` | `setup()`, `tryCreateLandmarker()`, `detectAsync()`, `onLiveStreamResult()` (including extracting `faceLandmarks()`) | -- | ❌ Not testable in pure JUnit: wraps the native MediaPipe engine (`FaceLandmarker.createFromOptions`, real GPU/CPU delegate) -- needs a device/emulator. `FaceLandmarkerResult` also can't be constructed by hand in a test (its `create()` factory is package-private on the MediaPipe side). |
| `FaceTrackingResult.kt` | Data class (`FaceTrackingResult`, `BlendshapeScore`) | -- | ➖ No logic of its own, serves as a fixture for other tests (see `IFacialMocapSenderTest`, `FaceLandmarkerHelperTest`). |
| `ThermalThrottle.kt` | `ThermalThrottleState.next()` | `ThermalThrottleTest.kt` | ✅ Covered (pure function, no real-timing dependency -- rate reduction/ramp-up, floor, consecutive-poll threshold for `downgradeSuggested`, sticky behavior, short bursts that don't trigger the suggestion). |
| `CalibrationAnomaly.kt` | `CalibrationAnomalyState.next()`, `maxAbsEulerDegrees()` | `CalibrationAnomalyTest.kt` | ✅ Covered (pure function, no real-timing dependency -- sticky behavior, both signals (rest+drift, face redetection), rest/pose gating, short bursts that don't trigger the anomaly, active expression vs. genuine stillness, redetection independent of rest). |
| `BlendshapeStability.kt` | `meanAbsoluteBlendshapeDelta()` | `BlendshapeStabilityTest.kt` | ✅ Covered (pure function -- sentinel value when comparison is impossible, correct delta, pairing by name rather than position, robust to a different order between the two lists). |
| `EyeAspectRatio.kt` | `eyeAspectRatio()`, `eyeAspectRatioFromLandmarks()` | `EyeAspectRatioTest.kt` | ✅ Covered (pure function -- open eye within the expected range, closed eye near zero, division-by-zero guard for coincident corners, reading the right indices from a landmark list, too-short list -> 0). Used by `EyeBlinkCorrection.kt`. |
| `EyeBlinkCorrection.kt` | `earOpenness()`, `correctBlinkScore()`, `correctEyeBlinkScores()` | `EyeBlinkCorrectionTest.kt` | ✅ Covered (10 tests -- damping a left/right leak vs. an unchanged pass-through of a genuine closure, end-to-end orchestration including a hold under realistic drift, and a real blink at an unusual camera angle that improves after a few episodes). Corrects the `eyeBlinkLeft/Right` blendshape before any network sending. Temporal smoothing relies on `OneEuroFilter.kt` below. The "closed" reference is self-adaptive (`AdaptiveEarFloor`, next row). |
| `EyeBlinkCorrection.kt` | `AdaptiveEarFloor.next()` -- self-adaptive per-eye "closed" EAR reference | `AdaptiveEarFloorTest.kt` | ✅ Covered (6 tests -- initial value, no change below the activity threshold with no episode in progress, minimum retained across the whole episode even if EAR rises mid-way, partial blending at the end of the episode, convergence across several episodes toward the true minimum, an isolated polluted episode that doesn't redefine the reference on its own). Corrects a real wink crushed at a low-angle camera position. |
| `BrowRaise.kt` | `browRaiseRatio()`, `browRaiseRatioFromLandmarks()` -- same principle as EAR applied to eyebrows | `BrowRaiseTest.kt` | ✅ Covered (5 tests -- raised brow > lowered brow, computation on known geometry, coincident eye corners -> zero, reading the right indices, too-short list -> zero). Left/right index correspondence ([BrowLandmarkIndices]) inferred by analogy, **not yet device-confirmed** -- a temporary diagnostic is in place (`BrowDiag`). |
| `Mirroring.kt` | `mirrorBlendshapeName()`, `mirrorBlendshapes()`, `mirrorFaceTrackingResult()` -- mirror mode (head + left/right blendshapes + per-eye gaze) | `MirroringTest.kt` | ✅ Covered (8 tests -- name swapping, unpaired blendshapes/`FaceTrackingResult` left unchanged, systematic coverage of the real catalog, involution). Guarantees mirror mode applies to head and left/right blendshapes consistently with each other. |
| `OneEuroFilter.kt` | Adaptive-cutoff low-pass filter (`minCutoff`, `beta`, `dCutoff`) | `OneEuroFilterTest.kt` | ✅ Covered (8 tests -- first sample unsmoothed, stable constant signal, damped isolated jump, high `beta` reduces lag on a sustained movement, low `minCutoff` smooths a noisy signal more, convergence on a held target). Integrated into `EyeBlinkCorrection.kt`. |
| `TongueOutGate.kt` | `jawOpenGateOpen()`, `mouthGeometricGateOpen()` -- stage 1 of the tongue-out cascade | `TongueOutGateTest.kt` | ✅ Covered (9 tests -- under/at/above the threshold, 0f/1f bounds, for both gates). `jawOpenGateOpen()` is the only gate that actually blocks stage 1 -- reliable on both CameraX and ARCore tiers. `mouthGeometricGateOpen()` exists and stays tested but is diagnostic-only: a genuine "tongue out" hold can produce the same geometric signature as a pressed mouth (near-total overlap), that case is resolved by stage 3 rather than by this gate. |
| `LipLandmarks.kt` | `mouthOpennessRatio()`, `mouthCropRegion()` | `LipLandmarksTest.kt` | ✅ Covered (9 tests -- known geometry, coincident corners -> null/0, too-short list, invalid image dimensions, bounds clamping, `MIN_CROP_DIMENSION_PX` degenerate-case guard). `LipLandmarkIndices` is confirmed correct on device (raw coordinates logged and compared). `MIN_CROP_DIMENSION_PX` (degenerate-case guard) stays in place but is no longer the main line of defense against the "pressed mouth" false positive -- see `mouthGeometricGateOpen()` above. |
| `MouthColorAnalysis.kt` | `rgbToHsv()`, `isTongueColoredPixel()`, `tonguePixelRatio()`, `colorGateOpen()` -- stage 2 | `MouthColorAnalysisTest.kt` | ✅ Covered (18 tests, hand-derived HSV values). Mechanically functional on device (CameraX + ARCore) but the color classifier itself is **not reliable** in practice (the threshold drifts too much session to session) -- see `TongueColorBaseline.kt` below. Functions stay pure/tested/logged, but `colorGateOpen()`/`adaptiveFired` no longer gate stage 3's triggering -- diagnostics only. |
| `TongueColorBaseline.kt` | `TongueColorBaseline.next()`, `isElevated()` -- adaptive "no tongue" reference (same principle as `AdaptiveEarFloor`) | `TongueColorBaselineTest.kt` | ✅ Covered (6 tests -- first sample, EMA drift in the normal zone, no drift in the elevated zone, `isElevated` below/above the margin). Improves discrimination over a fixed threshold without making it reliable. Same as `MouthColorAnalysis.kt` above: no longer used as a blocking filter, diagnostics only. |
| `InferenceLoadMonitor.kt` | `InferenceLoadState.next()` (EMA), `isRunningHigh()` | `InferenceLoadMonitorTest.kt` | ✅ Covered (6 tests -- raw first sample, EMA convergence, `isRunningHigh` below/above the margin, false before the first sample). Feeds the tongue-out detection load warning. |
| `TongueEmbeddingClassifier.kt` | `cosineSimilarity()`, `classifyTongueState()` -- stage 3 | `TongueEmbeddingClassifierTest.kt` | ✅ Covered (9 tests -- identical/orthogonal/opposite vectors, zero vector without NaN, classification picks the nearest reference, `UNDECIDED` if the reference is empty or the margin isn't met). The "tongue in + jaw movement" overlap (risk of false `TONGUE_OUT`) is mitigated by a widened calibration (`TongueCalibrationRecordingState.kt`), the remaining residual is filtered by the injection debounce (`TongueOutInjectionGate.kt`) -- network injection active. |
| `TongueEmbeddingHelper.kt` | `setup()`, `embed()` (GPU/CPU, `RunningMode.IMAGE`) | -- | ❌ Not testable in pure JUnit: wraps MediaPipe's native `ImageEmbedder`, same reason as `FaceLandmarkerHelper.setup()` above. |
| `TongueCalibrationAveraging.kt` | `EmbeddingAccumulator.accumulate()`, `average()` | `TongueCalibrationAveragingTest.kt` | ✅ Covered (5 tests -- single sample, hand-derived per-dimension average, zero samples -> null, inconsistent size -> exception). |
| `TongueCalibrationRecordingState.kt` | `tick()`, `result()` -- calibration state machine (`PREPARE_TONGUE_OUT` → `RECORDING_TONGUE_OUT` → `PREPARE_TONGUE_IN` → `RECORDING_TONGUE_IN` → `DONE`) | `TongueCalibrationRecordingStateTest.kt` | ✅ Covered (20 tests -- start, phase-isolated accumulation, both `PREPARE_*` phases never accumulate, transitions at the exact boundaries of each phase (`prepareDurationMs`/`durationMs`/`tongueInDurationMs`), `result()` before/after `DONE`, no-op after `DONE`/on `IDLE`, default values). Both recording phases (tongue out, tongue in) are preceded by a preparation pause (`PREPARE_TONGUE_OUT`/`PREPARE_TONGUE_IN`, shared `DEFAULT_CALIBRATION_PREPARE_DURATION_MS`, 3000ms) to give time to get into position before recording starts. Each recording phase applies its own duration multiplier (`TONGUE_OUT_RECORDING_MULTIPLIER`, `TONGUE_IN_RECORDING_MULTIPLIER`) to cover several movement cycles during calibration. |
| `TongueOutInjectionGate.kt` | `TongueOutInjectionState.next()`, `.confirmed()`, `tongueOutInjectionShouldUpdate()` -- debounce before network injection (requires several consecutive `TONGUE_OUT` classifications) | `TongueOutInjectionGateTest.kt` | ✅ Covered (15 tests -- initial state, increment/reset per classification type, default and custom thresholds, reproduction of two residual shapes (an isolated frame, a consecutive pair) insufficient to confirm, a sustained hold stays confirmed continuously). Allows enabling network injection despite a residual of isolated false positives. `tongueOutInjectionShouldUpdate()` (+5 tests) ensures the counter only advances on frames where a classification actually happened -- the `embed()` throttle can skip a camera frame without classifying it. |
| `TongueOutDisplaySmoothing.kt` | `TongueOutDisplayState.next()` -- smoothing of the value displayed locally AND actually injected over the network (a hold countdown after the last positive detection, fed by the signal already debounced by `TongueOutInjectionGate.kt`, not the raw classification) | `TongueOutDisplaySmoothingTest.kt` | ✅ Covered (9 tests -- initial state, hold while the delay hasn't elapsed, expiry, accumulation over several non-positive frames, reset by a new positive, `TONGUE_IN`/`UNDECIDED`/`null` all treated identically as non-positive). Avoids a fast 1/0 flicker during a hold -- doesn't touch any decision of the cascade itself (the pure function is unchanged), only what's passed into it. |

## logging/

| File | Covered element | Test(s) | Status |
|---|---|---|---|
| `LogFormatting.kt` | `formatLogLine()`, `maskIpAddresses()`, `appendWithRotation()`, `shouldPersist()` | `LogFormattingTest.kt` | ✅ Covered (13 tests -- line format with/without a call stack, IPv4 masking standalone/embedded in a message/multiple/absent, rotation under and beyond the size limit (keeps recent entries, never a partial leading line), level-threshold filtering). |
| `LogLevel.kt` | Enum (`VERBOSE`...`ERROR`, ordinal) | Exercised indirectly by `LogFormattingTest.kt` (`shouldPersist`) | ➖ No logic of its own to isolate, no invalid value possible for a Kotlin enum. |
| `AppLog.kt` | `v()`/`d()`/`i()`/`w()`/`e()` (real sink: `android.util.Log` + file) | -- | ❌ Depends on `BuildConfig.DEBUG`, `android.util.Log` and real disk access -- same category as the rest of this project's Android glue code. All the at-risk logic (formatting, masking, rotation, level filtering) is already covered separately in `LogFormatting.kt`; `AppLog` only orchestrates these pure functions before writing. |

## network/

| File | Covered element | Test(s) | Status |
|---|---|---|---|
| `IFacialMocapSender.kt` | `toIFacialMocapName()`, `buildMessage()` (extracted `internal`, pure) | `IFacialMocapSenderTest.kt` | ✅ Covered -- this format is at risk of a silent bug if naming disagrees with the receiver's expected protocol (e.g. `eyeSquint`). |
| `IFacialMocapSender.kt` | `startListening()`, `send()`/`openSendSocket()` (socket part), `stopListening()` | -- | ❌ Depends on a real `DatagramSocket`/network thread -- needs an instrumented test or a fake socket. Not a priority, the at-risk part (the message format) is already covered separately. Includes disconnection detection (`connect()`/`startUdpLivenessProbe`), best-effort and not reliable depending on the network (see `NetworkUtils.kt` below), which never affects sending itself. |
| `VmcOscSender.kt` | `buildBundle()`, `serializeToBytes()` (extracted `internal`, pure) | `VmcOscSenderTest.kt` | ✅ Covered -- `buildBundle()` groups the `/Val` + `/Apply` messages into a single `OSCBundle`. `serializeToBytes()` works around a `NoSuchMethodError` on `OSCPortOut` under Android 11/API 30: tested with a full round trip, serializes a bundle then reparses it with `OSCParser` and compares the content -- JVM-level proof that the replacement produces valid bytes, not just that it compiles. |
| `VmcOscSender.kt` | `send()` (socket part), `connect()` | -- | ❌ Depends on a real `DatagramSocket`/UDP -- needs an instrumented test or a fake socket. The at-risk part (the bundle content AND its byte serialization) is already covered separately via `serializeToBytes()`. Same disconnection-detection note as `IFacialMocapSender.kt` above. |
| `NetworkUtils.kt` | `getLocalIpAddress()` | -- | ❌ Depends on the device's real network interfaces (`NetworkInterface.getNetworkInterfaces()`) -- environment-dependent, low value to mock for a simple local-IP read. |
| `NetworkUtils.kt` | `startUdpLivenessProbe()` (disconnection-detection `receive()` probe, shared by `VmcOscSender`/`IFacialMocapSender`) | -- | ❌ Depends on a real `DatagramSocket`/thread -- same limitation as the rest of this module. Its effect (detecting an unreachable target) doesn't trigger reliably depending on the network -- two surfacing mechanisms are in place (`send()`, then this `receive()` probe), neither reliably sees the ICMP error. Assumed best-effort, never blocks actual sending. |
| `VTubeStudioProtocol.kt` | JSON encoding/decoding of every VTube Studio Plugin API message (auth, parameter creation, injection) -- pure, kotlinx.serialization | `VTubeStudioProtocolTest.kt` | ✅ Covered. Includes decoding real server payloads (copied from the official docs) and tolerance to unknown fields (`ignoreUnknownKeys`). |
| `VTubeStudioConnectionState.kt` | `nextVTubeStudioConnectionState()` (pure state machine) | `VTubeStudioConnectionStateTest.kt` | ✅ Covered. All valid transitions, plus out-of-sequence events (ignored without crashing) and `Disconnect`/`SocketFailed` valid from any state. |
| `VTubeStudioSender.kt` | Real WebSocket socket (nv-websocket-client), driving the state machine in response to incoming messages | -- | ❌ Depends on a real `WebSocket`/VTube Studio server -- same limitation as `VmcOscSender.send()`/`connect()`. The at-risk part (JSON protocol, state transitions) is already covered separately. |
| `VBridgerFormulas.kt` | `rawArkitFormulas`, `vbridgerCompositeFormulas`, `activeFormulas()`, `evaluate()`, and every composite formula (`faceAngleX/Y/Z`, `eyeOpen`, `eyeGazeX/Y`, `mouthSmile`, `mouthPuckerOut`, `mouthX`, `mouthShrug`, `mouthPressLipOpen`, `browY`, `brows`, `bodyAngleX/Y/Z`) -- ARKit-to-VBridger translation for VTube Studio, pure | `VBridgerFormulasTest.kt` | ✅ Covered -- registry structure (`rawArkitFormulas` size/creation-flag/`tongueOut` presence, `activeFormulas(false/true)` exclusive not additive, `evaluate()` ordering), neutral-face rest values matching VBridger's own documented defaults, non-neutral cases for the more elaborate formulas (`MouthSmile`, `MouthPressLipOpen`, `MouthX`), the `mouthDimpleLeft/Right` damping weight, direct-yaw-tracking (no redundant mirror-canceling negation) for `FaceAngleX`/`BodyAngleX`, and the VTS-axis-convention inversions for `FaceAngleZ`/`BodyAngleZ`/`EyeRightX`/`EyeLeftX`. |

## camera/

| File | Covered element | Test(s) | Status |
|---|---|---|---|
| `CameraController.kt` | `rotatedDimensions()` (extracted `internal`, pure) | `CameraControllerTest.kt` | ✅ Covered. |
| `CameraController.kt` | `bindImageAnalysis()`, `bindPreview()`, `processFrame()`, `acquirePooledBitmap()`, `releaseFrame()`, `peekPooledBitmap()` | -- | ❌ Depends on CameraX (`ProcessCameraProvider`, `ImageProxy`) and `android.graphics.Bitmap/Canvas/Matrix` -- needs an instrumented test (CameraX ships a test `CameraXConfig`, but it's still a device/emulator test) or Robolectric with its graphics shadows. The bitmap pool is a sensitive spot (identity comparison on `MPImage` rather than by value): a good instrumented-test candidate if the app grows further here. `peekPooledBitmap()` feeds stage 2 of the tongue-out cascade on the CameraX tier. |
| `ArCoreHeadPoseTracker.kt` | ARCore session, YUV->Bitmap conversion, `peekLastBitmap()`, `releaseFrame()`, live camera background GL (`setupBackgroundProgram`/`drawCameraBackground`) | -- | ❌ Depends on ARCore (`Session`, `Frame`) and `GLSurfaceView.Renderer` -- same limitation as `CameraController.kt`, needs an instrumented test or manual on-device verification. `peekLastBitmap()`/`releaseFrame()` give stage 2 of the tongue-out cascade pixel access on the ARCore tier. The live camera background (textured GL quad, shader/buffers/draw call) is the same category of code -- shader compilation and `glDraw*` calls aren't exercisable in the JVM; `backgroundQuadCoords` is a simple fixed quad (`val`), the fixed camera rotation works correctly across all tested physical orientations (portrait, both landscape directions, upside-down). |

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
| `TongueCalibrationStore.kt` | `load()`, `save()`, `clear()` -- CSV persistence of calibration references, stage 3 | `TongueCalibrationStoreTest.kt` | ✅ Covered (5 tests -- depends only on `java.io.File`, testable in pure JVM despite living in the `settings/` package, unlike `AppSettingsStore`/DataStore). `save()` formats floats with an explicit `Locale.ROOT` -- without it, under French locale, the decimal comma collides with the CSV separator and doubles the reloaded vector's length, silently breaking the similarity comparison (`simOut`/`simIn` stuck at 0.0, permanent `UNDECIDED`) after any app restart. A regression test under French locale is included. |
| `AppLanguage.kt` | `appLanguageFromTag()` (pure tag/enum mapping) | `AppLanguageTest.kt` | ✅ Covered. Building the real `LocaleListCompat` and calling `AppCompatDelegate.setApplicationLocales()` (in `MainViewModel.setAppLanguage()`) remain untested, same limitation as the other Android-framework touch points listed in this document. |

## ui/ (Compose)

| File | Covered element | Test(s) | Status |
|---|---|---|---|
| `MainViewModel.kt` | Orchestration (camera, sensors, network, DataStore, power-save timer...) | -- | ❌ Depends on `AndroidViewModel`/`Application` and every component above. Deliberately kept "thin": its only non-trivial logic (calibration composition) has been extracted into `RotationMath.composeCalibratedEuler`, tested separately -- a strategy to keep following for any new logic added to the ViewModel. |
| `MainViewModel.kt` | `initializeTracking()` idempotency (`trackingInitialized` guard) | -- | ❌ A meaningful test would require instantiating a real `MainViewModel` (Application, ArCoreApk, ActivityManager...) and calling `initializeTracking()` twice -- needs Robolectric. A one-line guard, low regression risk; worth covering if Robolectric is introduced for other needs (e.g. `AppSettingsStore`). |
| `MainScreen.kt` | Attaching `IconOrientationTracker`/`BatteryMonitor` to the lifecycle (`ON_START`/`ON_STOP`) | -- | ❌ A Compose composable observing a real `Lifecycle` -- needs a Compose UI test or Robolectric to simulate lifecycle transitions. Verified manually. |
| `MainScreen.kt`, `MainHud.kt`, `SettingsScreen.kt`, `AdvancedSettingsScreen.kt`, `ConnectionSettingsScreen.kt`, `DisplaySettingsScreen.kt`, `ComfortSettingsScreen.kt`, `ExperimentalFeaturesScreen.kt`, `BlendshapesScreen.kt`, `BlendshapePanel.kt`, `VtsFormulasScreen.kt`, `TongueCalibrationScreen.kt`, `LoadingScreen.kt`, `ConfirmationDialog.kt`, `ClickBlocking.kt`, `PowerSaveOverlay.kt`, `LowBatteryAlert.kt`, `FaceMeshOverlay.kt`, `Theme.kt` | Composables | -- | ❌ Out of scope for JVM unit tests: need either Compose UI tests (`androidx.compose.ui.test`, instrumented or Robolectric) or manual verification. `AdvancedSettingsScreen.kt` merges the former `DiagnosticsScreen.kt`/`LoggingSettingsScreen.kt`; `BlendshapesScreen.kt` is the former `BlendshapeSelectionScreen.kt`, promoted to a top-level settings category. `ClickBlocking.kt`/`ConfirmationDialog.kt` have no logic of their own to isolate (a `clickable {}` no-op consumer, a generic Material `AlertDialog` wrapper). |
| `LandmarkProjection.kt` | `toScreenPoint()` (normalized -> screen projection, `PreviewView`-style centered crop when the image/canvas ratio differs, with/without mirroring, fallback if image dimensions are unknown) | `LandmarkProjectionTest.kt` | ✅ Covered -- the only math part of the mesh overlay (see `FaceMeshOverlay.kt` above, which uses it but stays itself out of JUnit scope). Handles a screen/camera-image ratio mismatch (e.g. phones with an unusual screen aspect ratio) so the mesh doesn't drift/squash on screen. |
| `DebugPanelUnlock.kt` | `DebugPanelUnlockState.registerTap()` | `DebugPanelUnlockTest.kt` | ✅ Covered (pure function, no Android dependency -- consecutive-tap counting within a time window, reset outside the window, inclusive bound, no-op once unlocked). |
| `RotationBucket.kt` | `snapToRotationBucket()` | `RotationBucketTest.kt` | ✅ Covered (6 tests -- the 4 tier boundaries {0,90,180,270} and normalizing out-of-[0,360) values). Used for the blendshape panel's cosmetic rotation (`MainScreen.kt`). |

## Root

| File | Covered element | Test(s) | Status |
|---|---|---|---|
| `MainActivity.kt`, `MoCapApplication.kt` | Android lifecycle (`MoCapApplication.onCreate()` also initializes `AppLog` -- a direct call with no logic of its own to isolate) | -- | ❌ Pure Android entry points, testable only via an instrumented test. |

## Current state

37 test files, all pure JVM (no device/emulator required): `RotationMathTest`,
`TrackingTierSelectorTest`, `BlendshapeCatalogTest`, `FaceLandmarkerHelperTest`,
`IFacialMocapSenderTest`, `CameraControllerTest`, `VmcOscSenderTest`, `LandmarkProjectionTest`,
`ArCoreFaceSelectorTest`, `MeshOverlayVisibilityTest`, `ThermalThrottleTest`, `DebugPanelUnlockTest`,
`CalibrationAnomalyTest`, `BlendshapeStabilityTest`, `VTubeStudioProtocolTest`,
`VTubeStudioConnectionStateTest`, `AppLanguageTest`, `EyeAspectRatioTest`, `EyeBlinkCorrectionTest`,
`OneEuroFilterTest`, `AdaptiveEarFloorTest`, `LogFormattingTest`, `BrowRaiseTest`, `MirroringTest`,
`TongueOutGateTest`, `LipLandmarksTest`, `MouthColorAnalysisTest`, `TongueColorBaselineTest`,
`InferenceLoadMonitorTest`, `TongueEmbeddingClassifierTest`, `TongueCalibrationAveragingTest`,
`TongueCalibrationRecordingStateTest`, `TongueCalibrationStoreTest`, `TongueOutDisplaySmoothingTest`,
`TongueOutInjectionGateTest`, `RotationBucketTest`, `VBridgerFormulasTest`. They cover the entirety
of the logic identified as the most fragile math/formatting (rotation/calibration -- including
mirror mode, blendshape name mapping, eye gaze, tier selection (including its manual override),
camera rotation dimensions, OSC message grouping, mesh screen projection, ARCore primary-face
selection, overlay visibility, thermal-throttling response, debug-mock-panel unlock,
calibration-anomaly detection, VTube Studio Plugin API protocol and state machine, its
ARKit-to-VBridger formula translation, in-app language mapping, Eye Aspect Ratio and its
anti-leak/smoothing correction for blink reliability, generic adaptive-cutoff filter, self-adaptive
per-eye "closed" EAR reference, log formatting/masking/rotation, geometric brow height, the
tongue-out detection cascade -- jawOpen gate, mouth crop, color analysis, adaptive color reference,
inference-load monitor, embedding classification and stage-3 personal calibration, the blendshape
panel's cosmetic rotation bucketing), relying on a few pure-function extractions (`internal`
visibility) that change no behavior.

## Tracking mesh overlay (toggleable option)

Optional overlay of MediaPipe's 478-point facial mesh onto the camera preview, toggleable from
settings (persisted). Follows the same principle as the rest of the app: the only math part -- the
projection of a normalized point into screen space, with a mirror effect to match the preview -- was
written TDD-first (`LandmarkProjectionTest.kt` before `LandmarkProjection.kt`) and isolated from the
composable that uses it (`FaceMeshOverlay.kt`, not testable in pure JUnit).

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

## ARCore fusion

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
functional on device (see `AndroidMoCap_technical_spec.md`, §4) -- it's only this test-tracking file
that can't exercise it automatically.

## Rule going forward (TDD)

For any new feature or fix: 1) write the test describing the expected behavior first (it must fail),
2) write the minimal code to make it pass, 3) update this document (a new row in the relevant
module's table, or a new section). If a function contains non-trivial logic mixed with Android code
(as was the case for calibration or eye gaze), extract it into a pure function first (`internal` if
it's not part of the public API) before writing it -- this is what made it possible to cover almost
all of this app's at-risk logic without Robolectric or an instrumented test.
