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
import androidx.compose.ui.graphics.Brush
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

// ── Palette ───────────────────────────────────────────────────────────────────
private val NightSky     = Color(0xFF060B18)
private val StandDark    = Color(0xFF0D1B2A)
private val StandMid     = Color(0xFF162233)
private val FieldGreen   = Color(0xFF1B6B25)
private val PitchTan     = Color(0xFFCB9E5A)
private val FloodWhite   = Color(0xFFFFFDE7)
private val FloodGlow    = Color(0xFFFFFF99)
private val GroundShadow = Color(0xFF113A18)

// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CricketAnimationScreen(onFinished: () -> Unit) {
    val totalMs = 5_000L
    LaunchedEffect(Unit) { delay(totalMs); onFinished() }

    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(totalMs.toInt(), easing = LinearEasing)) }

    Box(
        modifier = Modifier.fillMaxSize().background(NightSky),
        contentAlignment = Alignment.Center
    ) {
        CricketMatchAnimation(progress = progress.value, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun CricketMatchAnimation(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val W = size.width
        val H = size.height

        // ── Ground is a fixed fraction of height ──────────────────────────────
        // Everything is measured from groundY so nothing floats or clips.
        val groundY  = H * 0.72f

        // ── Pitch geometry defined once, shared by pitch + stump + ball ───────
        // Pitch sits FLUSH on the ground: top edge at groundY - pitchH, bottom = groundY
        val pitchH   = H * 0.028f          // visible raised strip height
        val pitchTop = groundY - pitchH    // top of pitch rectangle

        // Stump roots are exactly at groundY
        val stumpsX  = W * 0.76f
        val bowlerX  = W * 0.18f
        val batsmanX = stumpsX - W * 0.11f

        val runupPhase    = (progress / 0.38f).coerceIn(0f, 1f)
        val deliveryPhase = ((progress - 0.26f) / 0.46f).coerceIn(0f, 1f)
        val hitPhase      = ((progress - 0.70f) / 0.30f).coerceIn(0f, 1f)

        drawNightSky(W, H)
        drawStadiumStands(W, H, groundY)
        drawFloodlightTowers(W, H, groundY, progress)
        drawOutfield(W, H, groundY)
        // Pitch drawn AFTER outfield so it sits on top cleanly
        drawPitch(W, groundY, pitchTop, pitchH, bowlerX, stumpsX)
        drawStumps(stumpsX, groundY, hitPhase)
        drawBatsman(batsmanX, groundY, deliveryPhase, hitPhase)
        drawBowler(bowlerX, groundY, runupPhase, deliveryPhase)
        drawBall(W, H, groundY, bowlerX, stumpsX, deliveryPhase, hitPhase)
        drawImpact(stumpsX, groundY, hitPhase)
        drawFlyingBails(stumpsX, groundY, hitPhase, W, H)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sky + stars
// ─────────────────────────────────────────────────────────────────────────────
private fun DrawScope.drawNightSky(W: Float, H: Float) {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF020509), Color(0xFF0A1628), Color(0xFF0F2040)),
            startY = 0f, endY = H * 0.75f
        ),
        size = Size(W, H)
    )
    val stars = listOf(
        0.05f to 0.04f, 0.14f to 0.08f, 0.23f to 0.03f, 0.33f to 0.07f,
        0.45f to 0.02f, 0.55f to 0.09f, 0.65f to 0.04f, 0.76f to 0.06f,
        0.88f to 0.02f, 0.92f to 0.10f, 0.08f to 0.15f, 0.19f to 0.18f,
        0.38f to 0.13f, 0.50f to 0.18f, 0.62f to 0.12f, 0.80f to 0.16f,
        0.72f to 0.20f, 0.28f to 0.21f, 0.41f to 0.23f, 0.60f to 0.24f,
        0.10f to 0.26f, 0.85f to 0.22f, 0.95f to 0.14f, 0.02f to 0.20f
    )
    stars.forEach { (fx, fy) ->
        drawCircle(Color.White.copy(alpha = 0.45f + fx * 0.35f),
            radius = W * 0.0013f + fy * W * 0.0018f,
            center = Offset(fx * W, fy * H))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stadium stands
// ─────────────────────────────────────────────────────────────────────────────
private fun DrawScope.drawStadiumStands(W: Float, H: Float, groundY: Float) {
    val roofH  = H * 0.36f
    val standH = groundY - roofH

    fun drawStand(pathFn: Path.() -> Unit, rowRange: ClosedRange<Float>, rowX: (Float) -> Pair<Float, Float>) {
        val p = Path().apply(pathFn)
        drawPath(p, StandDark)
        for (row in 0..7) {
            val f    = row / 8f
            val rowY = groundY - standH * 0.15f - standH * 0.72f * f
            val (x0, x1) = rowX(f)
            drawLine(StandMid.copy(alpha = 0.70f), Offset(x0, rowY), Offset(x1, rowY), strokeWidth = H * 0.011f)
            val bumps = 8 + row
            val bW = (x1 - x0) / bumps
            for (b in 0 until bumps) {
                drawCircle(Color(0xFF1E2D40).copy(alpha = 0.80f),
                    radius = H * 0.007f,
                    center = Offset(x0 + b * bW + bW * 0.5f, rowY - H * 0.007f))
            }
        }
    }

    // Left stand
    drawStand(
        pathFn = {
            moveTo(0f, groundY); lineTo(0f, roofH * 0.85f)
            cubicTo(W * 0.08f, roofH * 0.65f, W * 0.18f, roofH * 0.78f, W * 0.28f, roofH * 0.88f)
            lineTo(W * 0.28f, groundY); close()
        },
        rowRange = 0f..1f,
        rowX = { f -> (W * 0.01f + f * W * 0.02f) to (W * 0.26f - f * W * 0.05f) }
    )
    drawLine(Color(0xFF1A3A5C), Offset(0f, roofH * 0.85f), Offset(W * 0.28f, roofH * 0.88f), strokeWidth = H * 0.017f)

    // Right stand
    drawStand(
        pathFn = {
            moveTo(W, groundY); lineTo(W, roofH * 0.82f)
            cubicTo(W * 0.92f, roofH * 0.62f, W * 0.82f, roofH * 0.75f, W * 0.72f, roofH * 0.86f)
            lineTo(W * 0.72f, groundY); close()
        },
        rowRange = 0f..1f,
        rowX = { f -> (W * 0.74f + f * W * 0.05f) to (W * 0.99f - f * W * 0.02f) }
    )
    drawLine(Color(0xFF1A3A5C), Offset(W * 0.72f, roofH * 0.86f), Offset(W, roofH * 0.82f), strokeWidth = H * 0.017f)

    // Scoreboard
    val sbW = W * 0.20f; val sbH = H * 0.09f
    val sbX = (W - sbW) / 2f; val sbY = roofH * 0.52f
    drawRoundRect(Color(0xFF0D1F35), Offset(sbX, sbY), Size(sbW, sbH), CornerRadius(sbH * 0.1f))
    drawRoundRect(Color(0xFF1A3A5C), Offset(sbX + sbW * 0.05f, sbY + sbH * 0.13f),
        Size(sbW * 0.90f, sbH * 0.74f), CornerRadius(sbH * 0.06f))
    drawRoundRect(Color(0xFFFFD600).copy(alpha = 0.85f),
        Offset(sbX + sbW * 0.09f, sbY + sbH * 0.22f), Size(sbW * 0.52f, sbH * 0.18f), CornerRadius(sbH * 0.04f))
    drawRoundRect(Color(0xFF4FC3F7).copy(alpha = 0.70f),
        Offset(sbX + sbW * 0.09f, sbY + sbH * 0.52f), Size(sbW * 0.38f, sbH * 0.15f), CornerRadius(sbH * 0.04f))
}

// ─────────────────────────────────────────────────────────────────────────────
// Floodlight towers
// ─────────────────────────────────────────────────────────────────────────────
private fun DrawScope.drawFloodlightTowers(W: Float, H: Float, groundY: Float, progress: Float) {
    val towerXs  = listOf(W * 0.04f, W * 0.22f, W * 0.78f, W * 0.96f)
    val towerHs  = listOf(H * 0.60f, H * 0.53f, H * 0.53f, H * 0.60f)
    val poleW    = W * 0.010f
    val bracketW = W * 0.050f
    val headH    = H * 0.036f

    val flicker = 0.78f +
            (sin(progress * 73f) * 0.5f + 0.5f) * 0.12f +
            (sin(progress * 137f + 1.1f) * 0.5f + 0.5f) * 0.08f +
            (sin(progress * 211f + 2.3f) * 0.5f + 0.5f) * 0.06f

    towerXs.forEachIndexed { i, tx ->
        val tH   = towerHs[i]
        val topY = groundY - tH
        val isTall = i == 0 || i == 3

        drawLine(Color(0xFF8B9AA8), Offset(tx, groundY), Offset(tx, topY),
            strokeWidth = poleW, cap = StrokeCap.Round)
        // Mid brace
        val midY = topY + tH * 0.38f
        drawLine(Color(0xFF6A7888),
            Offset(tx - poleW * 2.5f, midY + tH * 0.07f),
            Offset(tx + poleW * 2.5f, midY - tH * 0.07f),
            strokeWidth = poleW * 0.6f)

        val armDir = if (i < 2) 1f else -1f
        val armEndX = tx + armDir * bracketW
        drawLine(Color(0xFF8B9AA8), Offset(tx, topY), Offset(armEndX, topY + headH * 0.4f),
            strokeWidth = poleW * 0.8f, cap = StrokeCap.Round)

        val headX = armEndX - bracketW * 0.5f
        drawRoundRect(Color(0xFF3A4A58), Offset(headX, topY + headH * 0.3f),
            Size(bracketW, headH), CornerRadius(headH * 0.2f))

        val bulbs = if (isTall) 6 else 5
        val bSpacing = bracketW / (bulbs + 1)
        for (b in 1..bulbs) {
            val bx = headX + b * bSpacing
            val by = topY + headH * 0.75f
            val bf = flicker - sin(progress * (41f + b * 17f + i * 31f)) * 0.04f
            drawCircle(FloodWhite.copy(alpha = bf.coerceIn(0.3f, 1.0f)),
                radius = W * 0.007f, center = Offset(bx, by))
        }

        val glowAlpha = (flicker * 0.17f).coerceIn(0f, 0.24f)
        val conePath = Path().apply {
            moveTo(armEndX - bracketW * 0.6f, topY + headH)
            lineTo(armEndX - bracketW * 3.2f, groundY)
            lineTo(armEndX + bracketW * 3.2f, groundY)
            lineTo(armEndX + bracketW * 0.6f, topY + headH)
            close()
        }
        drawPath(conePath, Brush.verticalGradient(
            listOf(FloodGlow.copy(alpha = glowAlpha), FloodGlow.copy(alpha = glowAlpha * 0.3f), Color.Transparent),
            startY = topY + headH, endY = groundY))
        drawCircle(FloodGlow.copy(alpha = (flicker * 0.42f).coerceIn(0f, 0.48f)),
            radius = bracketW * 0.75f, center = Offset(armEndX, topY + headH * 0.75f))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Outfield
// ─────────────────────────────────────────────────────────────────────────────
private fun DrawScope.drawOutfield(W: Float, H: Float, groundY: Float) {
    // Full field ellipse
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF1E7A2A), FieldGreen, Color(0xFF145020)),
            center = Offset(W * 0.5f, groundY + H * 0.12f),
            radius = W * 0.62f
        ),
        topLeft = Offset(-W * 0.12f, groundY - H * 0.04f),
        size    = Size(W * 1.24f, H * 0.35f)
    )
    // Mowing stripes
    for (s in 0..12) {
        if (s % 2 == 0) {
            val sx = W * 0.02f + s * W * 0.076f
            drawLine(Color.White.copy(alpha = 0.05f),
                Offset(sx, groundY), Offset(sx + W * 0.035f, groundY + H * 0.07f),
                strokeWidth = W * 0.022f)
        }
    }
    // Hard ground line — single crisp line, no shadow stripe
    drawLine(GroundShadow, Offset(0f, groundY), Offset(W, groundY), strokeWidth = 2.5f)
}

// ─────────────────────────────────────────────────────────────────────────────
// Pitch — flush with ground, shared geometry with stumps + ball
// ─────────────────────────────────────────────────────────────────────────────
private fun DrawScope.drawPitch(
    W: Float, groundY: Float, pitchTop: Float, pitchH: Float,
    bowlerX: Float, stumpsX: Float
) {
    val pLeft  = bowlerX - W * 0.025f
    val pRight = stumpsX + W * 0.025f
    val pWidth = pRight - pLeft

    // Base tan strip
    drawRect(
        color   = PitchTan,
        topLeft = Offset(pLeft, pitchTop),
        size    = Size(pWidth, pitchH)
    )
    // Subtle worn centre
    drawRect(
        brush   = Brush.verticalGradient(
            colors = listOf(Color(0xFFD4A96A).copy(alpha = 0.35f), Color.Transparent),
            startY = pitchTop, endY = groundY
        ),
        topLeft = Offset(pLeft + pWidth * 0.15f, pitchTop),
        size    = Size(pWidth * 0.70f, pitchH)
    )
    // Top edge highlight so pitch "lifts" off the ground
    drawLine(Color(0xFFEAC07A), Offset(pLeft, pitchTop), Offset(pRight, pitchTop), strokeWidth = 1.5f)

    // Crease lines — exactly at groundY so they sit on the ground surface
    val creaseY0 = pitchTop + 1f
    val creaseY1 = groundY
    val bowlerCreaseX = bowlerX + W * 0.042f
    val batsmanCreaseX = stumpsX - W * 0.014f

    drawLine(Color.White.copy(alpha = 0.90f),
        Offset(bowlerCreaseX, creaseY0), Offset(bowlerCreaseX, creaseY1), strokeWidth = 1.8f)
    drawLine(Color.White.copy(alpha = 0.90f),
        Offset(batsmanCreaseX, creaseY0), Offset(batsmanCreaseX, creaseY1), strokeWidth = 1.8f)

    // Popping crease (horizontal) at ground level
    val popOverhang = W * 0.018f
    drawLine(Color.White.copy(alpha = 0.75f),
        Offset(batsmanCreaseX - popOverhang, groundY - 1f),
        Offset(batsmanCreaseX + popOverhang, groundY - 1f),
        strokeWidth = 1.5f)
    drawLine(Color.White.copy(alpha = 0.75f),
        Offset(bowlerCreaseX - popOverhang, groundY - 1f),
        Offset(bowlerCreaseX + popOverhang, groundY - 1f),
        strokeWidth = 1.5f)
}

// ─────────────────────────────────────────────────────────────────────────────
// Stumps — rooted at groundY, stump height scales with H
// ─────────────────────────────────────────────────────────────────────────────
private fun DrawScope.drawStumps(cx: Float, groundY: Float, hitPhase: Float) {
    // Stump height a fixed fraction of screen height for consistent proportion
    val stumpH = size.height * 0.095f
    val stumpW = size.width  * 0.010f
    val gap    = size.width  * 0.018f
    val bailH  = stumpH * 0.060f
    val bailW  = gap + stumpW * 0.8f
    val xs     = floatArrayOf(cx - gap, cx, cx + gap)

    val e     = 1f - (1f - hitPhase) * (1f - hitPhase)
    val flyDX = floatArrayOf(-size.width * 0.12f * e, size.width * 0.003f * e, size.width * 0.14f * e)
    val flyDY = floatArrayOf(-size.height * 0.13f * e, -size.height * 0.18f * e, -size.height * 0.12f * e)
    val flyR  = floatArrayOf(-48f * e, 4f * e, 52f * e)

    xs.forEachIndexed { i, sx ->
        withTransform({
            translate(flyDX[i], flyDY[i])
            rotate(flyR[i], Offset(sx, groundY))
        }) {
            // Stump body — top at groundY - stumpH, bottom at groundY
            drawRoundRect(Color(0xFFF5DEB3),
                topLeft      = Offset(sx - stumpW / 2f, groundY - stumpH),
                size         = Size(stumpW, stumpH),
                cornerRadius = CornerRadius(stumpW / 2f))
            // Grain line
            drawLine(Color(0xFFBCAAA4).copy(alpha = 0.40f),
                Offset(sx, groundY - stumpH + stumpW * 1.5f),
                Offset(sx, groundY - stumpW * 1.5f),
                strokeWidth = stumpW * 0.15f)
            // Bail groove cap
            drawCircle(Color(0xFFFFECB3), radius = stumpW * 0.55f,
                center = Offset(sx, groundY - stumpH))
        }
    }

    // Bails sit on top of stumps only while upright
    if (hitPhase < 0.10f) {
        val topY = groundY - stumpH - bailH
        for (b in 0..1) {
            drawRoundRect(Color(0xFFFFD700),
                topLeft      = Offset(xs[b] - stumpW * 0.08f, topY),
                size         = Size(bailW, bailH),
                cornerRadius = CornerRadius(bailH / 2f))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Batsman — smaller (figH reduced from 0.160 → 0.115)
// ─────────────────────────────────────────────────────────────────────────────
private fun DrawScope.drawBatsman(
    cx: Float, groundY: Float,
    deliveryPhase: Float, hitPhase: Float
) {
    val figH      = size.height * 0.115f    // ← smaller
    val u         = figH / 20f
    val headR     = u * 2.0f
    val bodyH     = u * 6.0f
    val legH      = u * 5.0f
    val armLen    = u * 2.8f
    val handleLen = u * 2.2f
    val bladeLen  = u * 5.5f
    val batW      = u * 1.3f
    val handleW   = batW * 0.58f
    val footY     = groundY
    val hipY      = footY   - legH
    val shouldY   = hipY    - bodyH
    val headCY    = shouldY - headR * 1.2f

    val jersey = Color(0xFF1A237E)
    val skin   = Color(0xFFD7A77A)
    val helmet = Color(0xFF0D47A1)
    val pad    = Color(0xFFEEEEEE)
    val blade  = Color(0xFF8D6E63)
    val grip   = Color(0xFF4E342E)

    drawLine(jersey, Offset(cx + u * 0.8f, hipY), Offset(cx + u * 2.2f, footY), strokeWidth = u * 1.3f, cap = StrokeCap.Round)
    drawLine(jersey, Offset(cx - u * 0.8f, hipY), Offset(cx - u * 2.0f, footY), strokeWidth = u * 1.3f, cap = StrokeCap.Round)
    drawRoundRect(pad, Offset(cx - u * 3.4f, hipY + u * 0.5f), Size(u * 1.6f, legH * 0.84f), CornerRadius(u * 0.4f))
    drawRoundRect(pad, Offset(cx + u * 0.6f,  hipY + u * 0.5f), Size(u * 1.6f, legH * 0.84f), CornerRadius(u * 0.4f))
    drawLine(jersey, Offset(cx, hipY), Offset(cx, shouldY), strokeWidth = u * 1.8f, cap = StrokeCap.Round)

    val batAngle = when {
        deliveryPhase < 0.55f -> -80f
        hitPhase > 0f         ->  35f
        else -> -80f + ((deliveryPhase - 0.55f) / 0.45f) * 115f
    }
    val pivX = cx - u * 0.6f
    val pivY = shouldY + u * 1.0f
    rotate(batAngle, Offset(pivX, pivY)) {
        drawLine(skin, Offset(pivX, pivY), Offset(pivX, pivY + armLen), strokeWidth = u * 1.0f, cap = StrokeCap.Round)
        val handleTop = pivY + armLen
        drawRoundRect(grip, Offset(pivX - handleW / 2f, handleTop), Size(handleW, handleLen), CornerRadius(handleW / 2f))
        repeat(4) { g ->
            drawLine(Color(0xFF6D4C41).copy(alpha = 0.55f),
                Offset(pivX - handleW / 2f, handleTop + u * 0.30f + g * u * 0.45f),
                Offset(pivX + handleW / 2f, handleTop + u * 0.30f + g * u * 0.45f),
                strokeWidth = u * 0.18f)
        }
        val bladeTop = handleTop + handleLen
        drawRoundRect(blade, Offset(pivX - batW / 2f, bladeTop), Size(batW, bladeLen), CornerRadius(batW / 4f))
        drawLine(Color(0xFFBCAAA4).copy(alpha = 0.50f),
            Offset(pivX - batW / 2f + u * 0.16f, bladeTop + u * 0.3f),
            Offset(pivX - batW / 2f + u * 0.16f, bladeTop + bladeLen * 0.85f),
            strokeWidth = u * 0.20f)
    }
    drawLine(skin, Offset(cx + u * 0.5f, shouldY + u * 1.2f), Offset(cx - u * 1.0f, shouldY + u * 3.8f),
        strokeWidth = u * 0.9f, cap = StrokeCap.Round)

    // Neck
    drawLine(skin, Offset(cx, shouldY), Offset(cx, shouldY - u * 0.6f), strokeWidth = u * 1.0f, cap = StrokeCap.Round)
    // Head
    drawCircle(skin, headR, Offset(cx, headCY))
    drawArc(helmet.copy(alpha = 0.93f), 180f, 180f, useCenter = true,
        topLeft = Offset(cx - headR * 1.1f, headCY - headR * 1.05f), size = Size(headR * 2.2f, headR * 1.75f))
    repeat(4) { g ->
        drawLine(Color(0xFF9E9E9E),
            Offset(cx - headR * 0.72f + g * headR * 0.48f, headCY - headR * 0.04f),
            Offset(cx - headR * 0.72f + g * headR * 0.48f, headCY + headR * 0.62f),
            strokeWidth = u * 0.26f)
    }
    drawLine(Color(0xFF9E9E9E), Offset(cx - headR * 0.78f, headCY + headR * 0.26f),
        Offset(cx + headR * 0.78f, headCY + headR * 0.26f), strokeWidth = u * 0.18f)
    drawLine(helmet, Offset(cx - headR * 1.55f, headCY), Offset(cx + headR * 1.1f, headCY),
        strokeWidth = u * 0.65f, cap = StrokeCap.Round)
}

// ─────────────────────────────────────────────────────────────────────────────
// Bowler — smaller (figH reduced from 0.160 → 0.115), head fully attached
// ─────────────────────────────────────────────────────────────────────────────
private fun DrawScope.drawBowler(
    cx: Float, groundY: Float,
    runupPhase: Float, deliveryPhase: Float
) {
    val figH   = size.height * 0.115f    // ← smaller
    val u      = figH / 20f
    val headR  = u * 2.0f
    val bodyH  = u * 6.0f
    val legH   = u * 5.0f
    val armLen = u * 4.0f

    val leanX   = (deliveryPhase * u * 3.0f).coerceAtMost(u * 3.0f)
    val footY   = groundY
    val hipY    = footY   - legH
    val shouldY = hipY    - bodyH + leanX * 0.3f
    // Head is parented to shoulder — always attached
    val headCX  = cx + leanX
    val neckLen = u * 0.7f
    val headCY  = shouldY - neckLen - headR

    val jersey = Color(0xFF0D47A1)
    val skin   = Color(0xFFD7A77A)

    val stride = sin(runupPhase * PI.toFloat() * 5f) * u * 2.0f
    drawLine(jersey, Offset(cx, hipY), Offset(cx - u * 2.0f + stride, footY), strokeWidth = u * 1.3f, cap = StrokeCap.Round)
    drawLine(jersey, Offset(cx, hipY), Offset(cx + u * 2.0f - stride, footY), strokeWidth = u * 1.3f, cap = StrokeCap.Round)
    drawLine(jersey, Offset(cx, hipY), Offset(cx + leanX, shouldY), strokeWidth = u * 1.7f, cap = StrokeCap.Round)

    val bowlAngle = if (deliveryPhase < 0.15f) {
        -90f - runupPhase * 80f
    } else {
        -170f + (deliveryPhase / 0.65f).coerceIn(0f, 1f) * 250f
    }
    val sX      = cx + leanX
    val armEndX = sX      + cos(Math.toRadians(bowlAngle.toDouble())).toFloat() * armLen
    val armEndY = shouldY + sin(Math.toRadians(bowlAngle.toDouble())).toFloat() * armLen
    drawLine(jersey, Offset(sX, shouldY), Offset(armEndX, armEndY), strokeWidth = u * 1.2f, cap = StrokeCap.Round)

    // Ball in hand ONLY before release
    if (deliveryPhase < 0.05f) {
        drawCircle(Color(0xFFD32F2F), radius = u * 1.0f, center = Offset(armEndX, armEndY))
        drawCircle(Color(0xFFF44336), radius = u * 0.40f, center = Offset(armEndX - u * 0.24f, armEndY - u * 0.24f))
    }

    // Balance arm
    val balAngle = bowlAngle + 158f
    drawLine(jersey, Offset(sX, shouldY),
        Offset(sX + cos(Math.toRadians(balAngle.toDouble())).toFloat() * armLen * 0.68f,
            shouldY + sin(Math.toRadians(balAngle.toDouble())).toFloat() * armLen * 0.68f),
        strokeWidth = u * 1.0f, cap = StrokeCap.Round)

    // Neck
    drawLine(skin, Offset(headCX, shouldY), Offset(headCX, shouldY - neckLen), strokeWidth = u * 1.0f, cap = StrokeCap.Round)
    // Head — always at headCX/headCY derived from body
    drawCircle(skin, headR, Offset(headCX, headCY))
    drawArc(Color.White.copy(alpha = 0.92f), 180f, 180f, useCenter = true,
        topLeft = Offset(headCX - headR * 1.1f, headCY - headR * 1.0f), size = Size(headR * 2.2f, headR * 1.5f))
    drawLine(Color(0xFFBBBBBB), Offset(headCX - headR * 1.1f, headCY), Offset(headCX + headR * 1.6f, headCY),
        strokeWidth = u * 0.52f, cap = StrokeCap.Round)
}

// ─────────────────────────────────────────────────────────────────────────────
// Ball in flight
// ─────────────────────────────────────────────────────────────────────────────
private fun DrawScope.drawBall(
    W: Float, H: Float, groundY: Float,
    bowlerX: Float, stumpsX: Float,
    deliveryPhase: Float, hitPhase: Float
) {
    if (deliveryPhase <= 0.05f) return
    val t = ((deliveryPhase - 0.05f) / 0.95f).coerceIn(0f, 1f)
    val ballR     = W * 0.014f
    val startX    = bowlerX + W * 0.05f
    val startY    = groundY - size.height * 0.11f
    val pitchFrac = 0.42f
    val pitchX    = startX + (stumpsX - startX) * pitchFrac
    val pitchY    = groundY                        // ball bounces exactly on groundY
    val endX      = stumpsX
    val endY      = groundY - size.height * 0.040f

    val bx: Float; val by: Float
    if (t < pitchFrac) {
        val s = t / pitchFrac
        bx = startX + (pitchX - startX) * s
        by = startY + (pitchY - startY) * s + H * 0.020f * sin(s * PI.toFloat())
    } else {
        val s = (t - pitchFrac) / (1f - pitchFrac)
        bx = pitchX + (endX - pitchX) * s
        by = pitchY + (endY - pitchY) * s - H * 0.025f * sin(s * PI.toFloat())
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
                ty = startY + (pitchY - startY) * s + H * 0.020f * sin(s * PI.toFloat())
            } else {
                val s = (tt - pitchFrac) / (1f - pitchFrac)
                tx = pitchX + (endX - pitchX) * s
                ty = pitchY + (endY - pitchY) * s - H * 0.025f * sin(s * PI.toFloat())
            }
            drawCircle(Color(0xFFEF5350).copy(alpha = (1f - i / 6f) * 0.30f),
                radius = ballR * (1f - i * 0.12f).coerceAtLeast(0.3f),
                center = Offset(tx, ty))
        }
    }

    // Pitch mark
    if (t > pitchFrac - 0.04f && t < pitchFrac + 0.08f) {
        val fade = 1f - abs(t - pitchFrac) / 0.06f
        drawOval(Color(0xFF5D4037).copy(alpha = fade * 0.25f),
            Offset(pitchX - ballR, groundY - ballR * 0.35f),
            Size(ballR * 2f, ballR * 0.40f))
    }

    drawCircle(Color(0xFFD32F2F), radius = ballR, center = Offset(drawX, drawY))
    drawCircle(Color(0xFFF44336), radius = ballR * 0.44f,
        center = Offset(drawX - ballR * 0.20f, drawY - ballR * 0.20f))
    rotate(t * 1260f, Offset(drawX, drawY)) {
        val seam = Path().apply {
            moveTo(drawX - ballR * 0.62f, drawY)
            cubicTo(drawX - ballR * 0.18f, drawY - ballR * 0.48f,
                drawX + ballR * 0.18f, drawY + ballR * 0.48f,
                drawX + ballR * 0.62f, drawY)
        }
        drawPath(seam, Color(0xFF880000),
            style = Stroke(width = ballR * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Impact flash
// ─────────────────────────────────────────────────────────────────────────────
private fun DrawScope.drawImpact(stumpsX: Float, groundY: Float, hitPhase: Float) {
    if (hitPhase <= 0f || hitPhase > 0.65f) return
    val stumpH = size.height * 0.095f
    val impCY  = groundY - stumpH * 0.35f
    val prog   = hitPhase / 0.65f

    drawCircle(Color(0xFFFFEB3B).copy(alpha = (1f - prog) * 0.68f),
        radius = size.width * 0.024f * (1f + prog * 3.5f), center = Offset(stumpsX, impCY))
    drawCircle(Color.White.copy(alpha = (1f - prog) * 0.85f),
        radius = size.width * 0.013f * (1f - prog * 0.3f), center = Offset(stumpsX, impCY))
    repeat(10) { r ->
        val angle = Math.toRadians(r * 36.0).toFloat()
        drawLine(Color(0xFFFFD600).copy(alpha = (1f - prog) * 0.84f),
            Offset(stumpsX + cos(angle) * size.width * 0.016f, impCY + sin(angle) * size.width * 0.016f),
            Offset(stumpsX + cos(angle) * size.width * 0.046f * prog, impCY + sin(angle) * size.width * 0.046f * prog),
            strokeWidth = 2.2f, cap = StrokeCap.Round)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Flying bails
// ─────────────────────────────────────────────────────────────────────────────
private fun DrawScope.drawFlyingBails(
    stumpsX: Float, groundY: Float, hitPhase: Float, W: Float, H: Float
) {
    if (hitPhase < 0.10f) return
    val stumpH = H * 0.095f
    val bailH  = stumpH * 0.060f
    val bailW  = W * 0.036f
    val prog   = ((hitPhase - 0.10f) / 0.90f).coerceIn(0f, 1f)
    val eased  = 1f - (1f - prog) * (1f - prog)

    val b1x = stumpsX - W * 0.03f - W * 0.18f * eased
    val b1y = groundY - stumpH - bailH - H * 0.18f * eased + H * 0.06f * eased * eased
    rotate(-108f * eased, Offset(b1x + bailW / 2f, b1y + bailH / 2f)) {
        drawRoundRect(Color(0xFFFFD700).copy(alpha = 1f - eased * 0.20f),
            Offset(b1x, b1y), Size(bailW, bailH), CornerRadius(bailH / 2f))
    }
    val b2x = stumpsX + W * 0.01f + W * 0.20f * eased
    val b2y = groundY - stumpH - bailH - H * 0.21f * eased + H * 0.07f * eased * eased
    rotate(118f * eased, Offset(b2x + bailW / 2f, b2y + bailH / 2f)) {
        drawRoundRect(Color(0xFFFFD700).copy(alpha = 1f - eased * 0.20f),
            Offset(b2x, b2y), Size(bailW, bailH), CornerRadius(bailH / 2f))
    }
}