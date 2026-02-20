package com.example.highspeedcamera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val viewModel: CameraViewModel by viewModels()

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
            viewModel.initializeCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_PERMISSIONS)
        }

        setContent {
            CameraScreen(
                viewModel = viewModel,
                onPlaybackClick = { videoPath, metaPath ->
                    if (videoPath != null) {
                        val intent = Intent(this, PlaybackActivity::class.java).apply {
                            putExtra(PlaybackActivity.EXTRA_VIDEO_PATH, videoPath)
                            putExtra(PlaybackActivity.EXTRA_META_PATH, metaPath)
                        }
                        startActivity(intent)
                    }
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()) {
            viewModel.openCamera()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS && allPermissionsGranted()) {
            viewModel.initializeCamera()
        } else {
            Toast.makeText(this, "Camera and microphone permissions required", Toast.LENGTH_LONG).show()
        }
    }
}
