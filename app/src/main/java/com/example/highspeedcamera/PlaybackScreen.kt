package com.example.highspeedcamera

import android.content.Intent
import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView


private object Pb {
    val SkyTop        = Color(0xFF050D14)
    val SkyMid        = Color(0xFF0A1A2E)
    val FieldDeep     = Color(0xFF1B5E20)
    val PanelBg       = Color(0xFF111111)
    val PanelBorder   = Color(0xFF2A2A2A)
    val ItemBg        = Color(0xFF1A1A1A)
    val YellowLED     = Color(0xFFFFD600)
    val CyanGlow      = Color(0xFF00E5FF)
    val RecRed        = Color(0xFFE53935)
    val ReplayGreen   = Color(0xFF00E676)
    val OrangeTeam    = Color(0xFFE64A19)
    val TextWhite     = Color(0xFFF5F5F5)
    val TextSub       = Color(0xFF90A4AE)
    val TextDim       = Color(0xFF4A5C6A)
}


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
        if (videoPath != null) viewModel.loadVideo(videoPath, metaPath)
    }

    val isPlaying       by viewModel.isPlaying.collectAsState()
    val isLoading       by viewModel.isLoading.collectAsState()
    val videoName       by viewModel.videoName.collectAsState()
    val metadata        by viewModel.metadata.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration        by viewModel.duration.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Pb.SkyTop)
            .padding(innerPadding)
    ) {
        // ── STADIUM TUNNEL BACKGROUND ────────────────────────────────────
        // Night sky gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .background(
                    Brush.verticalGradient(
                        listOf(Pb.SkyTop, Pb.SkyMid, Color(0xFF0D200E))
                    )
                )
        )

        // Field strip at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.35f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0D200E), Pb.FieldDeep, Color(0xFF0A1A0A))
                    )
                )
        )

        // Floodlight beams from top corners
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    // Left floodlight
                    val leftTop = Offset(w * 0.06f, h * 0.04f)
                    drawLine(Color(0xFF607D8B), leftTop, Offset(leftTop.x, h * 0.45f), 5f)
                    drawCircle(Color(0xFFFFFFCC), 12f, leftTop)
                    drawCircle(
                        Brush.radialGradient(listOf(Color.White.copy(0.5f), Color.Transparent), leftTop, 28f),
                        28f, leftTop
                    )
                    // Left beam
                    drawLine(
                        Brush.linearGradient(
                            listOf(Color.White.copy(0.06f), Color.Transparent),
                            leftTop, Offset(w * 0.5f, h * 0.55f)
                        ),
                        leftTop, Offset(w * 0.5f, h * 0.55f), 180f
                    )
                    // Right floodlight
                    val rightTop = Offset(w * 0.94f, h * 0.04f)
                    drawLine(Color(0xFF607D8B), rightTop, Offset(rightTop.x, h * 0.45f), 5f)
                    drawCircle(Color(0xFFFFFFCC), 12f, rightTop)
                    drawCircle(
                        Brush.radialGradient(listOf(Color.White.copy(0.5f), Color.Transparent), rightTop, 28f),
                        28f, rightTop
                    )
                    drawLine(
                        Brush.linearGradient(
                            listOf(Color.White.copy(0.06f), Color.Transparent),
                            rightTop, Offset(w * 0.5f, h * 0.55f)
                        ),
                        rightTop, Offset(w * 0.5f, h * 0.55f), 180f
                    )
                    // Stars
                    val stars = listOf(
                        Offset(w * 0.15f, h * 0.03f), Offset(w * 0.3f, h * 0.06f),
                        Offset(w * 0.55f, h * 0.02f), Offset(w * 0.72f, h * 0.07f),
                        Offset(w * 0.85f, h * 0.04f), Offset(w * 0.45f, h * 0.09f)
                    )
                    stars.forEach { drawCircle(Color.White.copy(0.5f), 1.5f, it) }
                }
        )

        // Vignette
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            listOf(Color.Transparent, Color.Black.copy(0.5f)),
                            Offset(size.width / 2f, size.height / 2f),
                            size.width * 0.75f
                        )
                    )
                }
        )

        Column(modifier = Modifier.fillMaxSize()) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFE8E8E8))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickableNoRipple(onBackClick)
                ) {
                    Text(
                        "BACK",
                        color = Color(0xFF1A1A1A),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        letterSpacing = 2.sp
                    )
                }

                // Centre separator
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        videoName,
                        color = Pb.TextDim,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Share pill — orange team style
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Pb.OrangeTeam)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickableNoRipple {
                            videoPath?.let { path ->
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context, "${context.packageName}.fileprovider", java.io.File(path)
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "video/mp4"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Replay"))
                            }
                        }
                ) {
                    Text(
                        "SHARE",
                        color = Pb.TextWhite,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        letterSpacing = 2.sp
                    )
                }
            }

            // ═══════════════════════════════════════════════════════════
            //  INSTANT REPLAY BANNER
            // ═══════════════════════════════════════════════════════════
            InstantReplayBanner()

            Spacer(Modifier.height(8.dp))

            // ═══════════════════════════════════════════════════════════
            //  JUMBOTRON VIDEO FRAME
            // ═══════════════════════════════════════════════════════════
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .padding(horizontal = 14.dp)
                    // Outer metallic glow
                    .drawBehind {
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                listOf(
                                    Pb.CyanGlow.copy(alpha = 0.0f),
                                    Pb.CyanGlow.copy(alpha = 0.2f),
                                    Pb.CyanGlow.copy(alpha = 0.0f)
                                )
                            ),
                            cornerRadius = CornerRadius(8f),
                            size = Size(size.width + 16f, size.height + 16f),
                            topLeft = Offset(-8f, -8f)
                        )
                    }
                    .border(
                        1.5.dp,
                        Brush.linearGradient(
                            listOf(
                                Pb.CyanGlow.copy(alpha = 0.35f),
                                Pb.CyanGlow,
                                Pb.YellowLED.copy(alpha = 0.3f),
                                Pb.CyanGlow,
                                Pb.CyanGlow.copy(alpha = 0.35f)
                            )
                        ),
                        RoundedCornerShape(8.dp)
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
                    // Subtle bottom shadow on the video
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(0.3f)),
                                startY = size.height * 0.7f,
                                endY = size.height
                            )
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (videoPath != null) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoPath(videoPath)
                                setOnPreparedListener { mp -> viewModel.onMediaPlayerReady(mp) }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Pb.CyanGlow,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(32.dp)
                    )
                }
                // Duration chip
                if (duration > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color(0xCC111111), RoundedCornerShape(3.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            formatTime(duration),
                            color = Pb.TextSub,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ═══════════════════════════════════════════════════════════
            //  PLAYBACK CONTROLS — broadcast console style
            // ═══════════════════════════════════════════════════════════
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Pb.PanelBg)
            ) {
                // Yellow top accent
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    Pb.YellowLED.copy(0.5f),
                                    Pb.YellowLED,
                                    Pb.YellowLED.copy(0.5f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Seek bar
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatTime(currentPosition),
                            color = Pb.CyanGlow,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                        Slider(
                            value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                            onValueChange = { viewModel.seekTo(it) },
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Pb.YellowLED,
                                activeTrackColor = Pb.YellowLED,
                                inactiveTrackColor = Pb.PanelBorder
                            )
                        )
                        Text(
                            formatTime(duration),
                            color = Pb.TextDim,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }

                    // Transport buttons — LED console style
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BroadcastButton(
                            label = "RESTART",
                            onClick = { viewModel.restart() },
                            color = Pb.TextSub,
                            modifier = Modifier.weight(0.7f)
                        )

                        Box(
                            modifier = Modifier
                                .weight(1.4f)
                                .height(50.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isPlaying) Pb.RecRed else Pb.YellowLED
                                )
                                .clickableNoRipple { if (!isLoading) viewModel.togglePlayPause() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (isPlaying) "PAUSE" else "PLAY",
                                color = if (isPlaying) Pb.TextWhite else Color(0xFF111111),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                letterSpacing = 3.sp
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Pb.PanelBg)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(Pb.RecRed, CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "MATCH ANALYSIS",
                            color = Pb.YellowLED,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 4.sp
                        )
                    }
                    Box(Modifier.size(8.dp).background(Pb.RecRed, CircleShape))
                }

                Divider(color = Pb.PanelBorder, thickness = 0.5.dp)

                // Stats grid — 3 columns
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnalysisStatCell("FPS",        metadata["FPS"]        ?: "—", Modifier.weight(1f))
                    AnalysisStatCell("RESOLUTION", metadata["Resolution"] ?: "—", Modifier.weight(1f))
                    AnalysisStatCell("DURATION",   metadata["Duration"]   ?: "—", Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnalysisStatCell("ISO",      metadata["ISO"]      ?: "—", Modifier.weight(1f))
                    AnalysisStatCell("SHUTTER",  metadata["Shutter"]  ?: "—", Modifier.weight(1f))
                    AnalysisStatCell("EXPOSURE", metadata["Exposure"] ?: "—", Modifier.weight(1f))
                }

                // Timestamp full row
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Pb.ItemBg, RoundedCornerShape(4.dp))
                        .border(0.5.dp, Pb.CyanGlow.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .border(1.dp, Pb.CyanGlow.copy(alpha = 0.5f), RoundedCornerShape(3.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "TS",
                                color = Pb.CyanGlow,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "RECORDED AT",
                                color = Pb.CyanGlow.copy(alpha = 0.6f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                letterSpacing = 2.sp
                            )
                            Text(
                                metadata["Timestamp"] ?: "—",
                                color = Pb.TextWhite,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun InstantReplayBanner() {
    val shimmer = rememberInfiniteTransition(label = "replay_shimmer")
    val glowAlpha by shimmer.animateFloat(
        initialValue = 0.65f, targetValue = 1f, label = "ga",
        animationSpec = infiniteRepeatable<Float>(
            tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse
        )
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Pb.PanelBg.copy(alpha = 0.9f),
                            Pb.PanelBg.copy(alpha = 0.9f),
                            Color.Transparent
                        )
                    ),
                    RoundedCornerShape(4.dp)
                )
                .border(
                    0.5.dp,
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Pb.CyanGlow.copy(alpha = glowAlpha * 0.5f),
                            Pb.CyanGlow.copy(alpha = glowAlpha),
                            Pb.CyanGlow.copy(alpha = glowAlpha * 0.5f),
                            Color.Transparent
                        )
                    ),
                    RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 24.dp, vertical = 7.dp)
        ) {
            Text(
                "INSTANT REPLAY",
                color = Pb.CyanGlow.copy(alpha = glowAlpha),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                letterSpacing = 5.sp
            )
        }
    }
}

@Composable
private fun AnalysisStatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Pb.ItemBg, RoundedCornerShape(4.dp))
            .border(0.5.dp, Pb.PanelBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = Pb.CyanGlow.copy(alpha = 0.55f),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 7.sp,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            color = Pb.YellowLED,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}


@Composable
private fun BroadcastButton(
    label: String,
    onClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Pb.ItemBg)
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.5.sp
        )
    }
}


private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = MutableInteractionSource(),
    indication = null,
    onClick = onClick
)


@Composable
fun MetaItem(label: String, value: String) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(label, color = Pb.CyanGlow.copy(0.6f), fontFamily = FontFamily.Monospace, fontSize = 9.sp)
        Text(value, color = Pb.TextWhite, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

fun formatTime(ms: Int): String {
    val s = ms / 1000
    return String.format("%02d:%02d", s / 60, s % 60)
}