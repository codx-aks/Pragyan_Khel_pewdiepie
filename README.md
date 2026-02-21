# Khel x PewDiePie
## Multi-Modal High-Speed Cricket Analysis System

A hybrid Android and web-based system implementing three core problem statements for high-speed cricket video analysis: 240 FPS camera recording with manual controls, frame drop/merge detection using temporal analysis, and AI-powered dynamic subject tracking with background blur.

<p align="center">
  <img src="readme_img.png" width="800"/>
</p>


## System Architecture

The system is split into two independent execution environments to maximize performance and leverage platform-specific capabilities.

### Android Application
- **Problem Statement 1:** 240 FPS camera recording with manual exposure controls
- **Problem Statement 2:** Frame drop and merge detection via temporal consistency analysis
- **Problem Statement 3 (Orchestration):** User interface and redirection to web module

### Web Module (Git Submodule)
- **Problem Statement 3 (Execution):** MediaPipe object detection, predictive tracking algorithm, and WebGPU-accelerated background blur rendering
- **Deployment:** Client-side execution via WebAssembly and WebGPU
- **Repository:** `focusntrack` (included as Git submodule)

### Architectural Decision

Problem Statement 3 requires real-time ML inference and GPU-accelerated rendering at 60fps. The web platform provides:
- MediaPipe Tasks Vision (EfficientDet-Lite0) compiled to WebAssembly
- WebGPU compute shaders for background blur processing
- Web Workers for non-blocking ML inference
- Cross-platform compatibility without device-specific optimization

The Android app acts as a launcher, redirecting users to the hosted web application at `https://focusntrack.vercel.app/`. This separation allows independent iteration on the tracking pipeline without recompiling the Android APK and leverages browser JIT optimization for the compute-intensive tracking algorithm.

**On-Device Processing Clarification:**  
All processing for Problem Statement 3 occurs **locally in the browser**. The web module downloads the EfficientDet model (4.1 MB) and processes video frames entirely client-side using WebAssembly. No frames are transmitted to external servers. The Vercel hosting serves only static HTML/JS/WASM assets.

---

## Problem Statement 1: 240 FPS Pro Camera Application

### Requirement
Capture high-speed cricket footage at 240 FPS with manual control over ISO, shutter speed, and frame rate for slow-motion analysis.

### Implementation

#### Camera API: Camera2 Constrained High-Speed Mode
Standard Camera2 sessions cannot reliably sustain 120-240 FPS. The implementation uses `CameraConstrainedHighSpeedCaptureSession`, which enforces specific constraints to achieve high frame rates.

The session is created with only the MediaRecorder surface (no preview) because constrained high-speed mode requires all surfaces to match the same size and FPS. Adding a preview would force the display pipeline to render at 240 FPS, which most devices cannot sustain.

#### Manual Exposure Control
Manual ISO and shutter speed require the `MANUAL_SENSOR` capability. The implementation sets `CONTROL_AE_MODE_OFF` to disable auto-exposure.

**Critical Implementation Detail:**  
Setting `CONTROL_AE_MODE_OFF` disables the auto-exposure pipeline entirely. On devices without proper `MANUAL_SENSOR` support, this breaks the auto white balance (AWB) pipeline, producing severe chroma noise. The implementation mitigates this by:
- Explicitly keeping `CONTROL_AWB_MODE = AUTO` even when AE is disabled
- Setting `CONTROL_AWB_LOCK = false` to allow continuous white balance adjustment
- Enabling `COLOR_CORRECTION_ABERRATION_MODE_FAST` for chromatic aberration correction

#### Recording Pipeline
- **Codec:** H.265 (HEVC) for better compression at 240 FPS
- **Bitrate:** Dynamic calculation based on resolution and FPS
- **Container:** MP4 (MPEG-4) with frame rate metadata embedded
- **Audio:** AAC stereo at 44.1 kHz, 128 kbps

#### Metadata Capture
Each recording generates a sidecar JSON file with frame-accurate metadata:

```json
{
  "fps": 240,
  "resolution": "1920x1080",
  "iso": 800,
  "shutter_speed": "1/4000",
  "shutter_speed_ns": 250000,
  "manual_exposure": true,
  "timestamp_utc": "2026-02-21T10:15:30.000Z",
  "timestamp_epoch_ms": 1740132930000,
  "video_file": "HSC_20260221_101530_240fps.mp4",
  "camera_id": "0",
  "duration_seconds": 8.4
}
```

The JSON is saved to `Downloads/HighSpeedCam/` using `MediaStore` (API 29+) or direct file access (API < 29).

#### Design Trade-offs

**No Live Preview:**  
Constrained high-speed sessions require all surfaces to match resolution and FPS. Adding a preview `SurfaceView` would force the display pipeline to render at 240 FPS, which causes session configuration failures on most devices. Google's own Camera2SlowMotion sample uses the same no-preview approach.

**FPS-Scaled Duplicate Detection:**  
At 240 FPS, frame-to-frame pixel difference drops significantly even with motion present. The implementation dynamically scales the duplicate threshold using logarithmic scaling to prevent false positives where the transcoder interpolates frames during encoding.

**Graceful Degradation:**  
If the device doesn't support constrained high-speed, the implementation falls back to standard `CaptureSession` with the highest available FPS range advertised by the device.

---

## Problem Statement 2: Frame Drop and Merge Detection

### Requirement
Analyze recorded high-speed cricket footage to detect:
- **Frame Drops:** Sensor missed capturing a frame, causing motion discontinuity
- **Frame Merges:** Two distinct frames blended into one by encoder or ISP

### Implementation

#### Detection Pipeline
The analyzer uses OpenCV for computer vision primitives. For each frame triplet `(F_{t-1}, F_t, F_{t+1})`:

1. **Temporal Difference:** Mean absolute pixel difference between consecutive frames
2. **Optical Flow Magnitude:** Farneback dense optical flow to measure motion vectors
3. **SSIM (Structural Similarity Index):** Perceptual similarity between frames
4. **Synthetic Blend Comparison:** Creates `F_synthetic = 0.5 × F_{t-1} + 0.5 × F_{t+1}` and compares to current frame
5. **Edge Density (Canny):** Detects double-edge artifacts from ghosting

#### Classification Algorithm

```kotlin
fun classify(
    meanAbsDiff: Double, motionMag: Double,
    ssimNeighbor: Double, ssimSynthetic: Double, edgeCount: Int,
    motionMean: Double, motionStd: Double,
    ssimMean: Double, ssimStd: Double,
    edgeMean: Double, edgeStd: Double,
    windowSize: Int, fps: Double
): Pair<FrameClass, String> {

    val hasEnoughHistory = windowSize >= 5
    val dupThreshold = fpsScaledDuplicateThreshold(fps)
    val mergeSsimThreshold = fpsScaledMergeSsimThreshold(fps)

    // Rule 1: Duplicate frame detection (masked drop)
    if (meanAbsDiff < dupThreshold) {
        val hasHighSsim = ssimNeighbor > 0.997
        val hasZeroMotion = motionMag < 0.15
        
        if (hasHighSsim || hasZeroMotion) {
            return Pair(FrameClass.FRAME_DROP, "Duplicate frame detected")
        }
    }

    // Rule 2: Motion spike + SSIM drop (true drop)
    val motionThresh = motionMean + 2.5 * motionStd
    val ssimDropThresh = ssimMean - 2.0 * ssimStd
    
    if (hasEnoughHistory && motionMag > motionThresh && ssimNeighbor < ssimDropThresh) {
        return Pair(FrameClass.FRAME_DROP, "Motion spike detected")
    }

    // Rule 3: Blend similarity (merge detection)
    if (ssimSynthetic > mergeSsimThreshold) {
        val edgeThresh = edgeMean + 2.0 * edgeStd
        val hasEdgeEvidence = hasEnoughHistory && edgeCount > edgeThresh
        
        if (hasEdgeEvidence) {
            return Pair(FrameClass.FRAME_MERGE, "Blend + edge ghosting")
        }
        
        return Pair(FrameClass.FRAME_MERGE, "Blend similarity high")
    }

    // Rule 4: Edge spike (ghosting artifacts)
    val edgeThresh = edgeMean + 2.0 * edgeStd
    if (hasEnoughHistory && edgeCount > edgeThresh && ssimSynthetic > 0.80) {
        return Pair(FrameClass.FRAME_MERGE, "Edge ghosting detected")
    }

    return Pair(FrameClass.NORMAL, "Temporal continuity normal")
}
```

#### FPS-Scaled Thresholds

Detection thresholds adapt to frame rate:

```kotlin
fun fpsScaledDuplicateThreshold(fps: Double): Double {
    val ratio = (fps / 30.0).coerceAtLeast(1.0)
    return 1.5 / ln(ratio + 1.0)
}

fun fpsScaledMergeSsimThreshold(fps: Double): Double {
    val ratio = (fps / 30.0).coerceAtLeast(1.0)
    val scale = 1.0 - 1.0 / ratio
    return (0.92 + 0.08 * scale).coerceAtMost(0.995)
}
```

At 240 FPS, consecutive frames are highly similar even with motion, so the thresholds are dynamically adjusted using logarithmic scaling.

#### Why Hybrid Detection?

Pure pixel difference fails when encoders interpolate frames to maintain constant frame rate. Pure SSIM fails with high-motion scenes where legitimate frames have low similarity to neighbors. The hybrid approach using both spatial (SSIM, edges) and temporal (optical flow, synthetic blend) features achieves higher precision.

**Computational Optimization:**
- Frames downscaled to 640×360 before processing
- Gaussian blur applied to reduce sensor noise
- Max 300 frames sampled for long videos
- OpenCV operations run on CPU (Android NDK)

---

## Problem Statement 3: AI Smart Auto Focus & Dynamic Subject Tracking

### A. Android App Side

#### User Flow
The Android app provides a simple redirection mechanism:

```kotlin
class WebViewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val browserIntent = Intent(Intent.ACTION_VIEW, 
            Uri.parse("https://focusntrack.vercel.app/"))
        startActivity(browserIntent)
        finish()
    }
}
```

The app launches the system browser instead of embedding a WebView to ensure access to full WebGPU/WebAssembly capabilities and latest browser security patches.

---

### B. Web Module (Git Submodule)

The web implementation runs entirely in the browser using client-side technologies.

#### Object Detection: MediaPipe Tasks Vision

**Model:** EfficientDet-Lite0 (TFLite)  
**Architecture:** EfficientNet-B0 backbone + BiFPN (Bi-directional Feature Pyramid Network)  
**Execution:** WebAssembly (compiled from TensorFlow Lite)

The model is downloaded once (4.1 MB) and cached. All inference runs locally in the browser.

#### Tracking Algorithm

Standard object detectors don't maintain identity across frames. The tracking layer uses a predictive cost function to match detections to the tracked subject.

**Coast Tracking (Momentum-Based Prediction):**

When the detector loses the subject (occlusion, motion blur), the tracker maintains the bounding box for up to 15 frames by applying a constant-velocity motion model with 0.95 friction factor per frame.

**Color Histogram Matching:**

The tracker computes a 16×16 2D histogram in Hue-Saturation space (ignoring Value to handle lighting changes). An Epanechnikov kernel weights center pixels higher than edge pixels, making the histogram robust to partial occlusions.

```javascript
function calculateHistogram(imageData, box, numBins = 16) {
    const hist = new Float32Array(numBins * numBins);
    const cx = box.x + box.width / 2;
    const cy = box.y + box.height / 2;
    
    for (let y = y1; y < y2; y++) {
        for (let x = x1; x < x2; x++) {
            const [h, s] = rgbToHS(r, g, b);
            
            // Epanechnikov kernel: 1.0 at center, 0.0 at edge
            const dx = (x - cx) / (box.width / 2);
            const dy = (y - cy) / (box.height / 2);
            const distSq = dx*dx + dy*dy;
            if (distSq > 1 || s < 0.1) continue;
            
            const weight = 1.0 - distSq;
            const hBin = floor(h * numBins);
            const sBin = floor(s * numBins);
            hist[hBin * numBins + sBin] += weight;
        }
    }
    return normalize(hist);
}
```

**Cost Function:**

For each candidate detection:

```javascript
cost = 0.4 × normalizedDistance + 0.6 × (1 - colorSimilarity)
```

where `colorSimilarity` is the Bhattacharyya coefficient between histograms. Color is weighted higher (0.6) because it's more discriminative than position for sports tracking.

**Dynamic Search Radius:**  
If `colorSimilarity > 0.85`, the tracker expands the search radius to 80% of screen width, allowing recovery from fast camera pans.

#### Background Blur Rendering: WebGPU

The visual effect is rendered using WebGPU compute shaders. Video frames are uploaded to the GPU as textures, a separable box blur kernel is applied to the background, and a fragment shader composites the sharp focus area with the blurred background using a signed distance field for smooth transitions.

The blur shader uses a 9-tap separable kernel (horizontal + vertical pass) to reduce computational complexity from O(k²) to O(2k) per pixel.

#### Tap-to-Select Subject

User clicks on a detected object to initiate tracking. The system finds the detection bounding box containing the click coordinates and initializes the color histogram for that subject.

#### Dynamic Subject Switching

If the tracked subject leaves the frame or is occluded for more than 15 frames, the tracker resets velocity and scans all detections for the highest color similarity to the last known histogram. If similarity exceeds 0.6, it auto-switches to that detection.

---

## Technical Stack

### Android Application
- **Language:** Kotlin 1.9
- **UI Framework:** Jetpack Compose (Material 3)
- **Camera:** Camera2 API (Android SDK 24+)
- **CV Library:** OpenCV 4.9.0 (Android)
- **Architecture:** MVVM with StateFlow
- **Build System:** Gradle 8.5

### Web Module
- **Detection:** MediaPipe Tasks Vision 0.10.3 (EfficientDet-Lite0)
- **Rendering:** WebGPU (WGSL shaders)
- **Compute:** WebAssembly (TensorFlow Lite)
- **Async Processing:** Web Workers
- **Hosting:** Vercel (static CDN)

---

## Setup Instructions

### Android Application

#### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 24+ (API Level 24)
- Physical Android device (emulators don't support Camera2 high-speed)

#### Build Steps
```bash
# Clone repository with submodules
git clone --recurse-submodules https://github.com/YOUR_USERNAME/Pragyan_Khel_pewdiepie.git
cd Pragyan_Khel_pewdiepie

# Build APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

#### First Launch
1. Grant camera, microphone, and storage permissions when prompted
2. Navigate to the desired feature from the main menu

---

### Web Module (Local Development)

The web module can be run locally for development or testing.

#### Prerequisites
- Modern browser with WebGPU support (Chrome/Edge 113+)
- Node.js or Python for local HTTP server

#### Run Locally

**Option 1: Using Node.js**
```bash
cd focusntrack
npx serve .
# Open http://localhost:3000
```

**Option 2: Using Python**
```bash
cd focusntrack
python -m http.server 8000
# Open http://localhost:8000
```

The web module uses ES modules and Web Workers, which require a proper HTTP server (cannot be opened directly as `file://`).

---

## How to Clone

This repository uses a Git submodule for the web module.

### Clone with Submodules
```bash
git clone --recurse-submodules https://github.com/YOUR_USERNAME/Pragyan_Khel_pewdiepie.git
cd Pragyan_Khel_pewdiepie
```

### Manual Submodule Initialization
If you cloned without `--recurse-submodules`:

```bash
git clone https://github.com/YOUR_USERNAME/Pragyan_Khel_pewdiepie.git
cd Pragyan_Khel_pewdiepie
git submodule init
git submodule update
```

### Updating Submodule
```bash
cd focusntrack
git pull origin main
cd ..
git add focusntrack
git commit -m "Update focusntrack submodule"
```

---

## Project Structure

```
Pragyan_Khel_pewdiepie/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/highspeedcamera/
│   │   │   ├── CameraViewModel.kt            # PS1: Camera2 high-speed recording
│   │   │   ├── CameraScreen.kt               # PS1: Recording UI with manual controls
│   │   │   ├── PlaybackViewModel.kt          # PS1: Video playback state
│   │   │   ├── PlaybackScreen.kt             # PS1: Playback UI with metadata display
│   │   │   ├── VideoAnalyzer.kt              # PS2: Frame classification algorithm
│   │   │   ├── TemporalDetectorViewModel.kt  # PS2: Analysis state management
│   │   │   ├── DropMergeScreen.kt            # PS2: Analysis UI orchestration
│   │   │   ├── WebViewActivity.kt            # PS3: Redirect to web module
│   │   │   ├── MainActivity.kt               # App entry point + navigation
│   │   │   └── ui/screens/
│   │   │       ├── DropMergeHomeScreen.kt    # PS2: Video picker UI
│   │   │       ├── DropMergeAnalysisScreen.kt # PS2: Live analysis progress
│   │   │       └── DropMergeResultsScreen.kt # PS2: Classification results
│   │   └── res/
│   └── build.gradle
├── focusntrack/                              # Git submodule (PS3 implementation)
│   ├── index.html                            # Web app entry point
│   ├── app.js                                # Main application orchestrator
│   ├── mediapipe-tracker.js                  # MediaPipe object detection wrapper
│   ├── tracking-utils.js                     # Cost function + histogram tracking
│   ├── webgpu-renderer.js                    # Background blur shader pipeline
│   ├── worker.js                             # Web Worker for ML inference
│   └── README.md                             # Web module documentation
├── build.gradle
├── settings.gradle
└── .gitmodules
```
