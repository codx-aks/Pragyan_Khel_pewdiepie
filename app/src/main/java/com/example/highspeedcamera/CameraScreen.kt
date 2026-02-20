package com.example.highspeedcamera

import android.util.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    onPlaybackClick: (videoPath: String?, metaPath: String?) -> Unit
) {
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingTime by viewModel.recordingTime.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val cameraInfo by viewModel.cameraInfo.collectAsState()

    val selectedFps by viewModel.selectedFps.collectAsState()
    val selectedSize by viewModel.selectedSize.collectAsState()
    val selectedIso by viewModel.selectedIso.collectAsState()
    val selectedShutterNs by viewModel.selectedShutterNs.collectAsState()
    val isManualExposure by viewModel.isManualExposure.collectAsState()

    val lastVideoPath by viewModel.lastVideoPath.collectAsState()
    val lastMetaPath by viewModel.lastMetaPath.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0F))
    ) {
        // TOP HALF: No Live Preview Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF0A0A0F)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "[ NO LIVE PREVIEW ]\n\nCamera records directly\nto storage at high speed.\nPreview would limit FPS.",
                color = Color(0xFF333344),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            // Recording Indicator Overlay
            if (isRecording) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color.Red, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = recordingTime,
                        color = Color.Red,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Bottom Buttons inside the Top Half
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = statusMessage,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.toggleRecording() },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) Color(0xFFF44336) else Color(0xFF2196F3)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isRecording) "⏹  STOP" else "⏺  RECORD",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = { onPlaybackClick(lastVideoPath, lastMetaPath) },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isRecording && lastVideoPath != null
                    ) {
                        Text(
                            text = "▶  PLAYBACK",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Divider(color = Color(0xFF222228), thickness = 1.dp)

        // BOTTOM HALF: Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.2f)
                .background(Color(0xFF1A1A1F))
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "⚡ HIGH SPEED CAM",
                color = Color(0xFF00E5FF),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = cameraInfo,
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111116), RoundedCornerShape(4.dp))
                    .padding(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Selectors
            Row(modifier = Modifier.fillMaxWidth()) {
                // FPS Selector implementation for Compose
                DropdownMenuBox(
                    label = "FRAME RATE",
                    items = viewModel.FPS_OPTIONS.map { "$it FPS" },
                    selectedItem = "$selectedFps FPS",
                    onItemSelected = { idx -> viewModel.setFps(viewModel.FPS_OPTIONS[idx]) },
                    modifier = Modifier.weight(1f).padding(end = 4.dp)
                )

                DropdownMenuBox(
                    label = "RESOLUTION",
                    items = viewModel.HIGH_SPEED_SIZES.map { "${it.width} × ${it.height}" },
                    selectedItem = "${selectedSize.width} × ${selectedSize.height}",
                    onItemSelected = { idx -> viewModel.setSize(viewModel.HIGH_SPEED_SIZES[idx]) },
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "MANUAL EXPOSURE",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = isManualExposure,
                    onCheckedChange = { viewModel.setManualExposure(it) }
                )
            }

            if (isManualExposure) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF111116), RoundedCornerShape(8.dp))
                        .padding(16.dp)
                        .padding(top = 8.dp)
                ) {
                    Text("ISO: $selectedIso", color = Color.White, fontWeight = FontWeight.Bold)
                    Slider(
                        value = selectedIso.toFloat(),
                        onValueChange = { viewModel.setIso(it.toInt()) },
                        valueRange = 100f..6400f,
                        steps = 6
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val denominator = (1_000_000_000.0 / selectedShutterNs).toInt()
                    Text("Shutter: 1/$denominator s", color = Color.White, fontWeight = FontWeight.Bold)

                    // Reversed slider logically
                    Slider(
                        value = selectedShutterNs.toFloat(),
                        onValueChange = { viewModel.setShutterNs(it.toLong()) },
                        valueRange = 250_000f..33_333_333f // 1/4000s to 1/30s
                    )
                    Text(
                        "1/4000s ◀──────────────▶ 1/30s",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "⚠ Manual ISO/shutter may be limited in high-speed mode on some devices.",
                color = Color(0xFFFF9800),
                fontSize = 11.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownMenuBox(
    label: String,
    items: List<String>,
    selectedItem: String,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(text = label, color = Color.Gray, fontSize = 10.sp, letterSpacing = 1.5.sp)
        Spacer(modifier = Modifier.height(4.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            TextField(
                value = selectedItem,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.textFieldColors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = Color(0xFF222228),
                    unfocusedContainerColor = Color(0xFF222228)
                ),
                textStyle = LocalTextStyle.current.copy(color = Color.White),
                modifier = Modifier.menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color(0xFF222228))
            ) {
                items.forEachIndexed { index, selectionOption ->
                    DropdownMenuItem(
                        text = { Text(text = selectionOption, color = Color.White) },
                        onClick = {
                            onItemSelected(index)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
