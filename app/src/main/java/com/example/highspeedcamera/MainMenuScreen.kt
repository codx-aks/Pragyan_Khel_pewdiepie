package com.example.highspeedcamera

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MainMenuScreen(
    onCameraClick: () -> Unit,
    onDropMergeClick: () -> Unit,
    onFocusTrackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Logo
            Image(
                painter = painterResource(id = R.drawable.khellogo),
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(120.dp)
                    .padding(bottom = 16.dp)
            )
            
            // Title
            Text(
                text = "High Speed Camera",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Button 1: 240 FPS Camera
            MenuButton(
                text = "240 FPS",
                onClick = onCameraClick,
                containerColor = MaterialTheme.colorScheme.primary
            )
            
            // Button 2: Drop/Merge (placeholder for now)
            MenuButton(
                text = "Drop/Merge",
                onClick = onDropMergeClick,
                containerColor = MaterialTheme.colorScheme.secondary
            )
            
            // Button 3: Focus/Track (opens webview)
            MenuButton(
                text = "Focus/Track",
                onClick = onFocusTrackClick,
                containerColor = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun MenuButton(
    text: String,
    onClick: () -> Unit,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        )
    ) {
        Text(
            text = text,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
