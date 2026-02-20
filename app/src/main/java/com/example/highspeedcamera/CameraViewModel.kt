package com.example.highspeedcamera

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "CameraViewModel"

    private val cameraManager = application.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraConstrainedHighSpeedCaptureSession? = null
    private var mediaRecorder: MediaRecorder? = null

    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null

    // UI States
    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _selectedFps = MutableStateFlow(120)
    val selectedFps = _selectedFps.asStateFlow()

    private val _selectedSize = MutableStateFlow(Size(1920, 1080))
    val selectedSize = _selectedSize.asStateFlow()

    private val _selectedIso = MutableStateFlow(800)
    val selectedIso = _selectedIso.asStateFlow()

    private val _selectedShutterNs = MutableStateFlow(4_166_667L)
    val selectedShutterNs = _selectedShutterNs.asStateFlow()

    private val _isManualExposure = MutableStateFlow(false)
    val isManualExposure = _isManualExposure.asStateFlow()

    private val _statusMessage = MutableStateFlow("Initializing...")
    val statusMessage = _statusMessage.asStateFlow()

    private val _cameraInfo = MutableStateFlow("Detecting camera...")
    val cameraInfo = _cameraInfo.asStateFlow()

    private val _recordingTime = MutableStateFlow("00:00")
    val recordingTime = _recordingTime.asStateFlow()

    private val _lastVideoPath = MutableStateFlow<String?>(null)
    val lastVideoPath = _lastVideoPath.asStateFlow()

    private val _lastMetaPath = MutableStateFlow<String?>(null)
    val lastMetaPath = _lastMetaPath.asStateFlow()

    // Video output URI (used for MediaStore on API 29+)
    private var lastVideoUri: Uri? = null
    private var lastVideoFd: java.io.FileDescriptor? = null

    // Camera Capabilities
    private var supportedHighSpeedSizes = mutableListOf<Size>()
    private var supportedFpsRanges = mutableListOf<Range<Int>>()
    private var cameraId = "0"
    private var minIso = 100
    private var maxIso = 6400
    private var recordingStartTime = 0L

    val HIGH_SPEED_SIZES = listOf(
        Size(1920, 1080),
        Size(1280, 720),
        Size(640, 480)
    )
    val FPS_OPTIONS = listOf(240, 120, 60)

    fun initializeCamera() {
        startBackgroundThread()
        findHighSpeedCamera()
    }

    private fun findHighSpeedCamera() {
        var foundHs = false
        for (id in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                ?: continue

            if (capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO)) {
                cameraId = id
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: continue

                supportedHighSpeedSizes.clear()
                supportedFpsRanges.clear()
                map.highSpeedVideoSizes?.let { sizes -> supportedHighSpeedSizes.addAll(sizes.toList()) }
                map.highSpeedVideoFpsRanges?.let { ranges -> supportedFpsRanges.addAll(ranges.toList()) }

                characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.let { range ->
                    minIso = range.lower
                    maxIso = range.upper
                }

                updateCapabilityInfo()
                foundHs = true
                Log.d(TAG, "High-speed camera found: $id")
                break
            }
        }

        if (!foundHs) {
            _cameraInfo.value = "⚠ No high-speed camera found.\nDevice may not support 120/240 FPS."
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

        _cameraInfo.value = "✓ High-speed capable\nFPS: $fpsInfo\nSizes: $sizeInfo"
    }

    @SuppressLint("MissingPermission")
    fun openCamera() {
        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    _statusMessage.value = "Ready to record"
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    _statusMessage.value = "Camera error: $error"
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open camera", e)
            _statusMessage.value = "Cannot open camera"
        }
    }

    fun toggleRecording() {
        if (_isRecording.value) {
            stopRecording()
        } else {
            startHighSpeedRecording()
        }
    }

    private fun startHighSpeedRecording() {
        val camera = cameraDevice ?: return

        val targetFps = _selectedFps.value
        val targetSize = _selectedSize.value

        val fpsRange = findBestFpsRange(targetFps)
        val recordingSize = findBestSize(targetSize, fpsRange)

        try {
            setupMediaRecorder(recordingSize, fpsRange.upper)
            val recorderSurface = mediaRecorder!!.surface

            camera.createConstrainedHighSpeedCaptureSession(
                listOf(recorderSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session as CameraConstrainedHighSpeedCaptureSession
                        startRepeatingBurst(recorderSurface, fpsRange)
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "High-speed session config failed – falling back")
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

                if (_isManualExposure.value) {
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                    set(CaptureRequest.SENSOR_SENSITIVITY, _selectedIso.value)
                    set(CaptureRequest.SENSOR_EXPOSURE_TIME, _selectedShutterNs.value)
                } else {
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                }
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            }

            val requests = session.createHighSpeedRequestList(requestBuilder.build())
            session.setRepeatingBurst(requests, null, backgroundHandler)

            mediaRecorder?.start()
            recordingStartTime = System.currentTimeMillis()
            _isRecording.value = true
            onRecordingStarted(fpsRange.upper, _selectedSize.value)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start burst", e)
            _statusMessage.value = "Burst failed"
        }
    }

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
                            if (_isManualExposure.value) {
                                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                                set(CaptureRequest.SENSOR_SENSITIVITY, _selectedIso.value)
                                set(CaptureRequest.SENSOR_EXPOSURE_TIME, _selectedShutterNs.value)
                            }
                        }
                        session.setRepeatingRequest(requestBuilder.build(), null, backgroundHandler)
                        mediaRecorder?.start()
                        recordingStartTime = System.currentTimeMillis()
                        _isRecording.value = true
                        onRecordingStarted(clampedFps, size)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        _statusMessage.value = "Standard recording failed"
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
        _lastVideoPath.value = outputFile.absolutePath

        mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(getApplication())
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setVideoSize(size.width, size.height)
            setVideoFrameRate(fps)
            setVideoEncodingBitRate(calculateBitrate(size, fps))
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44100)

            // Use file descriptor for MediaStore (API 29+), otherwise use file path
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && lastVideoFd != null) {
                setOutputFile(lastVideoFd)
            } else {
                setOutputFile(_lastVideoPath.value)
            }

            try {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
                setOrientationHint(sensorOrientation)
            } catch (e: Exception) {
                setOrientationHint(90)
            }

            prepare()
        }
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
        _isRecording.value = false

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

        // Finalize MediaStore entry on API 29+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && lastVideoUri != null) {
            try {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                getApplication<Application>().contentResolver.update(lastVideoUri!!, contentValues, null, null)
            } catch (e: Exception) {
                Log.e(TAG, "Error finalizing MediaStore entry", e)
            }
        }

        saveMetadata()
        openCamera()

        _statusMessage.value = "Recording saved to Downloads/HighSpeedCam ✓"
    }

    private fun onRecordingStarted(fps: Int, size: Size) {
        _statusMessage.value = "Recording @ ${fps}fps  ${size.width}×${size.height}"
        viewModelScope.launch {
            while (_isRecording.value) {
                val elapsed = (System.currentTimeMillis() - recordingStartTime) / 1000L
                val minutes = elapsed / 60
                val seconds = elapsed % 60
                _recordingTime.value = String.format("%02d:%02d", minutes, seconds)
                delay(500)
            }
        }
    }

    private fun saveMetadata() {
        val videoPath = _lastVideoPath.value ?: return

        val denominator = (1_000_000_000.0 / _selectedShutterNs.value).toInt()
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(recordingStartTime))

        val json = JSONObject().apply {
            put("fps", _selectedFps.value)
            put("resolution", "${_selectedSize.value.width}x${_selectedSize.value.height}")
            put("iso", _selectedIso.value)
            put("shutter_speed", "1/$denominator")
            put("shutter_speed_ns", _selectedShutterNs.value)
            put("manual_exposure", _isManualExposure.value)
            put("timestamp_utc", timestamp)
            put("timestamp_epoch_ms", recordingStartTime)
            put("video_file", File(videoPath).name)
            put("camera_id", cameraId)
            put("duration_seconds", (System.currentTimeMillis() - recordingStartTime) / 1000.0)
        }

        val metaFileName = File(videoPath).name.replace(".mp4", "_meta.json")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore to save metadata JSON in Downloads/HighSpeedCam
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, metaFileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/HighSpeedCam")
                }
                val resolver = getApplication<Application>().contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        OutputStreamWriter(outputStream).use { writer ->
                            writer.write(json.toString(2))
                        }
                    }
                    // Update the meta path to the logical path
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    _lastMetaPath.value = File(File(downloadsDir, "HighSpeedCam"), metaFileName).absolutePath
                }
            } else {
                // Direct file access for API < 29
                val metaFile = File(File(videoPath).parent, metaFileName)
                _lastMetaPath.value = metaFile.absolutePath
                FileWriter(metaFile).use { it.write(json.toString(2)) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save metadata", e)
        }
    }

    private fun findBestFpsRange(targetFps: Int): Range<Int> {
        if (supportedFpsRanges.isEmpty()) return Range(targetFps, targetFps)
        return supportedFpsRanges
            .filter { it.upper >= targetFps / 2 }
            .minByOrNull { Math.abs(it.upper - targetFps) }
            ?: Range(targetFps, targetFps)
    }

    private fun findBestSize(target: Size, fpsRange: Range<Int>): Size {
        if (supportedHighSpeedSizes.isEmpty()) return target
        val compatibleSizes = supportedHighSpeedSizes.filter { size ->
            supportedFpsRanges.any { range ->
                range.upper >= fpsRange.upper &&
                        cameraManager.getCameraCharacteristics(cameraId)
                            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                            ?.highSpeedVideoSizes?.contains(size) == true
            }
        }
        val pool = if (compatibleSizes.isEmpty()) supportedHighSpeedSizes else compatibleSizes
        return pool.minByOrNull {
            Math.abs(it.width - target.width) + Math.abs(it.height - target.height)
        } ?: target
    }

    private fun createOutputFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "HSC_${timeStamp}_${_selectedFps.value}fps.mp4"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // API 29+: Use MediaStore to save into Downloads/HighSpeedCam
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/HighSpeedCam")
            }
            val resolver = getApplication<Application>().contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            lastVideoUri = uri

            // MediaRecorder needs a file path or FileDescriptor, so we get the FD from the URI
            if (uri != null) {
                val pfd = resolver.openFileDescriptor(uri, "rw")
                if (pfd != null) {
                    lastVideoFd = pfd.fileDescriptor
                    // We still need a File object for the path reference
                    // Store the logical path for display/metadata
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val dir = File(downloadsDir, "HighSpeedCam")
                    return File(dir, fileName) // logical path reference
                }
            }
            // Fallback if MediaStore fails
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "HighSpeedCam")
            if (!dir.exists()) dir.mkdirs()
            return File(dir, fileName)
        } else {
            // API < 29: Direct file access in Downloads/HighSpeedCam
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "HighSpeedCam")
            if (!dir.exists()) dir.mkdirs()
            return File(dir, fileName)
        }
    }

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("CameraBackground").also { it.start() }
            backgroundHandler = Handler(backgroundThread!!.looper)
        }
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

    // Setters
    fun setFps(fps: Int) {
        _selectedFps.value = fps
        _selectedShutterNs.value = 1_000_000_000L / fps
    }
    fun setSize(size: Size) { _selectedSize.value = size }
    fun setManualExposure(manual: Boolean) { _isManualExposure.value = manual }
    fun setIso(iso: Int) { _selectedIso.value = iso }
    fun setShutterNs(ns: Long) { _selectedShutterNs.value = ns }

    override fun onCleared() {
        if (_isRecording.value) stopRecording()
        cameraDevice?.close()
        cameraDevice = null
        stopBackgroundThread()
        super.onCleared()
    }
}
