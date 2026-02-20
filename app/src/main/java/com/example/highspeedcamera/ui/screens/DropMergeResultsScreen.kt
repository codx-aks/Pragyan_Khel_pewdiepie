package com.example.highspeedcamera.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.highspeedcamera.AnalysisReport
import com.example.highspeedcamera.FrameClass
import com.example.highspeedcamera.FrameResult
import com.example.highspeedcamera.ui.theme.*

@Composable
fun DropMergeResultsScreen(
    report: AnalysisReport,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalAnalyzed = report.totalFrames
    val dropPct = if (totalAnalyzed > 0) report.dropCount * 100f / totalAnalyzed else 0f
    val mergePct = if (totalAnalyzed > 0) report.mergeCount * 100f / totalAnalyzed else 0f
    val normalPct = (100f - dropPct - mergePct).coerceAtLeast(0f)

    val (severityText, severityColor) = when {
        dropPct + mergePct > 20 -> "SEVERE  ·  MAJOR CORRUPTION" to DmDropRed
        dropPct + mergePct > 5  -> "MODERATE  ·  NOTABLE ERRORS" to DmMergeYellow
        dropPct + mergePct > 0  -> "MINOR  ·  FEW ANOMALIES" to DmNormalGreen
        else                    -> "CLEAN  ·  NO ERRORS FOUND" to DmNormalGreen
    }

    var showNormal by remember { mutableStateOf(true) }
    var showDrop   by remember { mutableStateOf(true) }
    var showMerge  by remember { mutableStateOf(true) }

    val filteredFrames = remember(showNormal, showDrop, showMerge) {
        report.frames.filter { f ->
            when (f.classification) {
                FrameClass.NORMAL      -> showNormal
                FrameClass.FRAME_DROP  -> showDrop
                FrameClass.FRAME_MERGE -> showMerge
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        DmGroundBackground()

        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header bar ──────────────────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1A1A1A),
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF2A2A2A), Color(0xFF0A0A0A))
                            )
                        )
                        .border(bottomWidth = 2.dp, color = Color(0xFF404040))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Back
                    TextButton(
                        onClick = onBack,
                        colors = ButtonDefaults.textButtonColors(contentColor = DmGrey),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            text = "← BACK",
                            fontSize = 11.sp,
                            letterSpacing = 2.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }

                    Text(
                        text = "ANALYSIS COMPLETE",
                        color = DmYellow,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )

                    Text(
                        text = "${report.processingTimeMs}ms",
                        color = Color(0xFF505050),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            // ── Summary card ─────────────────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1A1A1A),
            ) {
                Column(
                    modifier = Modifier
                        .background(Color(0xFF121212))
                        .border(bottomWidth = 2.dp, color = Color(0xFF303030))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Severity
                    Text(
                        text = severityText,
                        color = severityColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                    )

                    // Meta info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "$totalAnalyzed FRAMES",
                            color = DmGrey,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            text = "%.1f FPS  ·  %.1fs".format(report.fps, report.durationMs / 1000.0),
                            color = DmGrey,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }

                    // Count pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        DmCountPill(report.normalCount, "NORMAL",  DmNormalGreen,       DmNormalGreenBg,  Modifier.weight(1f))
                        DmCountPill(report.dropCount,   "DROPS",   DmDropRedLight,      DmDropRedBg,      Modifier.weight(1f))
                        DmCountPill(report.mergeCount,  "MERGES",  DmMergeYellowLight,  DmMergeYellowBg, Modifier.weight(1f))
                    }

                    // Stacked bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .border(1.dp, Color(0xFF303030), RoundedCornerShape(2.dp))
                            .background(Color(0xFF0A0A0A), RoundedCornerShape(2.dp)),
                    ) {
                        if (normalPct > 0) Box(Modifier.weight(normalPct.coerceAtLeast(0.01f)).fillMaxHeight().background(DmNormalGreen))
                        if (dropPct > 0)   Box(Modifier.weight(dropPct.coerceAtLeast(0.01f)).fillMaxHeight().background(DmDropRed))
                        if (mergePct > 0)  Box(Modifier.weight(mergePct.coerceAtLeast(0.01f)).fillMaxHeight().background(DmMergeYellow))
                    }
                }
            }

            // ── Filter chips ─────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D0D0D))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = showNormal,
                    onClick = { showNormal = !showNormal },
                    label = { Text("NORMAL", fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DmNormalGreenBg,
                        selectedLabelColor = DmNormalGreen,
                        labelColor = DmNormalGreen.copy(alpha = 0.4f),
                        containerColor = Color(0xFF0A0A0A),
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = showNormal,
                        selectedBorderColor = DmNormalGreen.copy(alpha = 0.5f),
                        borderColor = Color(0xFF303030),
                    ),
                )
                FilterChip(
                    selected = showDrop,
                    onClick = { showDrop = !showDrop },
                    label = { Text("DROPS", fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DmDropRedBg,
                        selectedLabelColor = DmDropRedLight,
                        labelColor = DmDropRedLight.copy(alpha = 0.4f),
                        containerColor = Color(0xFF0A0A0A),
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = showDrop,
                        selectedBorderColor = DmDropRed.copy(alpha = 0.5f),
                        borderColor = Color(0xFF303030),
                    ),
                )
                FilterChip(
                    selected = showMerge,
                    onClick = { showMerge = !showMerge },
                    label = { Text("MERGES", fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DmMergeYellowBg,
                        selectedLabelColor = DmMergeYellowLight,
                        labelColor = DmMergeYellowLight.copy(alpha = 0.4f),
                        containerColor = Color(0xFF0A0A0A),
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = showMerge,
                        selectedBorderColor = DmMergeYellow.copy(alpha = 0.5f),
                        borderColor = Color(0xFF303030),
                    ),
                )
            }

            // ── Frame list ───────────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(items = filteredFrames, key = { it.index }) { frame ->
                    DmFrameResultItem(frame)
                }
            }
        }
    }
}

// ── Extension for bottom-only border ─────────────────────────────────────────
private fun Modifier.border(bottomWidth: androidx.compose.ui.unit.Dp, color: Color): Modifier =
    this.border(
        width = bottomWidth,
        brush = Brush.verticalGradient(listOf(Color.Transparent, color)),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
    )

@Composable
private fun DmCountPill(
    count: Int,
    label: String,
    textColor: Color,
    bgColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(2.dp))
            .border(1.dp, textColor.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "$count",
            color = textColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = label,
            color = textColor.copy(alpha = 0.7f),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun DmFrameResultItem(frame: FrameResult) {
    val (labelText, textColor, barColor) = when (frame.classification) {
        FrameClass.NORMAL      -> Triple("NORMAL",      DmNormalGreen,      DmNormalGreenDark)
        FrameClass.FRAME_DROP  -> Triple("FRAME DROP",  DmDropRedLight,     Color(0xFF662020))
        FrameClass.FRAME_MERGE -> Triple("FRAME MERGE", DmMergeYellowLight, Color(0xFF664800))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .background(Color(0xFF0D0D0D), RoundedCornerShape(2.dp))
            .border(1.dp, Color(0xFF222222), RoundedCornerShape(2.dp))
            .height(IntrinsicSize.Min),
    ) {
        // Classification accent bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(barColor, RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp)),
        )

        // Thumbnail
        frame.annotatedBitmap?.let { bmp ->
            val imageBitmap = remember(bmp) { bmp.asImageBitmap() }
            Image(
                bitmap = imageBitmap,
                contentDescription = "Frame #${frame.index}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 96.dp, height = 54.dp)
                    .padding(6.dp),
            )
        }

        // Text content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp, horizontal = 10.dp),
        ) {
            // Top row: frame index + label + timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "#${frame.index}",
                    color = Color(0xFF606060),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = labelText,
                    color = textColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = "  @${frame.timestampMs}ms",
                    color = Color(0xFF404040),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = frame.reason,
                color = Color(0xFFAAAAAA),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "ΔPx:${"%.2f".format(frame.meanAbsDiff)}  Fl:${"%.3f".format(frame.motionMagnitude)}  SSIM:${"%.3f".format(frame.ssimNeighbor)}  Syn:${"%.3f".format(frame.ssimSynthetic)}",
                color = Color(0xFF404040),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
