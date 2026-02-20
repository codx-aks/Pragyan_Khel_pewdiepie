package com.example.highspeedcamera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {

    private val cameraViewModel: CameraViewModel by viewModels()
    private val playbackViewModel: PlaybackViewModel by viewModels()

    companion object {
        private const val REQUEST_PERMISSIONS = 100
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (allPermissionsGranted()) {
            cameraViewModel.initializeCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_PERMISSIONS)
        }

        setContent {
            HighSpeedCameraApp(cameraViewModel, playbackViewModel)
        }
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()) {
            cameraViewModel.openCamera()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS && allPermissionsGranted()) {
            cameraViewModel.initializeCamera()
        } else {
            Toast.makeText(this, "Camera and microphone permissions required", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun HighSpeedCameraApp(
    cameraViewModel: CameraViewModel,
    playbackViewModel: PlaybackViewModel
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "camera") {
        composable("camera") {
            CameraScreen(
                viewModel = cameraViewModel,
                onPlaybackClick = { videoPath, metaPath ->
                    if (videoPath != null) {
                        // URL encode paths since they contain slashes which confuse Navigation routes
                        val encVideo = URLEncoder.encode(videoPath, "UTF-8")
                        val encMeta = if (metaPath != null) URLEncoder.encode(metaPath, "UTF-8") else "null"
                        navController.navigate("playback/$encVideo/$encMeta")
                    }
                }
            )
        }

        composable(
            route = "playback/{videoPath}/{metaPath}",
            arguments = listOf(
                navArgument("videoPath") { type = NavType.StringType },
                navArgument("metaPath") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val encVideo = backStackEntry.arguments?.getString("videoPath") ?: ""
            val encMeta = backStackEntry.arguments?.getString("metaPath") ?: "null"
            
            val videoPath = URLDecoder.decode(encVideo, "UTF-8")
            val metaPath = if (encMeta != "null") URLDecoder.decode(encMeta, "UTF-8") else null

            PlaybackScreen(
                videoPath = videoPath,
                metaPath = metaPath,
                viewModel = playbackViewModel,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
