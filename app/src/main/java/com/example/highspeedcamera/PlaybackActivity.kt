package com.example.highspeedcamera

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels

class PlaybackActivity : ComponentActivity() {

    companion object {
        const val EXTRA_VIDEO_PATH = "video_path"
        const val EXTRA_META_PATH = "meta_path"
    }

    private val viewModel: PlaybackViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val videoPath = intent.getStringExtra(EXTRA_VIDEO_PATH)
        val metaPath = intent.getStringExtra(EXTRA_META_PATH)

        if (videoPath == null) {
            Toast.makeText(this, "No video to play", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            PlaybackScreen(
                videoPath = videoPath,
                metaPath = metaPath,
                viewModel = viewModel,
                onBackClick = { finish() }
            )
        }
    }
}
