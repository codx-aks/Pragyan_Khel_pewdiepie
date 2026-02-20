package com.example.highspeedcamera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

// Cricket ground colors
private val FieldGreen = Color(0xFF2E7D32)
private val DarkGreen = Color(0xFF1B5E20)
private val LightGreen = Color(0xFF388E3C)
private val BoundaryWhite = Color(0xFFFFFFFF)
private val PitchBrown = Color(0xFFD4A574)

// Scoreboard colors
private val ScoreboardBlack = Color(0xFF0A0A0A)
private val ScoreboardYellow = Color(0xFFFFFF00)
private val ScoreboardWhite = Color(0xFFFFFFFF)
private val ScoreboardRed = Color(0xFFFF3333)

@Composable
fun MainMenuScreen(
    onCameraClick: () -> Unit,
    onDropMergeClick: () -> Unit,
    onFocusTrackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Cricket ground background
        CricketGroundBackground()
        
        // Center menu
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo in a circular "cricket ball" style container
            Surface(
                modifier = Modifier
                    .size(140.dp)
                    .padding(bottom = 24.dp),
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 12.dp
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(20.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.khellogo),
                        contentDescription = "App Logo",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            
            // Title with team collaboration design
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.90f)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Collaboration header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Khel Company box
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.95f),
                        shadowElevation = 8.dp
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp)
                        ) {
                            Text(
                                text = "KHEL",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = DarkGreen,
                                letterSpacing = 2.sp
                            )

                        }
                    }
                    
                    // X separator
                    Text(
                        text = "✕",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    
                    // Pewdiepie Team box
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFF6B35).copy(alpha = 0.95f),
                        shadowElevation = 8.dp
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp)
                        ) {
                            Text(
                                text = "PEWDIEPIE",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )

                        }
                    }
                }
                

            }
            
            // Scoreboard container
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f),
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF1A1A1A),
                shadowElevation = 20.dp
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF2A2A2A),
                                    Color(0xFF0A0A0A)
                                )
                            )
                        )
                        .border(
                            width = 3.dp,
                            color = Color(0xFF404040),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Scoreboard header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ScoreboardDot(isActive = true)
                        Text(
                            text = "MAIN MENU",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = ScoreboardYellow,
                            letterSpacing = 3.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        ScoreboardDot(isActive = true)
                    }
                    
                    Divider(
                        color = Color(0xFF404040),
                        thickness = 2.dp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    
                    // Button 1: 240 FPS Camera
                    ScoreboardButton(
                        mainText = "HIGH FPS",
                        subText = "beyond limits",
                        number = "01",
                        onClick = onCameraClick
                    )
                    
                    // Separator
                    Divider(
                        color = Color(0xFF303030),
                        thickness = 1.dp
                    )
                    
                    // Button 2: Drop/Merge
                    ScoreboardButton(
                        mainText = "DROP-MERGE",
                        subText = "beyond errors",
                        number = "02",
                        onClick = onDropMergeClick,
                    )
                    
                    // Separator
                    Divider(
                        color = Color(0xFF303030),
                        thickness = 1.dp
                    )
                    
                    // Button 3: Focus/Track
                    ScoreboardButton(
                        mainText = "FOCUS",
                        subText = "beyond eyes",
                        number = "03",
                        onClick = onFocusTrackClick
                    )
                    
                    Divider(
                        color = Color(0xFF404040),
                        thickness = 2.dp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    
                    // Scoreboard footer
                    Text(
                        text = "SELECT OPTION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF808080),
                        letterSpacing = 2.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Footer text
            Text(
                text = "TAP TO CONTINUE",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.8f),
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
private fun ScoreboardButton(
    mainText: String,
    subText: String,
    number: String,
    onClick: () -> Unit,
    isDisabled: Boolean = false,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp),
        shape = RoundedCornerShape(2.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ScoreboardBlack,
            disabledContainerColor = ScoreboardBlack.copy(alpha = 0.6f)
        ),
        enabled = !isDisabled,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 2.dp,
                    color = if (isDisabled) Color(0xFF404040) else Color(0xFF505050),
                    shape = RoundedCornerShape(2.dp)
                )
                .background(
                    if (isDisabled) {
                        ScoreboardBlack.copy(alpha = 0.5f)
                    } else {
                        ScoreboardBlack
                    }
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Number display (left side)
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = if (isDisabled) Color(0xFF2A2A2A) else Color(0xFF1A1A1A),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isDisabled) Color(0xFF404040) else ScoreboardYellow
                    )
                ) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = number,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDisabled) Color(0xFF606060) else ScoreboardYellow,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                
                // Text content (center)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = mainText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDisabled) Color(0xFF606060) else ScoreboardWhite,
                        letterSpacing = 2.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = if (isDisabled) Color(0xFF505050) else Color(0xFF909090),
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                
                // Arrow indicator (right side)
                Text(
                    text = if (isDisabled) "⊗" else "▶",
                    fontSize = 18.sp,
                    color = if (isDisabled) Color(0xFF505050) else ScoreboardRed,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ScoreboardDot(isActive: Boolean) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(
                color = if (isActive) ScoreboardRed else Color(0xFF303030),
                shape = CircleShape
            )
            .border(
                width = 1.dp,
                color = if (isActive) ScoreboardRed.copy(alpha = 0.5f) else Color(0xFF404040),
                shape = CircleShape
            )
    )
}

@Composable
private fun CricketGroundBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerX = width / 2f
        val centerY = height / 2f
        
        // Outer field - darker green
        drawRect(
            color = DarkGreen
        )
        
        // Main field - medium green
        drawCircle(
            color = FieldGreen,
            radius = width * 0.48f,
            center = Offset(centerX, centerY)
        )
        
        // Inner circle - lighter green (30-yard circle)
        drawCircle(
            color = LightGreen,
            radius = width * 0.35f,
            center = Offset(centerX, centerY)
        )
        
        // Boundary circle (rope)
        drawCircle(
            color = BoundaryWhite,
            radius = width * 0.48f,
            center = Offset(centerX, centerY),
            style = Stroke(width = 8f)
        )
        
        // Dashed 30-yard circle
        val dashRadius = width * 0.35f
        val dashCount = 60
        for (i in 0 until dashCount) {
            if (i % 2 == 0) {
                val angle = (i * 360f / dashCount) * Math.PI / 180f
                val startX = centerX + cos(angle).toFloat() * dashRadius
                val startY = centerY + sin(angle).toFloat() * dashRadius
                val endAngle = ((i + 0.8f) * 360f / dashCount) * Math.PI / 180f
                val endX = centerX + cos(endAngle).toFloat() * dashRadius
                val endY = centerY + sin(endAngle).toFloat() * dashRadius
                
                drawLine(
                    color = BoundaryWhite.copy(alpha = 0.6f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round
                )
            }
        }
        
        // Cricket pitch in the center
        val pitchWidth = width * 0.12f
        val pitchHeight = height * 0.32f
        val pitchLeft = centerX - pitchWidth / 2
        val pitchTop = centerY - pitchHeight / 2
        
        // Pitch - brown/tan color
        drawRoundRect(
            color = PitchBrown,
            topLeft = Offset(pitchLeft, pitchTop),
            size = androidx.compose.ui.geometry.Size(pitchWidth, pitchHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
        )
        
        // Pitch markings - white lines
        // Crease lines at both ends
        drawLine(
            color = BoundaryWhite,
            start = Offset(pitchLeft + 4f, pitchTop + pitchHeight * 0.15f),
            end = Offset(pitchLeft + pitchWidth - 4f, pitchTop + pitchHeight * 0.15f),
            strokeWidth = 3f
        )
        drawLine(
            color = BoundaryWhite,
            start = Offset(pitchLeft + 4f, pitchTop + pitchHeight * 0.85f),
            end = Offset(pitchLeft + pitchWidth - 4f, pitchTop + pitchHeight * 0.85f),
            strokeWidth = 3f
        )
        
        // Stumps representation (small rectangles)
        val stumpSize = 6f
        val stumpY1 = pitchTop + pitchHeight * 0.15f
        val stumpY2 = pitchTop + pitchHeight * 0.85f
        
        // Top stumps
        for (i in -1..1) {
            drawRect(
                color = Color(0xFFF5DEB3),
                topLeft = Offset(centerX + i * 10f - stumpSize / 2, stumpY1 - 15f),
                size = androidx.compose.ui.geometry.Size(stumpSize, 15f)
            )
        }
        
        // Bottom stumps
        for (i in -1..1) {
            drawRect(
                color = Color(0xFFF5DEB3),
                topLeft = Offset(centerX + i * 10f - stumpSize / 2, stumpY2),
                size = androidx.compose.ui.geometry.Size(stumpSize, 15f)
            )
        }
        
        // Field pattern lines (optional decorative element)
        val lineSpacing = width * 0.08f
        for (i in -3..3) {
            val x = centerX + i * lineSpacing
            drawLine(
                color = DarkGreen.copy(alpha = 0.3f),
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 2f
            )
        }
        for (i in -4..4) {
            val y = centerY + i * lineSpacing
            drawLine(
                color = DarkGreen.copy(alpha = 0.3f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 2f
            )
        }
    }
}
