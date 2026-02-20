package com.example.highspeedcamera

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlinx.coroutines.delay
import kotlin.math.*

private val FieldGreen      = Color(0xFF2E7D32)   // deep outfield green
private val PitchGreen      = Color(0xFF388E3C)   // slightly lighter in-pitch
private val GroundLineColor = Color(0xFF1B5E20)   // dark border / ground line

@Composable
fun CricketAnimationScreen(onFinished: () -> Unit) {
    val totalDurationMs = 5000L

    LaunchedEffect(Unit) {
        delay(totalDurationMs)
        onFinished()
    }

    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue   = 1f,
            animationSpec = tween(totalDurationMs.toInt(), easing = LinearEasing)
        )
    }

    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(FieldGreen),        // entire screen is green
        contentAlignment = Alignment.Center
    ) {
        CricketMatchAnimation(
            progress = animProgress.value,
            modifier = Modifier
                .fillMaxWidth(1.00f)
                .fillMaxHeight(0.55f)
        )
    }
}

@Composable
fun CricketMatchAnimation(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val W = size.width
        val H = size.height

        val groundY  = H * 0.75f
        val bowlerX  = W * 0.12f
        val stumpsX  = W * 0.82f
        val batsmanX = stumpsX - W * 0.14f

        val runupPhase    = (progress / 0.38f).coerceIn(0f, 1f)
        val deliveryPhase = ((progress - 0.26f) / 0.46f).coerceIn(0f, 1f)
        val hitPhase      = ((progress - 0.70f) / 0.30f).coerceIn(0f, 1f)

        drawGround(W, H, groundY)
        drawPitch(W, groundY, bowlerX, stumpsX)
        drawStumps(stumpsX, groundY, hitPhase)
        drawBatsman(batsmanX, groundY, deliveryPhase, hitPhase)
        drawBowler(bowlerX, groundY, runupPhase, deliveryPhase)
        drawBall(W, H, groundY, bowlerX, stumpsX, deliveryPhase, hitPhase)
        drawImpact(stumpsX, groundY, hitPhase, H)
        drawFlyingBails(stumpsX, groundY, hitPhase, W, H)
    }
}

private fun DrawScope.drawGround(W: Float, H: Float, groundY: Float) {
    // Slightly darker green strip below the ground line (shadow / rough)
    drawRect(
        color   = FieldGreen.copy(alpha = 0.60f),
        topLeft = Offset(0f, groundY),
        size    = Size(W, H - groundY)
    )
    drawLine(GroundLineColor, Offset(0f, groundY), Offset(W, groundY), strokeWidth = 3f)
}

private fun DrawScope.drawPitch(W: Float, groundY: Float, bowlerX: Float, stumpsX: Float) {
    val pH   = size.height * 0.052f
    val pTop = groundY - pH

    drawRect(
        color   = Color(0xFFE59855).copy(alpha = 0.55f),   // dry grass over green
        topLeft = Offset(bowlerX - W * 0.02f, pTop),
        size    = Size(stumpsX - bowlerX + W * 0.05f, pH)
    )
    drawLine(Color.White, Offset(bowlerX + W * 0.04f, pTop + 2f),
        Offset(bowlerX + W * 0.04f, groundY - 2f), strokeWidth = 2f)
    drawLine(Color.White, Offset(stumpsX - W * 0.01f, pTop + 2f),
        Offset(stumpsX - W * 0.01f, groundY - 2f), strokeWidth = 2f)
}

private fun DrawScope.drawStumps(cx: Float, groundY: Float, hitPhase: Float) {
    val stumpH = size.height * 0.110f
    val stumpW = size.width  * 0.012f
    val gap    = size.width  * 0.022f
    val bailH  = stumpH * 0.062f
    val bailW  = gap + stumpW * 0.9f

    val xs = floatArrayOf(cx - gap, cx, cx + gap)

    val e     = 1f - (1f - hitPhase) * (1f - hitPhase)
    val flyDX = floatArrayOf(-size.width * 0.13f * e, size.width * 0.003f * e, size.width * 0.15f * e)
    val flyDY = floatArrayOf(-size.height * 0.15f * e, -size.height * 0.20f * e, -size.height * 0.13f * e)
    val flyR  = floatArrayOf(-50f * e, 5f * e, 54f * e)

    xs.forEachIndexed { i, sx ->
        withTransform({
            translate(flyDX[i], flyDY[i])
            rotate(flyR[i], Offset(sx, groundY))
        }) {
            drawRoundRect(
                color        = Color(0xFFF5DEB3),
                topLeft      = Offset(sx - stumpW / 2f, groundY - stumpH),
                size         = Size(stumpW, stumpH),
                cornerRadius = CornerRadius(stumpW / 2f)
            )
            drawLine(
                color       = Color(0xFFBCAAA4).copy(alpha = 0.45f),
                start       = Offset(sx, groundY - stumpH + stumpW * 1.5f),
                end         = Offset(sx, groundY - stumpW * 1.5f),
                strokeWidth = stumpW * 0.15f
            )
            drawCircle(Color(0xFFFFECB3), radius = stumpW * 0.58f,
                center = Offset(sx, groundY - stumpH))
        }
    }

    if (hitPhase < 0.10f) {
        val topY = groundY - stumpH - bailH
        for (b in 0..1) {
            drawRoundRect(
                color        = Color(0xFFFFD700),
                topLeft      = Offset(xs[b] - stumpW * 0.1f, topY),
                size         = Size(bailW, bailH),
                cornerRadius = CornerRadius(bailH / 2f)
            )
        }
    }
}


private fun DrawScope.drawBatsman(
    cx: Float, groundY: Float,
    deliveryPhase: Float, hitPhase: Float
) {
    val figH     = size.height * 0.160f
    val u        = figH / 20f

    val headR    = u * 2.0f
    val bodyH    = u * 6.0f
    val legH     = u * 5.0f
    val armLen   = u * 2.8f
    val handleLen = u * 2.2f
    val bladeLen  = u * 5.5f
    val batW     = u * 1.3f
    val handleW  = batW * 0.58f

    val footY   = groundY
    val hipY    = footY   - legH
    val shouldY = hipY    - bodyH
    val headCY  = shouldY - headR * 1.1f

    val jersey = Color(0xFF1A237E)
    val skin   = Color(0xFFD7A77A)
    val helmet = Color(0xFF0D47A1)
    val pad    = Color(0xFFEEEEEE)
    val blade  = Color(0xFF8D6E63)   // willow
    val grip   = Color(0xFF4E342E)   // dark grip

    drawLine(jersey, Offset(cx + u * 0.8f, hipY), Offset(cx + u * 2.2f, footY),
        strokeWidth = u * 1.3f, cap = StrokeCap.Round)
    drawLine(jersey, Offset(cx - u * 0.8f, hipY), Offset(cx - u * 2.0f, footY),
        strokeWidth = u * 1.3f, cap = StrokeCap.Round)
    drawRoundRect(pad, Offset(cx - u * 3.5f, hipY + u * 0.5f),
        Size(u * 1.7f, legH * 0.86f), CornerRadius(u * 0.4f))
    drawRoundRect(pad, Offset(cx + u * 0.6f, hipY + u * 0.5f),
        Size(u * 1.7f, legH * 0.86f), CornerRadius(u * 0.4f))

    drawLine(jersey, Offset(cx, hipY), Offset(cx, shouldY),
        strokeWidth = u * 1.8f, cap = StrokeCap.Round)

    val batAngle = when {
        deliveryPhase < 0.55f -> -80f
        hitPhase > 0f         ->  35f
        else -> -80f + ((deliveryPhase - 0.55f) / 0.45f) * 115f
    }

    val pivX = cx - u * 0.6f
    val pivY = shouldY + u * 1.0f

    rotate(batAngle, Offset(pivX, pivY)) {
        // 1. Arm: shoulder → hands
        drawLine(skin, Offset(pivX, pivY), Offset(pivX, pivY + armLen),
            strokeWidth = u * 1.0f, cap = StrokeCap.Round)

        val handleTop = pivY + armLen
        drawRoundRect(
            color        = grip,
            topLeft      = Offset(pivX - handleW / 2f, handleTop),
            size         = Size(handleW, handleLen),
            cornerRadius = CornerRadius(handleW / 2f)
        )
        repeat(4) { g ->
            drawLine(Color(0xFF6D4C41).copy(alpha = 0.60f),
                Offset(pivX - handleW / 2f, handleTop + u * 0.30f + g * u * 0.45f),
                Offset(pivX + handleW / 2f, handleTop + u * 0.30f + g * u * 0.45f),
                strokeWidth = u * 0.20f)
        }

        val bladeTop = handleTop + handleLen
        drawRoundRect(
            color        = blade,
            topLeft      = Offset(pivX - batW / 2f, bladeTop),
            size         = Size(batW, bladeLen),
            cornerRadius = CornerRadius(batW / 4f)
        )
        drawLine(Color(0xFFBCAAA4).copy(alpha = 0.55f),
            Offset(pivX - batW / 2f + u * 0.16f, bladeTop + u * 0.3f),
            Offset(pivX - batW / 2f + u * 0.16f, bladeTop + bladeLen * 0.85f),
            strokeWidth = u * 0.22f)
        drawLine(Color(0xFFA5D6A7).copy(alpha = 0.45f),
            Offset(pivX - batW / 2f + u * 0.2f, bladeTop + bladeLen * 0.42f),
            Offset(pivX + batW / 2f - u * 0.2f, bladeTop + bladeLen * 0.42f),
            strokeWidth = u * 0.30f)
    }

    drawLine(skin,
        Offset(cx + u * 0.5f, shouldY + u * 1.2f),
        Offset(cx - u * 1.0f, shouldY + u * 3.8f),
        strokeWidth = u * 0.9f, cap = StrokeCap.Round)

    drawCircle(skin, headR, Offset(cx, headCY))
    drawArc(helmet.copy(alpha = 0.93f), 180f, 180f, useCenter = true,
        topLeft = Offset(cx - headR * 1.1f, headCY - headR * 1.05f),
        size    = Size(headR * 2.2f, headR * 1.75f))
    repeat(4) { g ->
        drawLine(Color(0xFF9E9E9E),
            Offset(cx - headR * 0.72f + g * headR * 0.48f, headCY - headR * 0.04f),
            Offset(cx - headR * 0.72f + g * headR * 0.48f, headCY + headR * 0.62f),
            strokeWidth = u * 0.27f)
    }
    drawLine(Color(0xFF9E9E9E),
        Offset(cx - headR * 0.78f, headCY + headR * 0.26f),
        Offset(cx + headR * 0.78f, headCY + headR * 0.26f),
        strokeWidth = u * 0.20f)
    drawLine(helmet,
        Offset(cx - headR * 1.55f, headCY),
        Offset(cx + headR * 1.1f,  headCY),
        strokeWidth = u * 0.65f, cap = StrokeCap.Round)
}

private fun DrawScope.drawBowler(
    cx: Float, groundY: Float,
    runupPhase: Float, deliveryPhase: Float
) {
    val figH   = size.height * 0.160f
    val u      = figH / 20f
    val headR  = u * 2.0f
    val bodyH  = u * 6.0f
    val legH   = u * 5.0f
    val armLen = u * 4.0f

    val leanX   = (deliveryPhase * u * 3.0f).coerceAtMost(u * 3.0f)
    val footY   = groundY
    val hipY    = footY   - legH
    val shouldY = hipY    - bodyH + leanX * 0.3f
    val headCY  = shouldY - headR * 1.1f

    val jersey = Color(0xFF0D47A1)   // blue jersey (contrasts with green background)
    val skin   = Color(0xFFD7A77A)

    val stride = sin(runupPhase * PI.toFloat() * 5f) * u * 2.0f
    drawLine(jersey, Offset(cx, hipY), Offset(cx - u * 2.0f + stride, footY),
        strokeWidth = u * 1.3f, cap = StrokeCap.Round)
    drawLine(jersey, Offset(cx, hipY), Offset(cx + u * 2.0f - stride, footY),
        strokeWidth = u * 1.3f, cap = StrokeCap.Round)
    drawLine(jersey, Offset(cx, hipY), Offset(cx + leanX, shouldY),
        strokeWidth = u * 1.7f, cap = StrokeCap.Round)

    val bowlAngle = if (deliveryPhase < 0.15f) {
        -90f - runupPhase * 80f
    } else {
        -170f + (deliveryPhase / 0.65f).coerceIn(0f, 1f) * 250f
    }
    val sX      = cx + leanX
    val armEndX = sX      + cos(Math.toRadians(bowlAngle.toDouble())).toFloat() * armLen
    val armEndY = shouldY + sin(Math.toRadians(bowlAngle.toDouble())).toFloat() * armLen
    drawLine(jersey, Offset(sX, shouldY), Offset(armEndX, armEndY),
        strokeWidth = u * 1.2f, cap = StrokeCap.Round)

    if (deliveryPhase < 0.28f) {
        drawCircle(Color(0xFFD32F2F), radius = u * 1.1f, center = Offset(armEndX, armEndY))
        drawCircle(Color(0xFFF44336), radius = u * 0.44f,
            center = Offset(armEndX - u * 0.27f, armEndY - u * 0.27f))
    }

    val balAngle = bowlAngle + 158f
    drawLine(jersey, Offset(sX, shouldY),
        Offset(sX + cos(Math.toRadians(balAngle.toDouble())).toFloat() * armLen * 0.68f,
            shouldY + sin(Math.toRadians(balAngle.toDouble())).toFloat() * armLen * 0.68f),
        strokeWidth = u * 1.0f, cap = StrokeCap.Round)

    val hx = cx + leanX * 0.4f
    drawCircle(skin, headR, Offset(hx, headCY))
    // White cap (visible on green background)
    drawArc(Color.White.copy(alpha = 0.92f), 180f, 180f, useCenter = true,
        topLeft = Offset(hx - headR * 1.1f, headCY - headR * 1.0f),
        size    = Size(headR * 2.2f, headR * 1.5f))
    drawLine(Color(0xFFBBBBBB),
        Offset(hx - headR * 1.1f, headCY),
        Offset(hx + headR * 1.6f, headCY),
        strokeWidth = u * 0.55f, cap = StrokeCap.Round)
}

// ── Ball ──────────────────────────────────────────────────────────────────────
private fun DrawScope.drawBall(
    W: Float, H: Float, groundY: Float,
    bowlerX: Float, stumpsX: Float,
    deliveryPhase: Float, hitPhase: Float
) {
    if (deliveryPhase <= 0f) return

    val ballR = W * 0.015f
    val t     = deliveryPhase.coerceIn(0f, 1f)

    val startX    = bowlerX + W * 0.05f
    val startY    = groundY - size.height * 0.13f
    val pitchFrac = 0.42f
    val pitchX    = startX + (stumpsX - startX) * pitchFrac
    val pitchY    = groundY - H * 0.010f
    val endX      = stumpsX
    val endY      = groundY - size.height * 0.110f * 0.38f

    val bx: Float
    val by: Float
    if (t < pitchFrac) {
        val s = t / pitchFrac
        bx = startX + (pitchX - startX) * s
        by = startY + (pitchY - startY) * s + H * 0.022f * sin(s * PI.toFloat())
    } else {
        val s = (t - pitchFrac) / (1f - pitchFrac)
        bx = pitchX + (endX - pitchX) * s
        by = pitchY + (endY - pitchY) * s - H * 0.028f * sin(s * PI.toFloat())
    }

    val drawX = if (hitPhase > 0f) bx + W * 0.10f * hitPhase * hitPhase else bx
    val drawY = if (hitPhase > 0f) by - H * 0.04f * hitPhase else by

    // Trail
    if (hitPhase < 0.1f) {
        for (i in 1..5) {
            val tt = (t - i * 0.028f).coerceAtLeast(0f)
            val tx: Float; val ty: Float
            if (tt < pitchFrac) {
                val s = tt / pitchFrac
                tx = startX + (pitchX - startX) * s
                ty = startY + (pitchY - startY) * s + H * 0.022f * sin(s * PI.toFloat())
            } else {
                val s = (tt - pitchFrac) / (1f - pitchFrac)
                tx = pitchX + (endX - pitchX) * s
                ty = pitchY + (endY - pitchY) * s - H * 0.028f * sin(s * PI.toFloat())
            }
            drawCircle(Color(0xFFEF5350).copy(alpha = (1f - i / 6f) * 0.35f),
                radius = ballR * (1f - i * 0.12f).coerceAtLeast(0.3f),
                center = Offset(tx, ty))
        }
    }

    // Pitch scuff mark
    if (t > pitchFrac - 0.04f && t < pitchFrac + 0.08f) {
        val fade = 1f - abs(t - pitchFrac) / 0.06f
        drawOval(Color(0xFF5D4037).copy(alpha = fade * 0.28f),
            Offset(pitchX - ballR, groundY - ballR * 0.4f),
            Size(ballR * 2f, ballR * 0.45f))
    }

    drawCircle(Color(0xFFD32F2F), radius = ballR, center = Offset(drawX, drawY))
    drawCircle(Color(0xFFF44336), radius = ballR * 0.46f,
        center = Offset(drawX - ballR * 0.22f, drawY - ballR * 0.22f))

    rotate(t * 1260f, Offset(drawX, drawY)) {
        val seam = Path().apply {
            moveTo(drawX - ballR * 0.64f, drawY)
            cubicTo(drawX - ballR * 0.20f, drawY - ballR * 0.50f,
                drawX + ballR * 0.20f,  drawY + ballR * 0.50f,
                drawX + ballR * 0.64f,  drawY)
        }
        drawPath(seam, Color(0xFF880000),
            style = Stroke(width = ballR * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// ── Impact flash ──────────────────────────────────────────────────────────────
private fun DrawScope.drawImpact(stumpsX: Float, groundY: Float, hitPhase: Float, H: Float) {
    if (hitPhase <= 0f || hitPhase > 0.65f) return
    val stumpH = H * 0.110f
    val impCY  = groundY - stumpH * 0.38f
    val prog   = hitPhase / 0.65f

    drawCircle(Color(0xFFFFEB3B).copy(alpha = (1f - prog) * 0.70f),
        radius = size.width * 0.026f * (1f + prog * 3.5f),
        center = Offset(stumpsX, impCY))
    drawCircle(Color.White.copy(alpha = (1f - prog) * 0.88f),
        radius = size.width * 0.014f * (1f - prog * 0.3f),
        center = Offset(stumpsX, impCY))
    repeat(10) { r ->
        val angle = Math.toRadians(r * 36.0).toFloat()
        drawLine(Color(0xFFFFD600).copy(alpha = (1f - prog) * 0.86f),
            Offset(stumpsX + cos(angle) * size.width * 0.018f,
                impCY  + sin(angle) * size.width * 0.018f),
            Offset(stumpsX + cos(angle) * size.width * 0.050f * prog,
                impCY  + sin(angle) * size.width * 0.050f * prog),
            strokeWidth = 2.5f, cap = StrokeCap.Round)
    }
}

// ── Flying bails ──────────────────────────────────────────────────────────────
private fun DrawScope.drawFlyingBails(
    stumpsX: Float, groundY: Float, hitPhase: Float, W: Float, H: Float
) {
    if (hitPhase < 0.10f) return
    val stumpH = H * 0.110f
    val bailH  = stumpH * 0.062f
    val bailW  = W * 0.040f
    val prog   = ((hitPhase - 0.10f) / 0.90f).coerceIn(0f, 1f)
    val eased  = 1f - (1f - prog) * (1f - prog)

    val b1x = stumpsX - W * 0.03f - W * 0.19f * eased
    val b1y = groundY - stumpH - bailH - H * 0.20f * eased + H * 0.07f * eased * eased
    rotate(-108f * eased, Offset(b1x + bailW / 2f, b1y + bailH / 2f)) {
        drawRoundRect(Color(0xFFFFD700).copy(alpha = 1f - eased * 0.20f),
            Offset(b1x, b1y), Size(bailW, bailH), CornerRadius(bailH / 2f))
    }

    val b2x = stumpsX + W * 0.01f + W * 0.21f * eased
    val b2y = groundY - stumpH - bailH - H * 0.23f * eased + H * 0.08f * eased * eased
    rotate(118f * eased, Offset(b2x + bailW / 2f, b2y + bailH / 2f)) {
        drawRoundRect(Color(0xFFFFD700).copy(alpha = 1f - eased * 0.20f),
            Offset(b2x, b2y), Size(bailW, bailH), CornerRadius(bailH / 2f))
    }
}