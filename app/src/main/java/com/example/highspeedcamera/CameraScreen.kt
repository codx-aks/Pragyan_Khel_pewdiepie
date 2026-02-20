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
    innerPadding: PaddingValues,
    viewModel: CameraViewModel,
    onPlaybackClick: (videoPath: String?, metaPath: String?) -> Unit
) {
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingTime by viewModel.recordingTime.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val cameraInfo by viewModel.cameraInfo.collectAsState()
    val permissionDenied by viewModel.permissionDenied.collectAsState()
    val unsupportedFeatureMessage by viewModel.unsupportedFeatureMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(permissionDenied) {
        if (permissionDenied) {
            snackbarHostState.showSnackbar(
                message = "Camera permission is required.",
                duration = SnackbarDuration.Short
            )
            viewModel.onPermissionDeniedDismissed()
        }
    }
    
    LaunchedEffect(unsupportedFeatureMessage) {
        unsupportedFeatureMessage?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Long
            )
            viewModel.dismissUnsupportedMessage()
        }
    }

    val selectedFps by viewModel.selectedFps.collectAsState()
    val selectedSize by viewModel.selectedSize.collectAsState()
    val selectedIso by viewModel.selectedIso.collectAsState()
    val selectedShutterNs by viewModel.selectedShutterNs.collectAsState()
    val isManualExposure by viewModel.isManualExposure.collectAsState()

    val lastVideoPath by viewModel.lastVideoPath.collectAsState()
    val lastMetaPath by viewModel.lastMetaPath.collectAsState()

    val availableFpsOptions by viewModel.availableFpsOptions.collectAsState()
    val availableSizeOptions by viewModel.availableSizeOptions.collectAsState()
    val isoRange by viewModel.isoRange.collectAsState()
    val shutterRangeNs by viewModel.shutterRangeNs.collectAsState()
    val isUnsupportedSnackbarVisible = remember { mutableStateOf(false) }

//    Scaffold(
//        snackbarHost = { SnackbarHost(snackbarHostState) },
//        containerColor = Color.Transparent,
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize()
//            .padding(scaffoldPadding)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // TOP HALF: No Live Preview Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "[ NO LIVE PREVIEW ]\n\nCamera records directly\nto storage at high speed.\nPreview would limit FPS.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

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
                                .background(MaterialTheme.colorScheme.error, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = recordingTime,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = statusMessage,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelMedium,
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
                                containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                contentColor = if (isRecording) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (isRecording) "⏹  STOP" else "⏺  RECORD",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        FilledTonalButton(
                            onClick = { onPlaybackClick(lastVideoPath, lastMetaPath) },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isRecording && lastVideoPath != null
                        ) {
                            Text(
                                text = "▶  PLAYBACK",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

            // BOTTOM HALF: Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.2f)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "High Speed Camera Specifications",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = cameraInfo,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    DropdownMenuBox(
                        label = "FRAME RATE",
                        items = availableFpsOptions.map { "$it FPS" },
                        selectedItem = "$selectedFps FPS",
                        onItemSelected = { idx -> 
                            if (idx in availableFpsOptions.indices) {
                                viewModel.setFps(availableFpsOptions[idx]) 
                            }
                        },
                        modifier = Modifier.weight(1f).padding(end = 4.dp)
                    )

                    DropdownMenuBox(
                        label = "RESOLUTION",
                        items = availableSizeOptions.map { "${it.width} × ${it.height}" },
                        selectedItem = "${selectedSize.width} × ${selectedSize.height}",
                        onItemSelected = { idx -> 
                            if (idx in availableSizeOptions.indices) {
                                viewModel.setSize(availableSizeOptions[idx])
                            }
                        },
                        modifier = Modifier.weight(1f).padding(start = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "MANUAL EXPOSURE",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = isManualExposure,
                        onCheckedChange = { viewModel.setManualExposure(it) }
                    )
                }

                if (isManualExposure) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text("ISO: $selectedIso", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Slider(
                                value = selectedIso.toFloat(),
                                onValueChange = { viewModel.setIso(it.toInt()) },
                                valueRange = isoRange.lower.toFloat()..isoRange.upper.toFloat(),
                                steps = 6
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            val denominator = (1_000_000_000.0 / selectedShutterNs).toInt()
                            Text("Shutter: 1/$denominator s", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                            Slider(
                                value = selectedShutterNs.toFloat(),
                                onValueChange = { viewModel.setShutterNs(it.toLong()) },
                                valueRange = shutterRangeNs.lower.toFloat()..shutterRangeNs.upper.toFloat()
                            )
                            val maxDenominator = (1_000_000_000.0 / shutterRangeNs.lower.coerceAtLeast(1)).toInt()
                            val minDenominator = (1_000_000_000.0 / shutterRangeNs.upper.coerceAtLeast(1)).toInt()
                            
                            Text(
                                "1/${maxDenominator}s ◀──────────────▶ 1/${minDenominator}s",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "⚠ Manual ISO/shutter may be limited in high-speed mode on some devices.",
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
//    }
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
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp)
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
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(8.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = Modifier.menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                items.forEachIndexed { index, selectionOption ->
                    DropdownMenuItem(
                        text = { Text(text = selectionOption, color = MaterialTheme.colorScheme.onSurfaceVariant) },
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
