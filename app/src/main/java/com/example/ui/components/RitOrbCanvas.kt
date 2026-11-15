package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import com.example.ai.RitOrbState
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CyanListening
import com.example.ui.theme.DeepIndigo
import com.example.ui.theme.IrisGlow
import com.example.ui.theme.MagentaPulse
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.SoftPinkAccent
import com.example.ui.theme.VioletPrimary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RitOrbCanvas(
    orbState: RitOrbState,
    isMuted: Boolean,
    contentDesc: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "rit_orb")

    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (orbState) {
                    RitOrbState.SPEAKING -> 1800
                    RitOrbState.THINKING, RitOrbState.CONNECTING -> 1400
                    RitOrbState.LISTENING -> 2200
                    RitOrbState.IDLE -> 4200
                    RitOrbState.DISCONNECTED -> 5000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_phase"
    )

    val breathe by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (orbState) {
                    RitOrbState.SPEAKING -> 550
                    RitOrbState.LISTENING -> 900
                    RitOrbState.THINKING, RitOrbState.CONNECTING -> 800
                    else -> 2400
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_breathe"
    )

    val rippleProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_ripple"
    )

    val targetCoreColor = when {
        isMuted && orbState == RitOrbState.LISTENING -> Color(0xFF475569)
        orbState == RitOrbState.LISTENING -> CyanListening
        orbState == RitOrbState.SPEAKING -> SoftPinkAccent
        orbState == RitOrbState.THINKING || orbState == RitOrbState.CONNECTING -> IrisGlow
        orbState == RitOrbState.DISCONNECTED -> AmberWarn
        else -> VioletPrimary
    }
    val primaryColor by animateColorAsState(
        targetValue = targetCoreColor,
        animationSpec = tween(450),
        label = "core_color"
    )

    val secondaryColor by animateColorAsState(
        targetValue = when (orbState) {
            RitOrbState.SPEAKING -> MagentaPulse
            RitOrbState.LISTENING -> VioletPrimary
            RitOrbState.THINKING, RitOrbState.CONNECTING -> SoftPinkAccent
            RitOrbState.DISCONNECTED -> RoseAlert
            else -> SoftPinkAccent
        },
        animationSpec = tween(450),
        label = "sec_color"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .sizeIn(minWidth = 220.dp, minHeight = 220.dp, maxWidth = 340.dp, maxHeight = 340.dp)
            .aspectRatio(1f)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = primaryColor),
                onClick = onClick
            )
            .semantics { contentDescription = contentDesc },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2f, h / 2f)
            val baseRadius = minOf(w, h) * 0.28f

            // 1. Ambient Cosmic Outer Halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = if (orbState == RitOrbState.DISCONNECTED) 0.14f else 0.34f),
                        secondaryColor.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = minOf(w, h) * 0.50f * breathe
                ),
                center = center,
                radius = minOf(w, h) * 0.50f * breathe
            )

            // 2. State-specific outer energy layers
            when (orbState) {
                RitOrbState.LISTENING -> {
                    // Dual expanding listening wave rings
                    for (i in 0..1) {
                        val p = (rippleProgress + i * 0.5f) % 1f
                        val ringRadius = baseRadius * (1.05f + p * 0.72f)
                        val alpha = (1f - p) * 0.45f
                        drawCircle(
                            color = CyanListening.copy(alpha = alpha),
                            radius = ringRadius,
                            center = center,
                            style = Stroke(width = 3.dp.toPx() * (1f - p * 0.4f))
                        )
                    }
                }

                RitOrbState.SPEAKING -> {
                    // Harmonic vocal equalizer rings around the orb
                    val points = 72
                    for (layer in 0..1) {
                        val path = Path()
                        val layerScale = if (layer == 0) 1.24f else 1.40f
                        val layerAmp = if (layer == 0) 0.14f else 0.09f
                        val layerFreq = if (layer == 0) 5f else 7f
                        for (i in 0..points) {
                            val angle = (i.toFloat() / points) * (2f * PI.toFloat())
                            val harmonic = sin(angle * layerFreq + phase * (if (layer == 0) 2f else -3f)) *
                                cos(angle * 2f - phase)
                            val r = baseRadius * (layerScale + harmonic * layerAmp * breathe)
                            val x = center.x + r * cos(angle)
                            val y = center.y + r * sin(angle)
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        path.close()
                        drawPath(
                            path = path,
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    SoftPinkAccent.copy(alpha = 0.75f),
                                    VioletPrimary.copy(alpha = 0.70f),
                                    MagentaPulse.copy(alpha = 0.75f),
                                    SoftPinkAccent.copy(alpha = 0.75f)
                                ),
                                center = center
                            ),
                            style = Stroke(width = (3.5f - layer).dp.toPx())
                        )
                    }
                }

                RitOrbState.THINKING, RitOrbState.CONNECTING -> {
                    // Rotating orbital aurora arcs
                    val arcRadius = baseRadius * 1.32f
                    val sweepAngleDeg = (phase * 180f / PI.toFloat())
                    for (i in 0..2) {
                        val startAngle = sweepAngleDeg + i * 120f
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(IrisGlow, SoftPinkAccent, CyanListening, IrisGlow),
                                center = center
                            ),
                            startAngle = startAngle,
                            sweepAngle = 74f,
                            useCenter = false,
                            topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                            size = androidx.compose.ui.geometry.Size(arcRadius * 2f, arcRadius * 2f),
                            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                RitOrbState.IDLE -> {
                    // Floating orbital star particles + subtle ring
                    val ringR = baseRadius * 1.28f
                    drawCircle(
                        color = VioletPrimary.copy(alpha = 0.25f),
                        radius = ringR,
                        center = center,
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                    for (i in 0..5) {
                        val a = phase + i * (PI.toFloat() / 3f)
                        val orbitR = baseRadius * (1.28f + 0.08f * sin(phase * 2f + i))
                        val dotPos = Offset(center.x + orbitR * cos(a), center.y + orbitR * sin(a))
                        drawCircle(
                            color = if (i % 2 == 0) SoftPinkAccent.copy(alpha = 0.7f) else IrisGlow.copy(alpha = 0.7f),
                            radius = 3.dp.toPx(),
                            center = dotPos
                        )
                    }
                }

                RitOrbState.DISCONNECTED -> {
                    drawCircle(
                        color = AmberWarn.copy(alpha = 0.35f),
                        radius = baseRadius * 1.22f,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            // 3. Organic Morphing Plasma Core
            val corePoints = 64
            val corePath = Path()
            val wobble = when (orbState) {
                RitOrbState.SPEAKING -> 0.08f
                RitOrbState.LISTENING -> 0.055f
                RitOrbState.THINKING, RitOrbState.CONNECTING -> 0.04f
                RitOrbState.IDLE -> 0.025f
                RitOrbState.DISCONNECTED -> 0.01f
            }
            for (i in 0..corePoints) {
                val angle = (i.toFloat() / corePoints) * (2f * PI.toFloat())
                val delta = sin(angle * 3f + phase) * cos(angle * 2f - phase * 1.5f) * wobble
                val r = baseRadius * breathe * (1f + delta)
                val x = center.x + r * cos(angle)
                val y = center.y + r * sin(angle)
                if (i == 0) corePath.moveTo(x, y) else corePath.lineTo(x, y)
            }
            corePath.close()

            drawPath(
                path = corePath,
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.92f),
                        primaryColor.copy(alpha = 0.95f),
                        secondaryColor.copy(alpha = 0.82f),
                        DeepIndigo.copy(alpha = 0.90f)
                    ),
                    center = Offset(center.x - baseRadius * 0.22f, center.y - baseRadius * 0.22f),
                    radius = baseRadius * 1.45f
                )
            )

            // 4. Inner Glass Rim Highlight
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.45f),
                        Color.Transparent,
                        secondaryColor.copy(alpha = 0.30f)
                    )
                ),
                radius = baseRadius * breathe * 0.96f,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}
