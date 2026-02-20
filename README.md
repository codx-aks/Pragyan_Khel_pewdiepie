# ⚡ HighSpeed Camera – Android App

A Camera2-based high-speed video recording app supporting **120 fps** and **240 fps** at 1080p/720p/480p using `CameraConstrainedHighSpeedCaptureSession`. Includes manual controls for ISO, shutter speed, and FPS, plus JSON metadata saved alongside each video.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        MainActivity                              │
│                                                                  │
│  ┌──────────────────┐        ┌───────────────────────────────┐  │
│  │   LEFT PANEL     │        │        RIGHT PANEL            │  │
│  │   (Controls)     │        │    (No-preview Recording)     │  │
│  │                  │        │                               │  │
│  │  • FPS Spinner   │        │  • Recording status           │  │
│  │  • Resolution    │        │  • REC indicator + timer      │  │
│  │  • Manual toggle │        │  • ⏺ RECORD / ⏹ STOP        │  │
│  │  • ISO SeekBar   │        │  • ▶ PLAYBACK button          │  │
│  │  • Shutter Seek  │        │                               │  │
│  └──────────────────┘        └───────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Camera2 Pipeline                             │
│                                                                  │
│  CameraManager.openCamera()                                      │
│       │                                                          │
│       ▼                                                          │
│  CameraDevice.createConstrainedHighSpeedCaptureSession()         │
│  (uses CONSTRAINED_HIGH_SPEED_VIDEO capability)                  │
│       │                                                          │
│       ▼                                                          │
│  CameraConstrainedHighSpeedCaptureSession                        │
│       │                                                          │
│       ├─ createHighSpeedRequestList(captureRequest)              │
│       │    (creates burst list required for HS sessions)         │
│       │                                                          │
│       └─ setRepeatingBurst(requests) ──► MediaRecorder.Surface   │
│                                                                  │
│  CaptureRequest params:                                          │
│    • CONTROL_AE_TARGET_FPS_RANGE = [120,120] or [240,240]        │
│    • SENSOR_SENSITIVITY (ISO) ─── manual mode only              │
│    • SENSOR_EXPOSURE_TIME (ns) ─── manual mode only             │
│    • CONTROL_AE_MODE = OFF (manual) / ON (auto)                  │
└─────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Output Files                                 │
│                                                                  │
│  /sdcard/Movies/HighSpeedCam/                                    │
│       HSC_20240115_143022_120fps.mp4    ← H.264 video            │
│       HSC_20240115_143022_120fps_meta.json ← metadata sidecar   │
│                                                                  │
│  Metadata JSON:                                                  │
│  {                                                               │
│    "fps": 120,                                                   │
│    "resolution": "1920x1080",                                    │
│    "iso": 800,                                                   │
│    "shutter_speed": "1/120",                                     │
│    "shutter_speed_ns": 8333333,                                  │
│    "manual_exposure": false,                                     │
│    "timestamp_utc": "2024-01-15T14:30:22.000Z",                  │
│    "timestamp_epoch_ms": 1705327822000,                          │
│    "video_file": "HSC_20240115_143022_120fps.mp4",               │
│    "camera_id": "0",                                             │
│    "duration_seconds": 5.2                                       │
│  }                                                               │
└─────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│                     PlaybackActivity                             │
│                                                                  │
│  • VideoView (MediaPlayer) renders the MP4 at declared FPS       │
│  • Metadata card shows: FPS, ISO, shutter, timestamp, duration   │
│  • Seek bar with time display                                    │
│  • "Open in Gallery" → system intent for any installed player    │
│  • Share button → FileProvider URI sharing                       │
└─────────────────────────────────────────────────────────────────┘
```

---

## High-Speed Recording: How It Works

### Camera2 Constrained High-Speed Mode

Standard Camera2 sessions **cannot** achieve 120–240 fps reliably. Android provides a dedicated API:

```kotlin
// 1. Check capability
val capabilities = characteristics.get(
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
val supportsHS = capabilities.contains(
    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO)

// 2. Discover supported sizes and FPS ranges
val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
val hsSizes = map.highSpeedVideoSizes        // e.g. [1920x1080, 1280x720]
val hsFpsRanges = map.highSpeedVideoFpsRanges // e.g. [[120,120], [240,240]]

// 3. Create the constrained session (NO preview surface – maximizes FPS)
camera.createConstrainedHighSpeedCaptureSession(
    listOf(mediaRecorderSurface),
    sessionCallback,
    backgroundHandler
)

// 4. Create burst list (required for constrained HS sessions)
val requests = session.createHighSpeedRequestList(captureRequest)
session.setRepeatingBurst(requests, null, backgroundHandler)
```

### Why No Live Preview?

Constrained High-Speed sessions have a **hard rule**: all surfaces must be of the same size and FPS. Adding a preview `SurfaceView` alongside the `MediaRecorder` surface would require the preview to also run at 120–240 fps, which most display pipelines cannot sustain. Removing the preview:
- Lets the ISP dedicate full bandwidth to recording
- Avoids session configuration failures on mid-range devices
- Is the approach used by Google's own Camera2SlowMotion sample

---

## Manual Controls

| Control | Camera2 API | Range |
|---------|------------|-------|
| ISO | `SENSOR_SENSITIVITY` | 100–6400 (device-dependent) |
| Shutter Speed | `SENSOR_EXPOSURE_TIME` (nanoseconds) | 1/4000s → 1/30s |
| FPS | `CONTROL_AE_TARGET_FPS_RANGE` | 60/120/240 (device-dependent) |

**Important:** In constrained high-speed mode, `CONTROL_AE_MODE = OFF` is required for manual ISO/shutter. Some chipsets further restrict which keys can be set during HS sessions. The app gracefully falls back to auto exposure if manual control is rejected.

---

## Supported Devices

High-speed video (120fps+) requires specific hardware. Typical support:

| Device Category | 120fps | 240fps |
|----------------|--------|--------|
| Flagship (Pixel 8, Galaxy S24, etc.) | ✓ 1080p | ✓ 720p |
| Mid-range (Pixel 7a, etc.) | ✓ 720p | ✗ |
| Budget | ✗ | ✗ |

The app auto-detects capability and shows supported sizes/fps ranges on the main screen. If high-speed mode is unavailable, it falls back to a standard `CaptureSession` at the requested (lower) FPS.

---

## Building the App

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 34
- A physical device (emulators don't have camera2 high-speed support)

### Steps
```bash
# Clone / copy the project, then:
cd HighSpeedCamera
./gradlew assembleDebug

# Install on connected device:
./gradlew installDebug
```

### First Launch
1. Grant camera and microphone permissions when prompted
2. Check the capability info box – it will show which FPS/sizes your device supports
3. Select FPS and resolution
4. Optionally enable manual exposure and adjust ISO/shutter
5. Tap **⏺ RECORD** to start, **⏹ STOP** to finish
6. Tap **▶ PLAYBACK** to review in-app, or open in Gallery

---

## Output Video Details

- **Container:** MP4 (MPEG-4)
- **Video codec:** H.264 (AVC)
- **Audio codec:** AAC (stereo, 44.1 kHz, 128 kbps)
- **Bitrate:** ~50 Mbps for 1080p@240fps, scales down for smaller sizes
- **Location:** `Movies/HighSpeedCam/` on external storage

The recorded MP4 has the frame rate metadata embedded (e.g., 120fps). When played back:
- In the app → plays at real speed (not slowed down) since the container declares 120fps
- In most gallery apps → same behavior; the player respects the declared FPS
- For slow-motion playback → import into a video editor and interpret footage at 24/30fps

---

## File Structure

```
HighSpeedCamera/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/example/highspeedcamera/
│   │   ├── MainActivity.kt       ← Camera2 recording logic + UI
│   │   └── PlaybackActivity.kt   ← MediaPlayer playback + metadata display
│   └── res/
│       ├── layout/
│       │   ├── activity_main.xml
│       │   └── activity_playback.xml
│       ├── drawable/
│       │   ├── red_dot.xml
│       │   └── spinner_bg.xml
│       ├── values/
│       │   ├── themes.xml
│       │   └── strings.xml
│       └── xml/
│           └── file_paths.xml    ← FileProvider config
├── app/build.gradle
├── build.gradle
├── settings.gradle
└── gradle/libs.versions.toml
```

---

## References

- [Camera2 Capture Sessions & Requests](https://developer.android.com/media/camera/camera2/capture-sessions-requests)
- [REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO](https://developer.android.com/reference/android/hardware/camera2/CameraMetadata.html#REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO)
- [Camera2Video sample](https://github.com/android/camera-samples/tree/main/Camera2Video)
- [Camera2SlowMotion sample](https://github.com/android/camera-samples/tree/main/Camera2SlowMotion)
