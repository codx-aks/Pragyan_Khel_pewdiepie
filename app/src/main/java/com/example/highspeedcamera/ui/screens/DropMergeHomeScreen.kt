package com.example.highspeedcamera.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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

    Box(modifier = modifier.fillMaxSize()) {
        // Cricket ground background
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
                        .border(
                            width = 3.dp,
                            color = Color(0xFF404040),
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Panel header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DmScoreboardDot()
                        Text(
                            text = "DROP-MERGE",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = DmYellow,
                            letterSpacing = 3.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        DmScoreboardDot()
                    }

                    Divider(color = Color(0xFF404040), thickness = 2.dp)

                    // Number badge + title
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(2.dp),
                            color = Color(0xFF1A1A1A),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DmYellow),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "02",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DmYellow,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "FRAME ANALYZER",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = DmWhite,
                                letterSpacing = 1.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                text = "beyond errors",
                                fontSize = 11.sp,
                                color = DmGrey,
                                letterSpacing = 1.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }

                    Divider(color = Color(0xFF303030), thickness = 1.dp)

                    // Select video button
                    Button(
                        onClick = onSelectVideo,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                        shape = RoundedCornerShape(2.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0A0A0A),
                        ),
                        elevation = ButtonDefaults.buttonElevation(0.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(2.dp, Color(0xFF505050), RoundedCornerShape(2.dp))
                                .padding(horizontal = 16.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "SELECT VIDEO",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DmWhite,
                                    letterSpacing = 2.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Text(
                                    text = "▶",
                                    fontSize = 18.sp,
                                    color = DmRed,
                                )
                            }
                        }
                    }

                    Divider(color = Color(0xFF303030), thickness = 1.dp)

                    // Legend cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Frame Drop card
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFF0A0A0A), RoundedCornerShape(2.dp))
                                .border(1.dp, Color(0xFF303030), RoundedCornerShape(2.dp))
                                .height(IntrinsicSize.Min),
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .fillMaxHeight()
                                    .background(DmDropRed),
                            )
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "FRAME DROP",
                                    color = DmDropRedLight,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "optical flow spike",
                                    color = DmGrey,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }

                        // Frame Merge card
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0xFF0A0A0A), RoundedCornerShape(2.dp))
                                .border(1.dp, Color(0xFF303030), RoundedCornerShape(2.dp))
                                .height(IntrinsicSize.Min),
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .fillMaxHeight()
                                    .background(DmMergeYellow),
                            )
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "FRAME MERGE",
                                    color = DmMergeYellowLight,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "SSIM triplet blend",
                                    color = DmGrey,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }

                    Divider(color = Color(0xFF404040), thickness = 2.dp)

                    // Footer row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = onBack,
                            colors = ButtonDefaults.textButtonColors(contentColor = DmGrey),
                        ) {
                            Text(
                                text = "← BACK",
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
}

@Composable
private fun DmScoreboardDot() {
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(color = DmRed, shape = CircleShape)
            .border(1.dp, DmRed.copy(alpha = 0.5f), CircleShape)
    )
}

@Composable
fun DmGroundBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerX = width / 2f
        val centerY = height / 2f

        // Outer field
        drawRect(color = DmBackground)

        // Main oval
        drawCircle(
            color = DmFieldGreen,
            radius = width * 0.48f,
            center = Offset(centerX, centerY),
        )

        // Inner circle
        drawCircle(
            color = DmLightFieldGreen,
            radius = width * 0.35f,
            center = Offset(centerX, centerY),
        )

        // Boundary rope
        drawCircle(
            color = androidx.compose.ui.graphics.Color.White,
            radius = width * 0.48f,
            center = Offset(centerX, centerY),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8f),
        )
    }
}
