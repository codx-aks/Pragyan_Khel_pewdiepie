package com.example.highspeedcamera

import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

class PlaybackViewModel : ViewModel() {

    private var mediaPlayer: MediaPlayer? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0)
    val currentPosition = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0)
    val duration = _duration.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _videoName = MutableStateFlow("")
    val videoName = _videoName.asStateFlow()

    private val _metadata = MutableStateFlow<Map<String, String>>(emptyMap())
    val metadata = _metadata.asStateFlow()

    fun loadVideo(videoPath: String, metaPath: String?) {
        _videoName.value = File(videoPath).name
        if (metaPath != null) {
            loadMetadata(metaPath)
        }
    }

    fun onMediaPlayerReady(mp: MediaPlayer) {
        mediaPlayer = mp
        _duration.value = mp.duration
        _isLoading.value = false
        
        mp.setOnCompletionListener {
            _isPlaying.value = false
        }
        
        startProgressTracker()
    }

    private fun loadMetadata(metaPath: String) {
        val metaFile = File(metaPath)
        if (!metaFile.exists()) return

        try {
            val json = JSONObject(metaFile.readText())
            val map = mapOf(
                "FPS" to "${json.optInt("fps", 0)} FPS",
                "Resolution" to json.optString("resolution", "—"),
                "ISO" to "ISO ${json.optInt("iso", 0)}",
                "Shutter" to json.optString("shutter_speed", "—"),
                "Timestamp" to json.optString("timestamp_utc", "—").replace("T", " ").replace("Z", " UTC"),
                "Duration" to String.format("%.1f s", json.optDouble("duration_seconds", 0.0)),
                "Exposure" to if (json.optBoolean("manual_exposure", false)) "Manual" else "Auto"
            )
            _metadata.value = map
        } catch (e: Exception) {
            Log.e("PlaybackViewModel", "Failed to parse metadata", e)
        }
    }

    fun togglePlayPause() {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            mp.pause()
            _isPlaying.value = false
        } else {
            mp.start()
            _isPlaying.value = true
        }
    }

    fun seekTo(progressPercentage: Float) {
        val mp = mediaPlayer ?: return
        val position = (mp.duration * progressPercentage).toInt()
        mp.seekTo(position)
        _currentPosition.value = position
    }

    fun restart() {
        mediaPlayer?.seekTo(0)
        mediaPlayer?.start()
        _isPlaying.value = true
    }

    private fun startProgressTracker() {
        viewModelScope.launch {
            while (true) {
                if (_isPlaying.value) {
                    mediaPlayer?.let { _currentPosition.value = it.currentPosition }
                }
                delay(200)
            }
        }
    }

    override fun onCleared() {
        mediaPlayer?.release()
        mediaPlayer = null
        super.onCleared()
    }
}
