package com.example.highspeedcamera.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DmDarkBackground)
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Analyzing Video",
            color = DmOnSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = state.videoName,
            color = DmTextMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(32.dp))

        Text(
            text = state.currentClass.name.replace("_", " "),
            color = classColor,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )

        Spacer(Modifier.height(24.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
            color = DmPrimary,
            trackColor = DmDarkSurface,
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${state.processed} / ${state.total} frames",
                color = DmTextMuted,
                fontSize = 12.sp,
            )
            Text(
                text = "$pct%",
                color = DmPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Normal: ${state.normalCount}  |  Drops: ${state.dropCount}  |  Merges: ${state.mergeCount}",
            color = DmOnSurface.copy(alpha = 0.7f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(DmDarkSurface, RoundedCornerShape(4.dp))
                .padding(12.dp),
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Running: duplicate check → Farneback optical flow → SSIM triplet → Canny edge count",
            color = DmTextDimmed,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        TextButton(
            onClick = onCancel,
            colors = ButtonDefaults.textButtonColors(contentColor = DmTextMuted),
        ) {
            Text("Cancel", fontSize = 14.sp)
        }
    }
}
