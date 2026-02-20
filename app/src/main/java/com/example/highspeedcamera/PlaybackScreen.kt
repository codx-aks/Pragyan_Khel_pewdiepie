package com.example.highspeedcamera

import android.content.Intent
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun PlaybackScreen(
    innerPadding: PaddingValues,
    videoPath: String?,
    metaPath: String?,
    viewModel: PlaybackViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(videoPath, metaPath) {
        if (videoPath != null) {
            viewModel.loadVideo(videoPath, metaPath)
        }
    }

    val isPlaying by viewModel.isPlaying.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val videoName by viewModel.videoName.collectAsState()
    val metadata by viewModel.metadata.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0F))
            .padding(innerPadding)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBackClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1F))
            ) {
                Text("← Back", color = Color(0xFFCCCCCC))
            }

            Text(
                text = videoName,
                color = Color(0xFFAAAAAA),
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            )

            Button(
                onClick = {
                    videoPath?.let { path ->
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            java.io.File(path)
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "video/mp4"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF555555))
            ) {
                Text("Share", color = Color.White)
            }
        }

        // Video Player
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (videoPath != null) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoPath(videoPath)
                            setOnPreparedListener { mp ->
                                viewModel.onMediaPlayerReady(mp)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (isLoading) {
                CircularProgressIndicator(color = Color(0xFF00E5FF))
            }
        }

        // Metadata section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1F)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    "RECORDING METADATA",
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        MetaItem("FPS", metadata["FPS"] ?: "—")
                        MetaItem("ISO", metadata["ISO"] ?: "—")
                        MetaItem("EXPOSURE", metadata["Exposure"] ?: "—")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        MetaItem("RESOLUTION", metadata["Resolution"] ?: "—")
                        MetaItem("SHUTTER", metadata["Shutter"] ?: "—")
                        MetaItem("DURATION", metadata["Duration"] ?: "—")
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                MetaItem("RECORDED AT", metadata["Timestamp"] ?: "—")
            }
        }

        // Control Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111116))
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Text(
                    formatTime(currentPosition),
                    color = Color(0xFFAAAAAA),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
                
                Slider(
                    value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                    onValueChange = { viewModel.seekTo(it) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                )

                Text(
                    formatTime(duration),
                    color = Color(0xFFAAAAAA),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = { viewModel.restart() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222228)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("⏮", fontSize = 18.sp)
                }

                Spacer(modifier = Modifier.width(16.dp))

                Button(
                    onClick = { viewModel.togglePlayPause() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                ) {
                    Text(if (isPlaying) "⏸  Pause" else "▶  Play", color = Color.White)
                }

                Spacer(modifier = Modifier.width(16.dp))

                Button(
                    onClick = {
                        videoPath?.let { path ->
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.fromFile(java.io.File(path)), "video/mp4")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // No app found
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222228)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Gallery", color = Color(0xFFCCCCCC))
                }
            }
        }
    }
}

@Composable
fun MetaItem(label: String, value: String) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(label, color = Color(0xFF666666), fontSize = 10.sp)
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

fun formatTime(ms: Int): String {
    val s = ms / 1000
    return String.format("%02d:%02d", s / 60, s % 60)
}
