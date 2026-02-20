package com.example.highspeedcamera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.highspeedcamera.databinding.ActivityMainBinding
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "HighSpeedCamera"
        private const val REQUEST_PERMISSIONS = 100
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        // Supported high-speed resolutions in preference order
        val HIGH_SPEED_SIZES = listOf(
            Size(1920, 1080),
            Size(1280, 720),
            Size(640, 480)
        )
    }

    private lateinit var binding: ActivityMainBinding

    // Camera2 objects
    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraConstrainedHighSpeedCaptureSession? = null
    private var mediaRecorder: MediaRecorder? = null

    // Background thread for camera operations
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null

    // State
    private var isRecording = false
    private var selectedFps = 120
    private var selectedSize = Size(1920, 1080)
    private var selectedIso = 800
    private var selectedShutterNs = 4_166_667L  // ~1/240s in nanoseconds
    private var currentVideoPath: String? = null
    private var currentMetadataPath: String? = null

    // Camera capabilities
    private var supportedHighSpeedSizes = mutableListOf<Size>()
    private var supportedFpsRanges = mutableListOf<Range<Int>>()
    private var cameraId = "0"
    private var minIso = 100
    private var maxIso = 6400

    // Recording timer
    private var recordingStartTime = 0L
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRecording) {
                val elapsed = (System.currentTimeMillis() - recordingStartTime) / 1000L
                val minutes = elapsed / 60
                val seconds = elapsed % 60
                binding.tvTimer.text = String.format("%02d:%02d", minutes, seconds)
                binding.tvTimer.postDelayed(this, 500)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager

        if (allPermissionsGranted()) {
            initCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_PERMISSIONS)
        }

        setupUI()
    }

    private fun setupUI() {
        // FPS Spinner
        val fpsOptions = arrayOf("240 FPS", "120 FPS", "60 FPS")
        binding.spinnerFps.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, fpsOptions).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerFps.setSelection(1) // Default 120fps
        binding.spinnerFps.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedFps = when (position) {
                    0 -> 240
                    1 -> 120
                    else -> 60
                }
                // Update recommended shutter speed
                val recommendedShutter = 1_000_000_000L / selectedFps
                selectedShutterNs = recommendedShutter
                binding.seekShutter.progress = shutterNsToProgress(selectedShutterNs)
                updateShutterLabel()
                Log.d(TAG, "FPS selected: $selectedFps")
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Resolution Spinner
        val resOptions = arrayOf("1920 × 1080", "1280 × 720", "640 × 480")
        binding.spinnerResolution.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, resOptions).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerResolution.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedSize = HIGH_SPEED_SIZES[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // ISO SeekBar (100–6400 logarithmic-ish, 10 steps)
        binding.seekIso.max = 100
        binding.seekIso.progress = 30
        binding.seekIso.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                selectedIso = progressToIso(progress)
                binding.tvIsoValue.text = "ISO: $selectedIso"
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
        binding.tvIsoValue.text = "ISO: $selectedIso"

        // Shutter Speed SeekBar (1/30 to 1/4000)
        binding.seekShutter.max = 100
        binding.seekShutter.progress = shutterNsToProgress(selectedShutterNs)
        binding.seekShutter.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                selectedShutterNs = progressToShutterNs(progress)
                updateShutterLabel()
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
        updateShutterLabel()

        // Auto/Manual toggle
        binding.switchManualExposure.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutManualControls.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // Record button
        binding.btnRecord.setOnClickListener {
            if (!isRecording) {
                startHighSpeedRecording()
            } else {
                stopRecording()
            }
        }

        // Playback button
        binding.btnPlayback.setOnClickListener {
            openPlayback()
        }
        binding.btnPlayback.isEnabled = false
    }

    private fun progressToIso(progress: Int): Int {
        // Map 0–100 progress to ISO values: 100, 200, 400, 800, 1600, 3200, 6400
        val isoValues = intArrayOf(100, 200, 400, 800, 1600, 3200, 6400)
        val index = (progress / 100.0 * (isoValues.size - 1)).toInt().coerceIn(0, isoValues.size - 1)
        return isoValues[index]
    }

    private fun progressToShutterNs(progress: Int): Long {
        // 1/30s (33ms) to 1/4000s (0.25ms)
        val minNs = 250_000L      // 1/4000s
        val maxNs = 33_333_333L   // 1/30s
        val fraction = (100 - progress) / 100.0
        return (minNs + fraction * (maxNs - minNs)).toLong()
    }

    private fun shutterNsToProgress(ns: Long): Int {
        val minNs = 250_000L
        val maxNs = 33_333_333L
        val fraction = (ns - minNs).toDouble() / (maxNs - minNs)
        return ((1.0 - fraction) * 100).toInt().coerceIn(0, 100)
    }

    private fun updateShutterLabel() {
        val denominator = (1_000_000_000.0 / selectedShutterNs).toInt()
        binding.tvShutterValue.text = "Shutter: 1/$denominator s"
    }

    // ─── Camera Initialization ───────────────────────────────────────────────

    private fun initCamera() {
        startBackgroundThread()
        findHighSpeedCamera()
    }

    private fun findHighSpeedCamera() {
        for (id in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                ?: continue

            if (capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO)) {
                cameraId = id

                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?: continue

                supportedHighSpeedSizes.clear()
                supportedFpsRanges.clear()

                map.highSpeedVideoSizes?.let { sizes ->
                    supportedHighSpeedSizes.addAll(sizes.toList())
                }
                map.highSpeedVideoFpsRanges?.let { ranges ->
                    supportedFpsRanges.addAll(ranges.toList())
                }

                // ISO range
                characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.let { range ->
                    minIso = range.lower
                    maxIso = range.upper
                }

                runOnUiThread {
                    updateCapabilityInfo()
                }

                Log.d(TAG, "High-speed camera found: $id")
                Log.d(TAG, "High-speed sizes: $supportedHighSpeedSizes")
                Log.d(TAG, "High-speed FPS ranges: $supportedFpsRanges")
                break
            }
        }

        if (supportedHighSpeedSizes.isEmpty()) {
            runOnUiThread {
                binding.tvCameraInfo.text = "⚠ No high-speed camera found.\nDevice may not support 120/240 FPS.\nFalling back to standard recording."
                binding.tvCameraInfo.setTextColor(0xFFFF9800.toInt())
            }
        }

        openCamera()
    }

    private fun updateCapabilityInfo() {
        val fpsInfo = supportedFpsRanges
            .filter { it.upper >= 60 }
            .sortedByDescending { it.upper }
            .take(5)
            .joinToString(", ") { "${it.lower}-${it.upper}" }

        val sizeInfo = supportedHighSpeedSizes
            .sortedByDescending { it.width }
            .joinToString(", ") { "${it.width}×${it.height}" }

        binding.tvCameraInfo.text = "✓ High-speed capable\nFPS: $fpsInfo\nSizes: $sizeInfo"
        binding.tvCameraInfo.setTextColor(0xFF4CAF50.toInt())
    }

    private fun openCamera() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                Log.d(TAG, "Camera opened: $cameraId")
                runOnUiThread {
                    binding.btnRecord.isEnabled = true
                    binding.tvStatus.text = "Ready to record"
                }
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
                cameraDevice = null
            }

            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
                cameraDevice = null
                Log.e(TAG, "Camera error: $error")
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Camera error: $error", Toast.LENGTH_SHORT).show()
                }
            }
        }, backgroundHandler)
    }

    // ─── Recording ───────────────────────────────────────────────────────────

    private fun startHighSpeedRecording() {
        val camera = cameraDevice ?: run {
            Toast.makeText(this, "Camera not ready", Toast.LENGTH_SHORT).show()
            return
        }

        // Find best supported combination
        val targetFps = selectedFps
        val targetSize = selectedSize

        val fpsRange = findBestFpsRange(targetFps)
        val recordingSize = findBestSize(targetSize, fpsRange)

        Log.d(TAG, "Starting recording: size=$recordingSize fps=$fpsRange")

        try {
            setupMediaRecorder(recordingSize, fpsRange.upper)
            val recorderSurface = mediaRecorder!!.surface

            // Create a constrained high-speed capture session
            camera.createConstrainedHighSpeedCaptureSession(
                listOf(recorderSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session as CameraConstrainedHighSpeedCaptureSession
                        startRepeatingBurst(recorderSurface, fpsRange)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "High-speed session config failed – falling back to standard")
                        runOnUiThread {
                            Toast.makeText(this@MainActivity,
                                "High-speed session failed. Trying standard recording.", Toast.LENGTH_LONG).show()
                        }
                        startStandardRecording(camera, recordingSize, targetFps)
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start high-speed recording", e)
            startStandardRecording(camera, recordingSize, targetFps)
        }
    }

    private fun startRepeatingBurst(surface: Surface, fpsRange: Range<Int>) {
        val session = captureSession ?: return
        try {
            val requestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)

                if (binding.switchManualExposure.isChecked) {
                    // Manual exposure mode
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                    set(CaptureRequest.SENSOR_SENSITIVITY, selectedIso)
                    set(CaptureRequest.SENSOR_EXPOSURE_TIME, selectedShutterNs)
                } else {
                    // Auto exposure locked to target FPS
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                }
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            }

            // createHighSpeedRequestList creates the required burst list for constrained HS
            val requests = session.createHighSpeedRequestList(requestBuilder.build())
            session.setRepeatingBurst(requests, null, backgroundHandler)

            mediaRecorder?.start()
            recordingStartTime = System.currentTimeMillis()
            isRecording = true

            runOnUiThread {
                onRecordingStarted(fpsRange.upper, selectedSize)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start burst", e)
        }
    }

    /** Fallback: standard MediaRecorder session when high-speed constraints fail */
    private fun startStandardRecording(camera: CameraDevice, size: Size, fps: Int) {
        try {
            val clampedFps = fps.coerceIn(30, 120)
            setupMediaRecorder(size, clampedFps)
            val recorderSurface = mediaRecorder!!.surface

            camera.createCaptureSession(
                listOf(recorderSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                            addTarget(recorderSurface)
                            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(clampedFps, clampedFps))
                            if (binding.switchManualExposure.isChecked) {
                                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                                set(CaptureRequest.SENSOR_SENSITIVITY, selectedIso)
                                set(CaptureRequest.SENSOR_EXPOSURE_TIME, selectedShutterNs)
                            }
                        }
                        session.setRepeatingRequest(requestBuilder.build(), null, backgroundHandler)
                        mediaRecorder?.start()
                        recordingStartTime = System.currentTimeMillis()
                        isRecording = true
                        runOnUiThread { onRecordingStarted(clampedFps, size) }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        runOnUiThread { Toast.makeText(this@MainActivity, "Recording failed", Toast.LENGTH_SHORT).show() }
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            Log.e(TAG, "Standard recording failed", e)
        }
    }

    private fun setupMediaRecorder(size: Size, fps: Int) {
        val outputFile = createOutputFile()
        currentVideoPath = outputFile.absolutePath

        mediaRecorder = MediaRecorder(this).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setVideoSize(size.width, size.height)
            setVideoFrameRate(fps)
            // High bitrate for high-speed: ~50 Mbps for 1080p 240fps
            setVideoEncodingBitRate(calculateBitrate(size, fps))
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44100)
            setOutputFile(currentVideoPath)

            try {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
                // Since app is locked to portrait (rotation 0), we set hint to sensor orientation straight
                setOrientationHint(sensorOrientation)
            } catch (e: Exception) {
                setOrientationHint(90) // Fallback for most devices in portrait
            }

            prepare()
        }

        Log.d(TAG, "MediaRecorder setup: ${size.width}x${size.height} @ ${fps}fps -> $currentVideoPath")
    }

    private fun calculateBitrate(size: Size, fps: Int): Int {
        val base = when {
            size.width >= 1920 -> 50_000_000
            size.width >= 1280 -> 30_000_000
            else               -> 15_000_000
        }
        return (base * (fps / 120.0)).toInt().coerceAtLeast(10_000_000)
    }

    private fun stopRecording() {
        isRecording = false
        binding.tvTimer.removeCallbacks(timerRunnable)

        try {
            captureSession?.stopRepeating()
            captureSession?.close()
            captureSession = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping capture session", e)
        }

        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            mediaRecorder?.release()
            mediaRecorder = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaRecorder", e)
        }

        // Save metadata sidecar JSON
        saveMetadata()

        // Re-open camera so it's ready for next recording
        openCamera()

        runOnUiThread {
            binding.btnRecord.text = "⏺  RECORD"
            binding.btnRecord.setBackgroundColor(0xFF2196F3.toInt())
            binding.tvStatus.text = "Recording saved ✓"
            binding.recordingIndicator.visibility = View.INVISIBLE
            binding.tvTimer.visibility = View.INVISIBLE
            binding.btnPlayback.isEnabled = true
            binding.btnRecord.isEnabled = false  // wait for camera re-open
            Toast.makeText(this, "Video saved!", Toast.LENGTH_LONG).show()
        }
    }

    private fun onRecordingStarted(fps: Int, size: Size) {
        binding.btnRecord.text = "⏹  STOP"
        binding.btnRecord.setBackgroundColor(0xFFf44336.toInt())
        binding.tvStatus.text = "Recording @ ${fps}fps  ${size.width}×${size.height}"
        binding.recordingIndicator.visibility = View.VISIBLE
        binding.tvTimer.visibility = View.VISIBLE
        binding.tvTimer.text = "00:00"
        binding.tvTimer.post(timerRunnable)
    }

    // ─── Metadata ────────────────────────────────────────────────────────────

    private fun saveMetadata() {
        val videoPath = currentVideoPath ?: return
        val metaFile = File(videoPath.replace(".mp4", "_meta.json"))
        currentMetadataPath = metaFile.absolutePath

        val denominator = (1_000_000_000.0 / selectedShutterNs).toInt()
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(recordingStartTime))

        val json = JSONObject().apply {
            put("fps", selectedFps)
            put("resolution", "${selectedSize.width}x${selectedSize.height}")
            put("iso", selectedIso)
            put("shutter_speed", "1/$denominator")
            put("shutter_speed_ns", selectedShutterNs)
            put("manual_exposure", binding.switchManualExposure.isChecked)
            put("timestamp_utc", timestamp)
            put("timestamp_epoch_ms", recordingStartTime)
            put("video_file", File(videoPath).name)
            put("camera_id", cameraId)
            put("duration_seconds", (System.currentTimeMillis() - recordingStartTime) / 1000.0)
        }

        try {
            FileWriter(metaFile).use { it.write(json.toString(2)) }
            Log.d(TAG, "Metadata saved: ${metaFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save metadata", e)
        }
    }

    // ─── Helper Methods ───────────────────────────────────────────────────────

    private fun findBestFpsRange(targetFps: Int): Range<Int> {
        if (supportedFpsRanges.isEmpty()) {
            return Range(targetFps, targetFps)
        }
        // Prefer exact match for upper bound, then closest
        return supportedFpsRanges
            .filter { it.upper >= targetFps / 2 }
            .minByOrNull { Math.abs(it.upper - targetFps) }
            ?: Range(targetFps, targetFps)
    }

    private fun findBestSize(target: Size, fpsRange: Range<Int>): Size {
        if (supportedHighSpeedSizes.isEmpty()) return target

        // Find sizes that support the fps range
        val compatibleSizes = supportedHighSpeedSizes.filter { size ->
            supportedFpsRanges.any { range ->
                range.upper >= fpsRange.upper &&
                        cameraManager.getCameraCharacteristics(cameraId)
                            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                            ?.highSpeedVideoSizes?.contains(size) == true
            }
        }

        val pool = if (compatibleSizes.isEmpty()) supportedHighSpeedSizes else compatibleSizes

        // Find closest to target
        return pool.minByOrNull {
            Math.abs(it.width - target.width) + Math.abs(it.height - target.height)
        } ?: target
    }

    private fun createOutputFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "HighSpeedCam")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "HSC_${timeStamp}_${selectedFps}fps.mp4")
    }

    private fun openPlayback() {
        val path = currentVideoPath ?: run {
            Toast.makeText(this, "No video recorded yet", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, PlaybackActivity::class.java).apply {
            putExtra(PlaybackActivity.EXTRA_VIDEO_PATH, path)
            putExtra(PlaybackActivity.EXTRA_META_PATH, currentMetadataPath)
        }
        startActivity(intent)
    }

    // ─── Background Thread ────────────────────────────────────────────────────

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping background thread", e)
        }
    }

    // ─── Permissions ──────────────────────────────────────────────────────────

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS && allPermissionsGranted()) {
            initCamera()
        } else {
            Toast.makeText(this, "Camera and microphone permissions required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        if (allPermissionsGranted() && cameraDevice == null) {
            openCamera()
        }
    }

    override fun onPause() {
        if (isRecording) stopRecording()
        cameraDevice?.close()
        cameraDevice = null
        stopBackgroundThread()
        super.onPause()
    }
}
