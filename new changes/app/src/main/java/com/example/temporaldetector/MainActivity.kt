package com.example.temporaldetector

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.temporaldetector.ui.screens.AnalysisScreen
import com.example.temporaldetector.ui.screens.HomeScreen
import com.example.temporaldetector.ui.screens.ResultsScreen
import com.example.temporaldetector.ui.theme.TemporalDetectorTheme
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {

    private val viewModel: TemporalDetectorViewModel by viewModels()

    private val pickVideo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            viewModel.startAnalysis(this, uri)
        }
    }

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pickVideo.launch("video/*")
        else Toast.makeText(this, "Storage permission required to select a video", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize OpenCV
        if (!OpenCVLoader.initLocal()) {
            Toast.makeText(this, "OpenCV failed to load", Toast.LENGTH_LONG).show()
        }

        setContent {
            TemporalDetectorTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val navController = rememberNavController()

                // React to state changes → navigate
                LaunchedEffect(uiState) {
                    when (uiState) {
                        is UiState.Home -> {
                            if (navController.currentDestination?.route != "home") {
                                navController.navigate("home") {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        }
                        is UiState.Analyzing -> {
                            if (navController.currentDestination?.route != "analysis") {
                                navController.navigate("analysis") {
                                    popUpTo("home")
                                }
                            }
                        }
                        is UiState.Results -> {
                            if (navController.currentDestination?.route != "results") {
                                navController.navigate("results") {
                                    popUpTo("home")
                                }
                            }
                        }
                        is UiState.Error -> {
                            // Stay on analysis screen; error is shown there or navigate back
                        }
                    }
                }

                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            onSelectVideo = { checkPermissionAndPick() },
                        )
                    }
                    composable("analysis") {
                        val state = uiState
                        if (state is UiState.Analyzing) {
                            AnalysisScreen(
                                state = state,
                                onCancel = { viewModel.reset() },
                            )
                        } else if (state is UiState.Error) {
                            // Show error then allow going back
                            AnalysisScreen(
                                state = UiState.Analyzing(videoName = "Error"),
                                onCancel = { viewModel.reset() },
                            )
                        }
                    }
                    composable("results") {
                        val state = uiState
                        if (state is UiState.Results) {
                            ResultsScreen(
                                report = state.report,
                                onBack = { viewModel.reset() },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkPermissionAndPick() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_VIDEO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            pickVideo.launch("video/*")
        } else {
            requestPermission.launch(permission)
        }
    }
}
