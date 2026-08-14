# AndroidMoCap — Functional specification

*🇫🇷 Version française : [AndroidMoCap_spec_fonctionnelle_FR.md](AndroidMoCap_spec_fonctionnelle_FR.md)*

*Reference document describing the current state of the functional scope. Describes what the app
does today, for a non-technical reader -- not a changelog, not a history of decisions, not a
roadmap. For architecture and implementation choices, see `AndroidMoCap_spec_technique.md`.*

## 1. Overview

AndroidMoCap turns an Android phone into a facial motion-capture tracker for VTubing: the front
camera captures the face, the app computes a set of blendshapes (facial expression coefficients)
and streams them live, over the local network, to receiving software on a PC that animates an
avatar.

**Target audience**: streamers/VTubers using an Android phone as a facial tracking solution, as an
alternative to iOS solutions (iFacialMocap, FaceMotion3D) which benefit from a dedicated depth
sensor (TrueDepth) absent from Android phones.

**What the app is not**: not an avatar display app (unlike VTube Studio Android), not a cloud
service (all processing and data exchange stays local to the device and the local Wi-Fi network),
not store-first distributed (currently distributed only via GitHub Releases).

## 2. Primary use case

A streamer installs the app on an Android phone, positions it facing them (hand, stand, tripod...),
launches the app, calibrates a neutral pose, chooses the protocol and network target matching their
VTuber software, then starts streaming. The phone runs continuously for the whole session, generally
without being touched or looked at directly -- the avatar displayed on the PC side serves as the
visual feedback.

## 3. Functional scope

### 3.1 Capture and tracking

- Automatic selection of the best available pipeline based on the device (`COMPATIBLE`, `STANDARD`
  or `OPTIMAL` tier), based on ARCore support, the official Android performance class, CPU core
  count and total RAM -- no manual configuration required. Automatic fallback from GPU delegate to
  CPU if GPU initialization fails. On the `OPTIMAL` tier, head pose comes from ARCore Augmented
  Faces rather than MediaPipe (blendshapes are always computed by MediaPipe) -- automatic, silent
  fallback to the `STANDARD` tier if ARCore turns out to be unavailable at runtime despite a device
  that supports it in principle.
- Computation of 52 ARKit-format blendshapes from the front camera (MediaPipe Face Landmarker). Two
  blendshapes (`tongueOut`, `cheekPuff`) aren't reliably restituted by this model -- a limitation of
  the model itself, not an app bug. `tongueOut` benefits from an experimental alternative detection
  path (see below); `cheekPuff` remains without mitigation.
- **Experimental tongue-out detection** (`tongueOut`) -- a dedicated cascade (open-mouth gate →
  comparison against a personal embedding calibration), toggleable under "Experimental features"
  (§3.4). Once the personal calibration is done, the value is sent to the
  network protocols (§3.3), not just displayed locally -- classified as "Experimental" due to a
  residual risk of an isolated false positive.
- Gaze direction estimation (per eye, pitch/yaw) reconstructed from directional blendshapes
  (`eyeLookUp/Down/In/OutLeft/Right`), since MediaPipe doesn't provide this natively.
- Head rotation estimation from the facial transformation matrix provided by MediaPipe.
- Optional overlay (off by default) of the full tracking mesh (478 points) superimposed on the
  camera preview, for visual diagnostics.

### 3.2 Calibration

Manual calibration of the neutral pose, triggered on demand from the main bar, with a 5-second
countdown before capture -- gives the user time to return to a neutral expression facing the camera.

**Calibration anomaly detection**: the calibration button turns red if the head pose appears to have
drifted since the last calibration (face at rest but pose not returning close to zero, or a loss of
face detection followed by redetection) -- purely informational, no automatic action, only resolved
by an explicit new calibration.

### 3.3 Network connectivity

Three output protocols, mutually exclusive (only one active at a time, chosen in settings):

- **VMC/OSC** -- for Blender, Unity (not VTube Studio, which doesn't accept this protocol as
  input). The app sends data to a PC IP/port entered manually in settings.
- **iFacialMocap/UDP-compatible protocol** -- for VBridger. Shown as "UDP / VBridger" in the
  interface (the "iFacialMocap" name stays as a secondary mention to remain findable by anyone
  searching for that exact term, but the app doesn't connect to that third-party app, it only
  implements a compatible protocol). The app listens passively; it's the PC software that connects
  in, using the phone's IP shown in settings -- no manual entry needed on the phone side for this
  path.
- **VTube Studio Plugin API** -- direct integration, bypassing VMC/OSC which VTube Studio doesn't
  accept. IP/port (8001 by default) entered manually, same as VMC. An authorization popup must be
  accepted in VTube Studio on first connection (the token is then remembered, with an automatic new
  request if it's revoked in the meantime, plus a "Forget token" button as a fallback). Once
  connected, the created parameters must be mapped once by the user in VTube Studio's parameter
  editor to animate a Live2D model -- the app cannot do this on the user's behalf.

The phone and the receiving PC must be on the same local Wi-Fi network in all three cases.

### 3.4 User interface

- **Minimal HUD bar**, always shown over the full-screen camera preview: face-detection indicator,
  connect/disconnect button, calibration button (with countdown ring), settings access. Each icon
  rotates in place to stay readable regardless of the angle the phone is held or set down at.
- **Settings screen**, organized into a 5-category menu (each its own screen, back to the menu via
  a standard arrow or the system back button/gesture): Diagnostics (read-only -- active tier,
  GPU/CPU delegate, face detected, inference latency, calibration state), Connection (type + network
  target, only the sub-block for the chosen type is shown), Display & comfort (access to the
  displayed-blendshapes selection screen + its persistence, tracking mesh overlay, power-save mode,
  low-battery alert threshold), Experimental features (tongue-out detection toggle + access to its
  dedicated calibration screen, see above), Logging (log level, log file sharing).
- **Displayed-blendshapes selection screen**: full catalog of the 52 ARKit blendshapes, grouped by
  category (eyebrows, eyes, cheeks, nose, jaw, mouth, tongue), with search. Shows the live value of
  each checked blendshape on the main screen. A discreet warning icon next to blendshapes known to
  be poorly restituted by MediaPipe (`jawForward`, `jawLeft`, `jawRight`, `mouthDimpleLeft/Right`,
  `cheekPuff`, `tongueOut`) -- informational, doesn't prevent selection. Not kept across sessions by
  default, but persistence can be enabled in settings (Display & comfort).
- **Navigation**: every overlay screen (settings and its 5 categories, blendshape selection) closes
  via a standard back arrow, the hardware back button, or the system swipe gesture (predictive back,
  Android 13+) -- all three trigger the same action.
- **Language**: interface available in French (default) and English. Two ways to choose it: the
  system per-app selector (Android settings, Android 13+ only), or an **in-app** selector (Display &
  comfort > "App language" -- "Follow system" / "Français" / "English"), which works on every
  Android version and remembers the choice automatically across launches. Changing the language from
  this selector closes the currently open settings screen (back to the main screen) while the change
  is applied. ARKit blendshape names (`jawOpen`, `mouthSmileLeft`...) stay in technical English
  regardless of the chosen language -- they are protocol identifiers, not display text.

### 3.5 Power management

- **Power-save mode**: after a configurable idle delay (no screen touch), dims the screen to
  minimum and hides the displayed camera preview -- tracking and network sending continue normally
  in the background. Immediate exit on the slightest touch. Designed for long sessions where the
  phone sits away from the user.
- **Low-battery alert**: a visual overlay (pulsing icon) when the battery drops below a
  configurable threshold and the phone isn't charging.
- **Dynamic thermal throttling**: the target analysis rate is halved if the device heats up during
  a session (a discreet icon in the HUD bar), and ramps back up automatically once it cools down --
  no user action required.

### 3.6 Distribution

Distributed outside the Play Store, via GitHub Releases (signed APK, published automatically on
every version tag). No in-app update check.

## 4. Cross-cutting functional constraints

- **Privacy / network**: no communication other than the voluntary stream to the target chosen by
  the user, on the local network -- no telemetry, no third-party service.
- **Required hardware**: a physical device with a front camera; the Android emulator doesn't
  provide a usable camera feed for tracking.
- **Minimum Android version**: Android 11 (API 30).
- **License**: free use of the software, including commercial use, except building a competing
  product -- see `LICENSE`.

## 5. Quick glossary

- **Blendshape**: a coefficient (0 to 1) representing the intensity of an elementary facial
  expression (smile, blink, jaw opening...), in the standardized ARKit format (52 coefficients).
- **VMC (Virtual Motion Capture)**: an OSC-based network protocol, the de facto standard for
  streaming mocap data to VTuber software (Blender, Unity, VSeeFace... -- not VTube Studio, which
  uses its own Plugin API, see §3.3).
- **Tracking tier**: the pipeline level automatically chosen based on device capabilities
  (`COMPATIBLE` < `STANDARD` < `OPTIMAL`), determining the delegate (CPU/GPU), the target rate and
  the head-pose source used.
- **Perfect Sync**: a convention (from VSeeFace/VBridger) describing an avatar rig able to use the
  full set of 52 ARKit blendshapes, beyond basic expressions.
