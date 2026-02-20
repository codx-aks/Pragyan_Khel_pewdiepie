package com.example.highspeedcamera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.Scaffold
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
        private val REQUIRED_PERMISSIONS: Array<String>
            get() {
                val perms = mutableListOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    // API 28 and below: need WRITE_EXTERNAL_STORAGE for Downloads access
                    perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // API 33+: need READ_MEDIA_VIDEO to read back the saved videos
                    perms.add(Manifest.permission.READ_MEDIA_VIDEO)
                } else {
                    // API 29-32: need READ_EXTERNAL_STORAGE to read back files
                    perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                return perms.toTypedArray()
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
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

    Scaffold { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "camera",
        ) {
            composable("camera") {
                CameraScreen(
                    innerPadding = innerPadding,
                    viewModel = cameraViewModel,
                    onPlaybackClick = { videoPath, metaPath ->
                        if (videoPath != null) {
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
                    innerPadding = innerPadding,
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
}
