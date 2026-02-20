package com.example.highspeedcamera.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.highspeedcamera.ui.theme.*

@Composable
fun DropMergeHomeScreen(
    onSelectVideo: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAboutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DmDarkBackground)
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Back button row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBack,
                colors = ButtonDefaults.textButtonColors(contentColor = DmTextMuted)
            ) {
                Text("← Back", fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "📹",
            fontSize = 64.sp,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Temporal Error Detector",
            color = DmOnSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Detects frame drops & frame merges\nusing optical flow + SSIM triplet analysis",
            color = DmTextMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onSelectVideo,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DmPrimary,
                contentColor = DmOnPrimary,
            ),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                text = "Select Video to Analyze",
                fontSize = 16.sp,
            )
        }

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(DmDarkSurface, RoundedCornerShape(4.dp))
                    .padding(12.dp),
            ) {
                Text(
                    text = "🔴 FRAME DROP",
                    color = DmDropRedLight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Duplicate detection + optical flow spike",
                    color = DmTextMuted,
                    fontSize = 10.sp,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(DmDarkSurface, RoundedCornerShape(4.dp))
                    .padding(12.dp),
            ) {
                Text(
                    text = "🟡 FRAME MERGE",
                    color = DmMergeYellowLight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "SSIM triplet synthesis + edge ghosting",
                    color = DmTextMuted,
                    fontSize = 10.sp,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        TextButton(
            onClick = { showAboutDialog = true },
            colors = ButtonDefaults.textButtonColors(contentColor = DmTextMuted),
        ) {
            Text(
                text = "Algorithm Details",
                fontSize = 12.sp,
            )
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Text(
                    text = "About — Hybrid Algorithm",
                    fontWeight = FontWeight.Bold,
                    color = DmOnSurface,
                )
            },
            text = {
                Text(
                    text = "This feature implements the research-backed hybrid temporal error detection pipeline:\n\n" +
                            "1. DUPLICATE DETECTION\n   Mean abs diff < 1.5 → masked frame drop\n\n" +
                            "2. MOTION SPIKE (Optical Flow)\n   Farneback dense flow avg > μ + 2.5σ AND SSIM < μ − 2σ → true frame drop\n\n" +
                            "3. SSIM TRIPLET SYNTHESIS\n   SSIM(F_t, blend(F_t-1,F_t+1)) > 0.92 → frame merge / ghosting\n\n" +
                            "4. EDGE COUNT SPIKE (Canny)\n   Edge pixels > μ + 2σ + blend sim > 0.80 → double-edge merge\n\n" +
                            "Rolling 30-frame window for adaptive thresholding.",
                    fontSize = 13.sp,
                    color = DmTextMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("OK", color = DmPrimary)
                }
            },
            containerColor = DmDarkSurface,
        )
    }
}
