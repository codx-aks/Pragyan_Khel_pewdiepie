package com.example.highspeedcamera

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
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
import androidx.core.content.ContextCompat
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

    // region variables
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

    private val _selectedIso = MutableStateFlow(100)
    val selectedIso = _selectedIso.asStateFlow()

    private val _selectedShutterNs = MutableStateFlow(16_666_667L) // ~1/60s – reasonable default to avoid grain
    val selectedShutterNs = _selectedShutterNs.asStateFlow()

    private val _isManualExposure = MutableStateFlow(false)
    val isManualExposure = _isManualExposure.asStateFlow()

    private val _statusMessage = MutableStateFlow("Initializing...")
    val statusMessage = _statusMessage.asStateFlow()

    private val _permissionDenied = MutableStateFlow(false)
    val permissionDenied = _permissionDenied.asStateFlow()

    private val _unsupportedFeatureMessage = MutableStateFlow<String?>(null)
    val unsupportedFeatureMessage = _unsupportedFeatureMessage.asStateFlow()

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
    private var recordingStartTime = 0L

    // Device capability flags — set once in findHighSpeedCamera()
    private var isHighSpeedCapable = false
    private var isManualSensorCapable = false

    private val _isManualExposureSupported = MutableStateFlow(true)
    val isManualExposureSupported: StateFlow<Boolean> = _isManualExposureSupported.asStateFlow()

    private val _availableFpsOptions = MutableStateFlow<List<Int>>(emptyList())
    val availableFpsOptions = _availableFpsOptions.asStateFlow()

    private val _supportedFpsSet = MutableStateFlow<Set<Int>>(emptySet())
    val supportedFpsSet = _supportedFpsSet.asStateFlow()

    private val _allFpsOptions = MutableStateFlow<List<Int>>(listOf(30, 60, 120, 240))
    val allFpsOptions = _allFpsOptions.asStateFlow()

    private val _availableSizeOptions = MutableStateFlow<List<Size>>(emptyList())
    val availableSizeOptions = _availableSizeOptions.asStateFlow()

    private val _supportedSizeSet = MutableStateFlow<Set<Size>>(emptySet())
    val supportedSizeSet = _supportedSizeSet.asStateFlow()

    private val _allSizeOptions = MutableStateFlow<List<Size>>(emptyList())
    val allSizeOptions = _allSizeOptions.asStateFlow()

    private val _isoRange = MutableStateFlow<Range<Int>>(Range(100, 6400))
    val isoRange = _isoRange.asStateFlow()

    private val _shutterRangeNs = MutableStateFlow<Range<Long>>(Range(250_000L, 33_333_333L))
    val shutterRangeNs = _shutterRangeNs.asStateFlow()

    // Noise reduction
    private val _selectedNoiseReductionMode = MutableStateFlow(CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
    val selectedNoiseReductionMode = _selectedNoiseReductionMode.asStateFlow()

    private val _availableNoiseReductionModes = MutableStateFlow<List<Int>>(listOf(
        CaptureRequest.NOISE_REDUCTION_MODE_OFF,
        CaptureRequest.NOISE_REDUCTION_MODE_FAST,
        CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY
    ))
    val availableNoiseReductionModes = _availableNoiseReductionModes.asStateFlow()

    // endregion

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
                foundHs = true
                Log.d(TAG, "High-speed camera found: $id")
                break
            }
        }

        val characteristics = try {
            cameraManager.getCameraCharacteristics(cameraId)
        } catch (e: Exception) {
            _unsupportedFeatureMessage.value = "Failed to access camera: ${e.message}"
            openCamera()
            return
        }
        
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        supportedHighSpeedSizes.clear()
        supportedFpsRanges.clear()
        
        if (foundHs && map != null) {
            map.highSpeedVideoSizes?.let { sizes -> supportedHighSpeedSizes.addAll(sizes.toList()) }
            map.highSpeedVideoFpsRanges?.let { ranges -> supportedFpsRanges.addAll(ranges.toList()) }
            
            val fpsList = supportedFpsRanges.map { it.upper }.distinct().sortedDescending()
            _availableFpsOptions.value = if (fpsList.isNotEmpty()) fpsList else listOf(60)
            
            val sizeList = supportedHighSpeedSizes.distinct().sortedByDescending { it.width * it.height }
            _availableSizeOptions.value = if (sizeList.isNotEmpty()) sizeList else listOf(Size(1920, 1080))
            
            updateCapabilityInfo()
        } else {
            _cameraInfo.value = "⚠ No high-speed camera found.\nDevice may not support 120/240 FPS."
            _unsupportedFeatureMessage.value = "High-speed features not supported on this device. Using standard limits."
            
            map?.getOutputSizes(MediaRecorder::class.java)?.let { sizes ->
                _availableSizeOptions.value = sizes.toList().sortedByDescending { it.width * it.height }.take(5)
            }
            if (_availableSizeOptions.value.isEmpty()) {
                 _availableSizeOptions.value = listOf(Size(1920, 1080))
            }

            val fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            if (fpsRanges != null) {
                val fpsList = fpsRanges.map { it.upper }.distinct().sortedDescending()
                _availableFpsOptions.value = if (fpsList.isNotEmpty()) fpsList else listOf(30)
            } else {
                _availableFpsOptions.value = listOf(30)
            }
        }

        characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.let { range ->
            _isoRange.value = range
            if (_selectedIso.value !in range.lower..range.upper) {
                _selectedIso.value = range.lower
            }
        }

        characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.let { range ->
            _shutterRangeNs.value = range
            if (_selectedShutterNs.value !in range.lower..range.upper) {
                _selectedShutterNs.value = range.upper.coerceAtMost(1_000_000_000L / 30) // Fallback to 30fps shutter approx
            }
        }

        // Query available noise reduction modes from the device
        characteristics.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)?.let { modes ->
            _availableNoiseReductionModes.value = modes.toList()
            if (_selectedNoiseReductionMode.value !in modes) {
                _selectedNoiseReductionMode.value = modes.lastOrNull() ?: CaptureRequest.NOISE_REDUCTION_MODE_OFF
            }
        }

        // Store capability flags for use in recording paths
        isHighSpeedCapable = foundHs
        val allCapabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
        isManualSensorCapable = allCapabilities.contains(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
        )
        _isManualExposureSupported.value = isManualSensorCapable

        // Build the supported sets from what the device actually supports
        _supportedFpsSet.value = _availableFpsOptions.value.toSet()
        _supportedSizeSet.value = _availableSizeOptions.value.toSet()

        // Build "all" FPS list: standard options + any device-specific ones, sorted descending
        val standardFps = listOf(30, 60, 120, 240)
        val allFps = (standardFps + _availableFpsOptions.value).distinct().sortedDescending()
        _allFpsOptions.value = allFps

        // Build "all" size list: common resolutions + device-specific ones, sorted by pixel count descending
        val commonSizes = listOf(
            Size(3840, 2160),
            Size(1920, 1080),
            Size(1280, 720),
            Size(640, 480)
        )
        val allSizes = (commonSizes + _availableSizeOptions.value)
            .distinctBy { "${it.width}x${it.height}" }
            .sortedByDescending { it.width * it.height }
        _allSizeOptions.value = allSizes

        if (_selectedFps.value !in _supportedFpsSet.value) {
            _selectedFps.value = _availableFpsOptions.value.firstOrNull() ?: 30
        }
        if (_selectedSize.value !in _supportedSizeSet.value) {
            _selectedSize.value = _availableSizeOptions.value.firstOrNull() ?: Size(1920, 1080)
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

    fun openCamera() {
        val hasPermission = ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            _permissionDenied.value = true
            _statusMessage.value = "Camera permission denied"
            return
        }
        
        _permissionDenied.value = false

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
    
    fun onPermissionDeniedDismissed() {
        _permissionDenied.value = false
    }

    fun dismissUnsupportedMessage() {
        _unsupportedFeatureMessage.value = null
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

        // Device doesn't support constrained high-speed — skip straight to standard recording
        // with the highest FPS range the device actually advertises.
        if (!isHighSpeedCapable) {
            val achievableRange = findBestStandardFpsRange(targetFps)
            startStandardRecording(camera, targetSize, achievableRange.upper)
            return
        }

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
                        // Release the recorder created for the failed high-speed session
                        // before startStandardRecording creates a new one.
                        try { mediaRecorder?.reset(); mediaRecorder?.release(); mediaRecorder = null } catch (_: Exception) {}
                        startStandardRecording(camera, recordingSize, targetFps)
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start high-speed recording", e)
            try { mediaRecorder?.reset(); mediaRecorder?.release(); mediaRecorder = null } catch (_: Exception) {}
            startStandardRecording(camera, recordingSize, targetFps)
        }
    }

    private fun startRepeatingBurst(surface: Surface, fpsRange: Range<Int>) {
        val session = captureSession ?: return
        try {
            val requestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)


                if (_isManualExposure.value && isManualSensorCapable) {
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                    set(CaptureRequest.SENSOR_SENSITIVITY, _selectedIso.value)
                    val maxShutterForFps = 1_000_000_000L / fpsRange.upper
                    set(CaptureRequest.SENSOR_EXPOSURE_TIME,
                        _selectedShutterNs.value.coerceAtMost(maxShutterForFps))
                } else {
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                }

                set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                set(CaptureRequest.CONTROL_AWB_LOCK, false)
                set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE,
                    CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_FAST)

                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                set(CaptureRequest.NOISE_REDUCTION_MODE, _selectedNoiseReductionMode.value)
                set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_FAST)
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

            val bestFpsRange = findBestStandardFpsRange(fps)
            val actualFps = bestFpsRange.upper

            setupMediaRecorder(size, actualFps)
            val recorderSurface = mediaRecorder!!.surface

            camera.createCaptureSession(
                listOf(recorderSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                            addTarget(recorderSurface)
                            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, bestFpsRange)
                            if (_isManualExposure.value && isManualSensorCapable) {
                                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                                set(CaptureRequest.SENSOR_SENSITIVITY, _selectedIso.value)
                                val maxShutterForFps = 1_000_000_000L / actualFps
                                set(CaptureRequest.SENSOR_EXPOSURE_TIME,
                                    _selectedShutterNs.value.coerceAtMost(maxShutterForFps))
                            } else {
                                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                            }
                            set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                            set(CaptureRequest.CONTROL_AWB_LOCK, false)
                            set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE,
                                CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_FAST)
                            set(CaptureRequest.NOISE_REDUCTION_MODE, _selectedNoiseReductionMode.value)
                            set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_FAST)
                        }
                        session.setRepeatingRequest(requestBuilder.build(), null, backgroundHandler)
                        mediaRecorder?.start()
                        recordingStartTime = System.currentTimeMillis()
                        _isRecording.value = true
                        onRecordingStarted(actualFps, size)
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
            setVideoEncoder(MediaRecorder.VideoEncoder.HEVC)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setVideoSize(size.width, size.height)
            setVideoFrameRate(fps)
            setVideoEncodingBitRate(calculateBitrate(size, fps))
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44100)

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

    /**
     * Finds the best AE FPS range the device actually supports for a standard CaptureSession.
     * Prefers fixed ranges (min == max) at or below [targetFps], picking the highest.
     * This prevents requesting Range(120,120) on a device that maxes out at 30fps.
     */
    private fun findBestStandardFpsRange(targetFps: Int): Range<Int> {
        return try {
            val ranges = cameraManager.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                ?: return Range(30, 30)
            // Fixed-rate range at or below target — gives the smoothest recording
            val fixed = ranges.filter { it.lower == it.upper && it.upper <= targetFps }
            if (fixed.isNotEmpty()) return fixed.maxByOrNull { it.upper }!!
            // Fallback: variable range with highest upper bound
            ranges.maxByOrNull { it.upper } ?: Range(30, 30)
        } catch (e: Exception) {
            Range(30, 30)
        }
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
        // Shutter can't be longer than the frame interval (1/fps).
        // Clamp only if current value exceeds the max allowed for this FPS.
        val maxShutterForFps = 1_000_000_000L / fps
        if (_selectedShutterNs.value > maxShutterForFps) {
            _selectedShutterNs.value = maxShutterForFps
        }
    }
    fun setSize(size: Size) { _selectedSize.value = size }
    fun setManualExposure(manual: Boolean) { _isManualExposure.value = manual }
    fun setIso(iso: Int) { _selectedIso.value = iso }
    fun setShutterNs(ns: Long) { _selectedShutterNs.value = ns }
    fun setNoiseReductionMode(mode: Int) { _selectedNoiseReductionMode.value = mode }

    override fun onCleared() {
        if (_isRecording.value) stopRecording()
        cameraDevice?.close()
        cameraDevice = null
        stopBackgroundThread()
        super.onCleared()
    }
}
