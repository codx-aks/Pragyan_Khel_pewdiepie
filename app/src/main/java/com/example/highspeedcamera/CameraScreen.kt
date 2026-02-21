package com.example.highspeedcamera

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private object S {
    val SkyTop          = Color(0xFF050D14)
    val SkyMid          = Color(0xFF0A1A2E)
    val FieldBright     = Color(0xFF2E7D32)
    val FieldDeep       = Color(0xFF1B5E20)
    val PitchTan        = Color(0xFFBCA98A)

    val PanelBg         = Color(0xFF111111)
    val PanelBorder     = Color(0xFF2A2A2A)
    val ItemBg          = Color(0xFF1A1A1A)

    val YellowLED       = Color(0xFFFFD600)
    val AmberWarm       = Color(0xFFFFC107)
    val CyanGlow        = Color(0xFF00E5FF)
    val RecRed          = Color(0xFFE53935)
    val RecRedPulse     = Color(0xFFFF5252)
    val OrangeTeam      = Color(0xFFE64A19)
    val ReplayGreen     = Color(0xFF00E676)

    val TextWhite       = Color(0xFFF5F5F5)
    val TextSub         = Color(0xFF90A4AE)
    val TextDim         = Color(0xFF4A5C6A)
    val TextYellow      = Color(0xFFFFD600)
}

//  CAMERA SCREEN
@Composable
fun CameraScreen(
    innerPadding: PaddingValues,
    viewModel: CameraViewModel,
    onPlaybackClick: (videoPath: String?, metaPath: String?) -> Unit
) {
    val isRecording       by viewModel.isRecording.collectAsState()
    val recordingTime     by viewModel.recordingTime.collectAsState()
    val statusMessage     by viewModel.statusMessage.collectAsState()
    val cameraInfo        by viewModel.cameraInfo.collectAsState()
    val permissionDenied  by viewModel.permissionDenied.collectAsState()
    val unsupportedMsg    by viewModel.unsupportedFeatureMessage.collectAsState()

    val selectedFps       by viewModel.selectedFps.collectAsState()
    val selectedSize      by viewModel.selectedSize.collectAsState()
    val selectedIso       by viewModel.selectedIso.collectAsState()
    val selectedShutterNs by viewModel.selectedShutterNs.collectAsState()
    val isManualExposure  by viewModel.isManualExposure.collectAsState()
    val lastVideoPath     by viewModel.lastVideoPath.collectAsState()
    val lastMetaPath      by viewModel.lastMetaPath.collectAsState()
    val allFpsOptions     by viewModel.allFpsOptions.collectAsState()
    val allSizeOptions    by viewModel.allSizeOptions.collectAsState()
    val supportedFpsSet   by viewModel.supportedFpsSet.collectAsState()
    val supportedSizeSet  by viewModel.supportedSizeSet.collectAsState()
    val isoRange          by viewModel.isoRange.collectAsState()
    val shutterRangeNs    by viewModel.shutterRangeNs.collectAsState()
    val selectedNrMode    by viewModel.selectedNoiseReductionMode.collectAsState()
    val availableNrModes  by viewModel.availableNoiseReductionModes.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(permissionDenied) {
        if (permissionDenied) {
            snackbar.showSnackbar("Camera permission is required.", duration = SnackbarDuration.Short)
            viewModel.onPermissionDeniedDismissed()
        }
    }
    LaunchedEffect(unsupportedMsg) {
        unsupportedMsg?.let {
            snackbar.showSnackbar(message = it, duration = SnackbarDuration.Long)
            viewModel.dismissUnsupportedMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color.Transparent
    ) { scaffoldPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(S.SkyTop)
        ) {

            // STADIUM BACKGROUND PAINTING
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.55f)
                    .background(
                        Brush.verticalGradient(
                            listOf(S.SkyTop, S.SkyMid, Color(0xFF0D2010))
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.62f)
                    .align(Alignment.BottomCenter)
                    .drawBehind {
                        drawOval(
                            brush = Brush.radialGradient(
                                colors = listOf(S.FieldBright, S.FieldDeep, Color(0xFF0D1A0F)),
                                center = Offset(size.width / 2f, size.height * 0.45f),
                                radius = size.width * 0.65f
                            ),
                            topLeft = Offset(-size.width * 0.15f, 0f),
                            size = Size(size.width * 1.3f, size.height)
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.2f),
                            radius = size.width * 0.48f,
                            center = Offset(size.width / 2f, size.height * 0.3f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                        )
                        val pitchW = size.width * 0.12f
                        val pitchH = size.height * 0.22f
                        val pitchX = size.width / 2f - pitchW / 2f
                        val pitchY = size.height * 0.15f
                        drawRoundRect(
                            color = S.PitchTan,
                            topLeft = Offset(pitchX, pitchY),
                            size = Size(pitchW, pitchH),
                            cornerRadius = CornerRadius(4f)
                        )
                    }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val w = size.width
                        val h = size.height
                        drawFloodlightPole(this, Offset(w * 0.08f, h * 0.08f), beamAngle = 30f, beamAlpha = 0.07f)
                        drawFloodlightPole(this, Offset(w * 0.92f, h * 0.08f), beamAngle = -30f, beamAlpha = 0.07f, mirrorX = true)
                    }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            Brush.radialGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = size.width * 0.75f
                            )
                        )
                    }
            )

            // UI CONTENT
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding)
            ) {

                //  VIEWFINDER
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBehind {
                                drawRoundRect(
                                    brush = Brush.linearGradient(
                                        listOf(
                                            S.CyanGlow.copy(alpha = 0.0f),
                                            S.CyanGlow.copy(alpha = 0.25f),
                                            S.CyanGlow.copy(alpha = 0.0f)
                                        )
                                    ),
                                    cornerRadius = CornerRadius(6f),
                                    size = Size(size.width + 12f, size.height + 12f),
                                    topLeft = Offset(-6f, -6f)
                                )
                            }
                            .border(
                                1.5.dp,
                                Brush.linearGradient(
                                    listOf(
                                        S.CyanGlow.copy(alpha = 0.4f),
                                        S.CyanGlow,
                                        S.CyanGlow.copy(alpha = 0.4f)
                                    )
                                ),
                                RoundedCornerShape(6.dp)
                            )
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x22050D14))
                    ) {
                        ViewfinderCornerMarks(color = S.CyanGlow)

                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "NO LIVE PREVIEW",
                                color = S.TextDim,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                letterSpacing = 3.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "DIRECT-TO-STORAGE  ·  MAX FPS",
                                color = S.TextDim.copy(alpha = 0.5f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 7.sp,
                                letterSpacing = 1.5.sp
                            )
                        }

                        LiveScoreboardBadge(
                            isRecording = isRecording,
                            recordingTime = recordingTime,
                            fps = selectedFps,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp)
                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .background(S.PanelBg.copy(alpha = 0.88f), RoundedCornerShape(4.dp))
                                .border(0.5.dp, S.YellowLED.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                "${selectedFps} FPS  ${selectedSize.width}\u00d7${selectedSize.height}",
                                color = S.YellowLED,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp
                            )
                        }

                        Text(
                            statusMessage.uppercase(),
                            color = S.TextSub.copy(alpha = 0.45f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            letterSpacing = 2.sp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 10.dp)
                        )
                    }
                }

                //  MAIN MENU PANEL — bounded height with fixed bottom btns
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(S.PanelBg)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        S.YellowLED.copy(alpha = 0.7f),
                                        S.YellowLED,
                                        S.YellowLED.copy(alpha = 0.7f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                            .padding(top = 14.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {

                        // Panel header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).background(S.RecRed, CircleShape))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "MAIN MENU",
                                    color = S.YellowLED,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    letterSpacing = 4.sp
                                )
                            }
                            Box(Modifier.size(8.dp).background(S.RecRed, CircleShape))
                        }

                        Divider(color = S.PanelBorder, thickness = 0.5.dp)
                        Spacer(Modifier.height(10.dp))

                        // Camera info
                        ScoreboardMenuItem(
                            number = "00",
                            title = "CAMERA INFO",
                            subtitle = cameraInfo.lines().firstOrNull() ?: ""
                        )

                        Spacer(Modifier.height(8.dp))

                        // FPS selector
                        ScoreboardMenuDropdown(
                            number = "01",
                            title = "FRAME RATE",
                            subtitle = "recording speed",
                            items = allFpsOptions.map { "$it FPS" },
                            selectedItem = "$selectedFps FPS",
                            enabledItems = allFpsOptions.map { it in supportedFpsSet },
                            onItemSelected = { i -> if (i in allFpsOptions.indices) viewModel.setFps(allFpsOptions[i]) }
                        )

                        Spacer(Modifier.height(8.dp))

                        // Resolution selector
                        ScoreboardMenuDropdown(
                            number = "02",
                            title = "RESOLUTION",
                            subtitle = "frame dimensions",
                            items = allSizeOptions.map { "${it.width}\u00d7${it.height}" },
                            selectedItem = "${selectedSize.width}\u00d7${selectedSize.height}",
                            enabledItems = allSizeOptions.map { it in supportedSizeSet },
                            onItemSelected = { i -> if (i in allSizeOptions.indices) viewModel.setSize(allSizeOptions[i]) }
                        )

                        Spacer(Modifier.height(8.dp))

                        // Manual Exposure toggle
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(S.ItemBg, RoundedCornerShape(4.dp))
                                .border(0.5.dp, S.PanelBorder, RoundedCornerShape(4.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .border(1.5.dp, S.YellowLED, RoundedCornerShape(3.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "03",
                                        color = S.YellowLED,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "MANUAL EXPOSURE",
                                        color = S.TextWhite,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        "override auto settings",
                                        color = S.YellowLED.copy(alpha = 0.6f),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    )
                                }
                                Switch(
                                    checked = isManualExposure,
                                    onCheckedChange = { viewModel.setManualExposure(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = S.SkyTop,
                                        checkedTrackColor = S.CyanGlow,
                                        uncheckedThumbColor = S.TextDim,
                                        uncheckedTrackColor = S.PanelBorder
                                    )
                                )
                            }
                        }

                        // Manual exposure expanded controls
                        if (isManualExposure) {
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(S.ItemBg, RoundedCornerShape(4.dp))
                                    .border(0.5.dp, S.CyanGlow.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .padding(14.dp)
                            ) {
                                Column {
                                    // ISO
                                    StatReadout("ISO", "$selectedIso", "LOWER = LESS GRAIN")
                                    Slider(
                                        value = selectedIso.toFloat(),
                                        onValueChange = { viewModel.setIso(it.toInt()) },
                                        valueRange = isoRange.lower.toFloat()..isoRange.upper.toFloat(),
                                        steps = 6,
                                        colors = SliderDefaults.colors(
                                            thumbColor = S.YellowLED,
                                            activeTrackColor = S.YellowLED,
                                            inactiveTrackColor = S.PanelBorder
                                        )
                                    )
                                    Spacer(Modifier.height(8.dp))

                                    // Shutter
                                    val denom = (1_000_000_000.0 / selectedShutterNs).toInt()
                                    StatReadout("SHUTTER", "1/$denom s", "SLOWER = MORE LIGHT")
                                    val maxShutter = (1_000_000_000L / selectedFps).coerceAtMost(shutterRangeNs.upper)
                                    val minShutter = shutterRangeNs.lower.coerceAtMost(maxShutter)
                                    Slider(
                                        value = selectedShutterNs.toFloat().coerceIn(minShutter.toFloat(), maxShutter.toFloat()),
                                        onValueChange = { viewModel.setShutterNs(it.toLong()) },
                                        valueRange = minShutter.toFloat()..maxShutter.toFloat(),
                                        colors = SliderDefaults.colors(
                                            thumbColor = S.YellowLED,
                                            activeTrackColor = S.YellowLED,
                                            inactiveTrackColor = S.PanelBorder
                                        )
                                    )
                                    val maxD = (1_000_000_000.0 / minShutter.coerceAtLeast(1)).toInt()
                                    val minD = (1_000_000_000.0 / maxShutter.coerceAtLeast(1)).toInt()
                                    Text(
                                        "1/${maxD}s  ---  1/${minD}s",
                                        color = S.TextDim,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 8.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(Modifier.height(12.dp))

                                    // Noise reduction
                                    Text(
                                        "NOISE REDUCTION",
                                        color = S.CyanGlow.copy(alpha = 0.7f),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 8.sp,
                                        letterSpacing = 2.sp
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    LedDropdown(
                                        items = availableNrModes.map { noiseName(it) },
                                        selectedItem = noiseName(selectedNrMode),
                                        onItemSelected = { i ->
                                            if (i in availableNrModes.indices) viewModel.setNoiseReductionMode(availableNrModes[i])
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(S.PanelBg)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Transparent,
                                            S.PanelBorder,
                                            S.PanelBorder,
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CricketBallRecordButton(
                                isRecording = isRecording,
                                onClick = { viewModel.toggleRecording() },
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { onPlaybackClick(lastVideoPath, lastMetaPath) },
                                enabled = !isRecording && lastVideoPath != null,
                                modifier = Modifier.weight(1f).height(52.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1A3A1A),
                                    contentColor = S.ReplayGreen,
                                    disabledContainerColor = S.PanelBorder.copy(alpha = 0.3f),
                                    disabledContentColor = S.TextDim
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (!isRecording && lastVideoPath != null) S.ReplayGreen.copy(alpha = 0.6f)
                                    else S.PanelBorder
                                )
                            ) {
                                Text(
                                    "REPLAY",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    letterSpacing = 2.sp
                                )
                            }
                        }

                        Text(
                            "SELECT OPTION",
                            color = S.TextDim,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            letterSpacing = 3.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        )
                    } // end fixed bottom Column

                } // end panel Column
            }
        }
    }
}

//  COMPONENT — Viewfinder corner marks
@Composable
private fun ViewfinderCornerMarks(color: Color) {
    val armDp = 20.dp
    val thickDp = 2.dp
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val a = armDp.toPx()
                val t = thickDp.toPx()
                val w = size.width
                val h = size.height
                drawRect(color, Offset(0f, 0f), Size(a, t))
                drawRect(color, Offset(0f, 0f), Size(t, a))
                drawRect(color, Offset(w - a, 0f), Size(a, t))
                drawRect(color, Offset(w - t, 0f), Size(t, a))
                drawRect(color, Offset(0f, h - t), Size(a, t))
                drawRect(color, Offset(0f, h - a), Size(t, a))
                drawRect(color, Offset(w - a, h - t), Size(a, t))
                drawRect(color, Offset(w - t, h - a), Size(t, a))
            }
    )
}

//  COMPONENT — LIVE scoreboard badge
@Composable
private fun LiveScoreboardBadge(
    isRecording: Boolean,
    recordingTime: String,
    fps: Int,
    modifier: Modifier = Modifier
) {
    val pulse = rememberInfiniteTransition(label = "live")
    val dotAlpha by pulse.animateFloat(
        initialValue = 1f, targetValue = 0.2f, label = "da",
        animationSpec = infiniteRepeatable<Float>(
            tween(700, easing = LinearEasing), RepeatMode.Reverse
        )
    )
    Column(
        modifier = modifier
            .background(S.PanelBg.copy(alpha = 0.92f), RoundedCornerShape(4.dp))
            .border(
                0.5.dp,
                if (isRecording) S.RecRed.copy(alpha = 0.7f) else S.CyanGlow.copy(alpha = 0.25f),
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(
                        S.RecRed.copy(alpha = if (isRecording) dotAlpha else 0.25f),
                        CircleShape
                    )
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = if (isRecording) "REC  $recordingTime" else "STANDBY",
                color = if (isRecording) S.TextWhite else S.TextDim,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.sp
            )
        }
        Text(
            "$fps FPS",
            color = S.CyanGlow,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            letterSpacing = 1.sp
        )
    }
}

//  COMPONENT — Scoreboard menu item
@Composable
private fun ScoreboardMenuItem(number: String, title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(S.ItemBg, RoundedCornerShape(4.dp))
            .border(0.5.dp, S.PanelBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .border(1.5.dp, S.YellowLED, RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    number,
                    color = S.YellowLED,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = S.TextWhite,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp
                )
                Text(
                    subtitle,
                    color = S.YellowLED.copy(alpha = 0.55f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }
        }
    }
}

//  COMPONENT — Scoreboard menu item with dropdown
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScoreboardMenuDropdown(
    number: String,
    title: String,
    subtitle: String,
    items: List<String>,
    selectedItem: String,
    enabledItems: List<Boolean>,
    onItemSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(S.ItemBg, RoundedCornerShape(4.dp))
            .border(0.5.dp, S.PanelBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .border(1.5.dp, S.YellowLED, RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    number,
                    color = S.YellowLED,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = S.TextWhite,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp
                )
                Text(
                    subtitle,
                    color = S.YellowLED.copy(alpha = 0.55f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }
            androidx.compose.material3.ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                Box(
                    modifier = Modifier
                        .menuAnchor()
                        .background(S.PanelBg, RoundedCornerShape(3.dp))
                        .border(0.5.dp, S.YellowLED.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            selectedItem,
                            color = S.YellowLED,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (expanded) "▲" else "▶",
                            color = S.YellowLED,
                            fontSize = 8.sp
                        )
                    }
                }
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(S.PanelBg)
                ) {
                    items.forEachIndexed { i, opt ->
                        val en = enabledItems.getOrElse(i) { true }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    opt,
                                    color = if (en) S.TextWhite else S.TextDim.copy(alpha = 0.4f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp
                                )
                            },
                            onClick = { if (en) { onItemSelected(i); expanded = false } },
                            enabled = en
                        )
                    }
                }
            }
        }
    }
}

//  COMPONENT — Cricket ball record button with pulse ring
@Composable
private fun CricketBallRecordButton(
    isRecording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulse = rememberInfiniteTransition(label = "ball_pulse")
    val ringScale by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.08f, label = "bs",
        animationSpec = infiniteRepeatable<Float>(
            tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse
        )
    )
    val ringAlpha by pulse.animateFloat(
        initialValue = 0.8f, targetValue = 0.1f, label = "ba",
        animationSpec = infiniteRepeatable<Float>(
            tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse
        )
    )

    Box(modifier = modifier.height(52.dp), contentAlignment = Alignment.Center) {
        if (isRecording) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .scale(ringScale)
                    .border(2.dp, S.RecRedPulse.copy(alpha = ringAlpha), RoundedCornerShape(8.dp))
            )
        }
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecording) S.RecRed else Color(0xFF8B0000),
                contentColor = S.TextWhite
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (isRecording) "STOP" else "RECORD",
                    color = S.TextWhite,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

//  COMPONENT — Stat readout row
@Composable
private fun StatReadout(label: String, value: String, hint: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column {
            Text(
                label,
                color = S.CyanGlow.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 2.sp
            )
            Text(
                hint,
                color = S.TextDim,
                fontFamily = FontFamily.Monospace,
                fontSize = 7.sp
            )
        }
        Text(
            value,
            color = S.YellowLED,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp
        )
    }
}

//  COMPONENT — Simple LED dropdown
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedDropdown(
    items: List<String>,
    selectedItem: String,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabledItems: List<Boolean> = items.map { true }
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        TextField(
            value = selectedItem,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = S.PanelBg,
                unfocusedContainerColor = S.PanelBg,
                focusedTextColor = S.YellowLED,
                unfocusedTextColor = S.TextWhite
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            ),
            shape = RoundedCornerShape(4.dp),
            modifier = modifier
                .menuAnchor()
                .border(0.5.dp, S.YellowLED.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(S.PanelBg)
        ) {
            items.forEachIndexed { i, opt ->
                val en = enabledItems.getOrElse(i) { true }
                DropdownMenuItem(
                    text = {
                        Text(
                            opt,
                            color = if (en) S.TextWhite else S.TextDim.copy(alpha = 0.35f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    },
                    onClick = { if (en) { onItemSelected(i); expanded = false } },
                    enabled = en
                )
            }
        }
    }
}

// Public alias kept for any existing callers
@Composable
fun DropdownMenuBox(
    label: String,
    items: List<String>,
    selectedItem: String,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabledItems: List<Boolean> = items.map { true }
) = LedDropdown(items, selectedItem, onItemSelected, modifier, enabledItems)

//  UTILITY — floodlight pole drawing
private fun drawFloodlightPole(
    scope: androidx.compose.ui.graphics.drawscope.DrawScope,
    topCenter: Offset,
    beamAngle: Float,
    beamAlpha: Float,
    mirrorX: Boolean = false
) {
    val poleBottom = Offset(topCenter.x, scope.size.height * 0.55f)
    scope.drawLine(
        color = Color(0xFF607D8B),
        start = topCenter,
        end = poleBottom,
        strokeWidth = 6f
    )
    scope.drawCircle(color = Color(0xFFFFFFCC), radius = 14f, center = topCenter)
    scope.drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.6f), Color.Transparent),
            center = topCenter,
            radius = 30f
        ),
        radius = 30f,
        center = topCenter
    )
    val spread = 200f
    val beamLength = scope.size.height * 0.45f
    val rad = Math.toRadians(beamAngle.toDouble())
    val beamTip = Offset(
        (topCenter.x + beamLength * Math.sin(rad)).toFloat(),
        (topCenter.y + beamLength * Math.cos(rad)).toFloat()
    )
    scope.drawLine(
        brush = Brush.linearGradient(
            listOf(Color.White.copy(alpha = beamAlpha), Color.Transparent),
            start = topCenter,
            end = beamTip
        ),
        start = topCenter,
        end = beamTip,
        strokeWidth = spread * 0.6f
    )
}

private fun noiseName(mode: Int): String = when (mode) {
    android.hardware.camera2.CaptureRequest.NOISE_REDUCTION_MODE_OFF               -> "OFF"
    android.hardware.camera2.CaptureRequest.NOISE_REDUCTION_MODE_FAST              -> "FAST"
    android.hardware.camera2.CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY      -> "HIGH QUALITY"
    android.hardware.camera2.CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL           -> "MINIMAL"
    android.hardware.camera2.CaptureRequest.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG  -> "ZERO SHUTTER LAG"
    else -> "MODE $mode"
}