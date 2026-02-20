package com.example.highspeedcamera

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ─── UI State ─────────────────────────────────────────────────────────────────

sealed class DropMergeUiState {
    data object Home : DropMergeUiState()

    data class Analyzing(
        val videoName: String = "",
        val processed: Int = 0,
        val total: Int = 0,
        val normalCount: Int = 0,
        val dropCount: Int = 0,
        val mergeCount: Int = 0,
        val currentClass: FrameClass = FrameClass.NORMAL,
    ) : DropMergeUiState()

    data class Results(val report: AnalysisReport) : DropMergeUiState()

    data class Error(val message: String) : DropMergeUiState()
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

class TemporalDetectorViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<DropMergeUiState>(DropMergeUiState.Home)
    val uiState: StateFlow<DropMergeUiState> = _uiState.asStateFlow()

    fun startAnalysis(context: Context, uri: Uri) {
        val videoName = uri.lastPathSegment ?: uri.toString()

        _uiState.value = DropMergeUiState.Analyzing(videoName = videoName)

        val analyzer = VideoAnalyzer(context.applicationContext)

        viewModelScope.launch(Dispatchers.IO) {
            analyzer.analyze(uri, object : AnalysisProgressCallback {

                override fun onProgress(processed: Int, total: Int, currentClass: FrameClass) {
                    val current = _uiState.value
                    if (current is DropMergeUiState.Analyzing) {
                        val (normal, drop, merge) = when (currentClass) {
                            FrameClass.NORMAL -> Triple(
                                current.normalCount + 1, current.dropCount, current.mergeCount
                            )
                            FrameClass.FRAME_DROP -> Triple(
                                current.normalCount, current.dropCount + 1, current.mergeCount
                            )
                            FrameClass.FRAME_MERGE -> Triple(
                                current.normalCount, current.dropCount, current.mergeCount + 1
                            )
                        }
                        _uiState.value = current.copy(
                            processed = processed,
                            total = total,
                            normalCount = normal,
                            dropCount = drop,
                            mergeCount = merge,
                            currentClass = currentClass,
                        )
                    }
                }

                override fun onComplete(report: AnalysisReport) {
                    _uiState.value = DropMergeUiState.Results(report)
                }

                override fun onError(message: String) {
                    _uiState.value = DropMergeUiState.Error(message)
                }
            })
        }
    }

    fun reset() {
        _uiState.value = DropMergeUiState.Home
    }
}
