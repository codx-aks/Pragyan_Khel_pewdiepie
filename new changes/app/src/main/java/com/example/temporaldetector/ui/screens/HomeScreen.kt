package com.example.temporaldetector.ui.screens

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
import com.example.temporaldetector.ui.theme.*

@Composable
fun HomeScreen(
    onSelectVideo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAboutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "📹",
            fontSize = 64.sp,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Temporal Error Detector",
            color = OnSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Detects frame drops & frame merges\nusing optical flow + SSIM triplet analysis",
            color = TextMuted,
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
                containerColor = Primary,
                contentColor = OnPrimary,
            ),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                text = "Select Video to Analyze",
                fontSize = 16.sp,
            )
        }

        Spacer(Modifier.height(24.dp))

        // Algorithm summary cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(DarkSurface, RoundedCornerShape(4.dp))
                    .padding(12.dp),
            ) {
                Text(
                    text = "🔴 FRAME DROP",
                    color = DropRedLight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Duplicate detection + optical flow spike",
                    color = TextMuted,
                    fontSize = 10.sp,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(DarkSurface, RoundedCornerShape(4.dp))
                    .padding(12.dp),
            ) {
                Text(
                    text = "🟡 FRAME MERGE",
                    color = MergeYellowLight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "SSIM triplet synthesis + edge ghosting",
                    color = TextMuted,
                    fontSize = 10.sp,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        TextButton(
            onClick = { showAboutDialog = true },
            colors = ButtonDefaults.textButtonColors(contentColor = TextMuted),
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
                )
            },
            text = {
                Text(
                    text = "This app implements the research-backed hybrid temporal error detection pipeline:\n\n" +
                            "1. DUPLICATE DETECTION\n   Mean abs diff < 1.5 → masked frame drop\n\n" +
                            "2. MOTION SPIKE (Optical Flow)\n   Farneback dense flow avg > μ + 2.5σ AND SSIM < μ − 2σ → true frame drop\n\n" +
                            "3. SSIM TRIPLET SYNTHESIS\n   SSIM(F_t, blend(F_t-1,F_t+1)) > 0.92 → frame merge / ghosting\n\n" +
                            "4. EDGE COUNT SPIKE (Canny)\n   Edge pixels > μ + 2σ + blend sim > 0.80 → double-edge merge\n\n" +
                            "Rolling 30-frame window for adaptive thresholding.",
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("OK")
                }
            },
            containerColor = DarkSurface,
        )
    }
}

