package com.example.highspeedcamera.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.highspeedcamera.DropMergeUiState
import com.example.highspeedcamera.FrameClass
import com.example.highspeedcamera.ui.theme.*

@Composable
fun DropMergeAnalysisScreen(
    state: DropMergeUiState.Analyzing,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = if (state.total > 0) state.processed.toFloat() / state.total else 0f
    val pct = (progress * 100).toInt()

    val classColor = when (state.currentClass) {
        FrameClass.FRAME_DROP  -> DmDropRed
        FrameClass.FRAME_MERGE -> DmMergeYellow
        FrameClass.NORMAL      -> DmNormalGreen
    }
    val classLabel = when (state.currentClass) {
        FrameClass.FRAME_DROP  -> "FRAME DROP"
        FrameClass.FRAME_MERGE -> "FRAME MERGE"
        FrameClass.NORMAL      -> "NORMAL"
    }

    Box(modifier = modifier.fillMaxSize()) {
        DmGroundBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Scoreboard panel
            Surface(
                modifier = Modifier.fillMaxWidth(0.92f),
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF1A1A1A),
                shadowElevation = 20.dp,
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF2A2A2A), Color(0xFF0A0A0A))
                            )
                        )
                        .border(3.dp, Color(0xFF404040), RoundedCornerShape(4.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DmAnalysisDot(active = true)
                        Text(
                            text = "ANALYZING...",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = DmYellow,
                            letterSpacing = 3.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        DmAnalysisDot(active = progress > 0.5f)
                    }

                    Divider(color = Color(0xFF404040), thickness = 2.dp)

                    // Video filename
                    Text(
                        text = state.videoName,
                        color = DmGrey,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Current classification
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0A0A0A), RoundedCornerShape(2.dp))
                            .border(1.dp, classColor.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = classLabel,
                            color = classColor,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp,
                        )
                    }

                    // Progress bar
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = DmYellow,
                            trackColor = Color(0xFF1A1A1A),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${state.processed} / ${state.total} frames",
                                color = DmGrey,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                text = "$pct%",
                                color = DmYellow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }

                    Divider(color = Color(0xFF303030), thickness = 1.dp)

                    // Running counts
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0A0A0A), RoundedCornerShape(2.dp))
                            .border(1.dp, Color(0xFF303030), RoundedCornerShape(2.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        DmCountLabel("OK", state.normalCount, DmNormalGreen)
                        Text("│", color = Color(0xFF404040), fontFamily = FontFamily.Monospace)
                        DmCountLabel("DROP", state.dropCount, DmDropRedLight)
                        Text("│", color = Color(0xFF404040), fontFamily = FontFamily.Monospace)
                        DmCountLabel("MERGE", state.mergeCount, DmMergeYellowLight)
                    }

                    // Pipeline hint
                    Text(
                        text = "dupe → flow → ssim → canny",
                        color = Color(0xFF505050),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                    )

                    Divider(color = Color(0xFF404040), thickness = 2.dp)

                    TextButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.textButtonColors(contentColor = DmGrey),
                    ) {
                        Text(
                            text = "CANCEL",
                            fontSize = 11.sp,
                            letterSpacing = 2.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DmAnalysisDot(active: Boolean) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(
                color = if (active) DmRed else Color(0xFF303030),
                shape = CircleShape,
            )
            .border(
                1.dp,
                if (active) DmRed.copy(alpha = 0.5f) else Color(0xFF404040),
                CircleShape,
            )
    )
}

@Composable
private fun DmCountLabel(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$count",
            color = color,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = label,
            color = DmGrey,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}
