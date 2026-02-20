package com.example.highspeedcamera.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    val severity = when {
        dropPct + mergePct > 20 -> "🔴 SEVERE — Major temporal corruption detected"
        dropPct + mergePct > 5  -> "🟡 MODERATE — Notable temporal errors present"
        dropPct + mergePct > 0  -> "🟢 MINOR — Few temporal anomalies found"
        else                    -> "✅ CLEAN — No temporal errors detected"
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DmDarkBackground),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DmDarkSurface)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onBack,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFCCCCCC)),
            ) {
                Text("← Back", fontSize = 12.sp)
            }
            Text(
                text = "Analysis Complete",
                color = DmOnSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${report.processingTimeMs}ms",
                color = DmTextDimmed,
                fontSize = 11.sp,
            )
        }

        // Summary card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DmDarkSurfaceVariant)
                .padding(16.dp),
        ) {
            Text(text = severity, color = DmOnSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("$totalAnalyzed frames analyzed", color = DmTextMuted, fontSize = 12.sp)
                Text("%.1f fps  •  %.1fs".format(report.fps, report.durationMs / 1000.0), color = DmTextMuted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DmCountPill(report.normalCount, "Normal", DmNormalGreen, DmNormalGreenBg, Modifier.weight(1f))
                DmCountPill(report.dropCount, "Drops", DmDropRedLight, DmDropRedBg, Modifier.weight(1f))
                DmCountPill(report.mergeCount, "Merges", DmMergeYellowLight, DmMergeYellowBg, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth().height(12.dp)) {
                if (normalPct > 0) Box(Modifier.weight(normalPct.coerceAtLeast(0.01f)).fillMaxHeight().background(DmNormalGreen))
                if (dropPct > 0)   Box(Modifier.weight(dropPct.coerceAtLeast(0.01f)).fillMaxHeight().background(DmDropRed))
                if (mergePct > 0)  Box(Modifier.weight(mergePct.coerceAtLeast(0.01f)).fillMaxHeight().background(DmMergeYellow))
            }
        }

        // Filter chips
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = showNormal, onClick = { showNormal = !showNormal },
                label = { Text("Normal", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = DmNormalGreenBg, selectedLabelColor = DmNormalGreen, labelColor = DmNormalGreen.copy(alpha = 0.5f)))
            FilterChip(selected = showDrop, onClick = { showDrop = !showDrop },
                label = { Text("Frame Drops", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = DmDropRedBg, selectedLabelColor = DmDropRedLight, labelColor = DmDropRedLight.copy(alpha = 0.5f)))
            FilterChip(selected = showMerge, onClick = { showMerge = !showMerge },
                label = { Text("Frame Merges", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = DmMergeYellowBg, selectedLabelColor = DmMergeYellowLight, labelColor = DmMergeYellowLight.copy(alpha = 0.5f)))
        }

        // Frame list
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(items = filteredFrames, key = { it.index }) { frame ->
                DmFrameResultItem(frame)
            }
        }
    }
}

@Composable
private fun DmCountPill(count: Int, label: String, textColor: Color, bgColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.background(bgColor, RoundedCornerShape(4.dp)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$count", color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = textColor, fontSize = 12.sp)
    }
}

@Composable
private fun DmFrameResultItem(frame: FrameResult) {
    val (labelText, textColor, barColor) = when (frame.classification) {
        FrameClass.NORMAL      -> Triple("NORMAL", DmNormalGreen, DmNormalGreenDark)
        FrameClass.FRAME_DROP  -> Triple("FRAME DROP", DmDropRedLight, Color(0xFF662020))
        FrameClass.FRAME_MERGE -> Triple("FRAME MERGE", DmMergeYellowLight, Color(0xFF664800))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .background(DmDarkSurfaceVariant, RoundedCornerShape(4.dp))
            .height(IntrinsicSize.Min),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(barColor))

        frame.annotatedBitmap?.let { bmp ->
            val imageBitmap = remember(bmp) { bmp.asImageBitmap() }
            Image(
                bitmap = imageBitmap,
                contentDescription = "Frame #${frame.index}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 100.dp, height = 56.dp).padding(8.dp),
            )
        }

        Column(Modifier.weight(1f).padding(vertical = 8.dp, horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Frame #${frame.index}", color = Color(0xFFAAAAAA), fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text(labelText, color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("@ ${frame.timestampMs}ms", color = DmTextDimmed, fontSize = 10.sp, modifier = Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.height(2.dp))
            Text(frame.reason, color = Color(0xFFCCCCCC), fontSize = 11.sp)
            Spacer(Modifier.height(3.dp))
            Text(
                "ΔPx: ${"%.2f".format(frame.meanAbsDiff)}  Flow: ${"%.3f".format(frame.motionMagnitude)}  SSIM: ${"%.3f".format(frame.ssimNeighbor)}  SynSSIM: ${"%.3f".format(frame.ssimSynthetic)}  Edges: ${frame.edgeCount}",
                color = DmTextDimmed, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            )
        }
    }
}
