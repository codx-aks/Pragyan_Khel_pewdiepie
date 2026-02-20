package com.example.highspeedcamera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.highspeedcamera.ui.screens.DropMergeAnalysisScreen
import com.example.highspeedcamera.ui.screens.DropMergeHomeScreen
import com.example.highspeedcamera.ui.screens.DropMergeResultsScreen
import org.opencv.android.OpenCVLoader

/**
 * Entry point composable for the Drop/Merge (Temporal Detector) feature.
 * Owns its own NavController for home → analysis → results sub-navigation.
 * [onBack] is called when the user wants to return to the main menu.
 */
@Composable
fun DropMergeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: TemporalDetectorViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    // Initialize OpenCV once
    LaunchedEffect(Unit) {
        if (!OpenCVLoader.initLocal()) {
            Toast.makeText(context, "OpenCV failed to load", Toast.LENGTH_LONG).show()
        }
    }

    // Video picker launcher
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            viewModel.startAnalysis(context, uri)
        }
    }

    // Permission launcher
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pickVideo.launch("video/*")
        else Toast.makeText(context, "Storage permission required to select a video", Toast.LENGTH_LONG).show()
    }

    fun checkPermissionAndPick() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_VIDEO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            pickVideo.launch("video/*")
        } else {
            requestPermission.launch(permission)
        }
    }

    // Navigate sub-screens based on state
    LaunchedEffect(uiState) {
        when (uiState) {
            is DropMergeUiState.Home -> {
                if (navController.currentDestination?.route != "dm_home") {
                    navController.navigate("dm_home") { popUpTo(0) { inclusive = true } }
                }
            }
            is DropMergeUiState.Analyzing -> {
                if (navController.currentDestination?.route != "dm_analysis") {
                    navController.navigate("dm_analysis") { popUpTo("dm_home") }
                }
            }
            is DropMergeUiState.Results -> {
                if (navController.currentDestination?.route != "dm_results") {
                    navController.navigate("dm_results") { popUpTo("dm_home") }
                }
            }
            is DropMergeUiState.Error -> { /* stay on analysis screen */ }
        }
    }

    NavHost(navController = navController, startDestination = "dm_home") {
        composable("dm_home") {
            DropMergeHomeScreen(
                onSelectVideo = { checkPermissionAndPick() },
                onBack = onBack,
            )
        }
        composable("dm_analysis") {
            val state = uiState
            if (state is DropMergeUiState.Analyzing) {
                DropMergeAnalysisScreen(
                    state = state,
                    onCancel = { viewModel.reset() },
                )
            } else if (state is DropMergeUiState.Error) {
                DropMergeAnalysisScreen(
                    state = DropMergeUiState.Analyzing(videoName = "Error: ${(uiState as DropMergeUiState.Error).message}"),
                    onCancel = { viewModel.reset() },
                )
            }
        }
        composable("dm_results") {
            val state = uiState
            if (state is DropMergeUiState.Results) {
                DropMergeResultsScreen(
                    report = state.report,
                    onBack = { viewModel.reset() },
                )
            }
        }
    }
}
