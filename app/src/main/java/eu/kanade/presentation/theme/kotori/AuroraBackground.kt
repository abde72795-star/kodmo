/*
 * Adapted from Kotori (https://github.com/tailolicon/kotori), Apache-2.0.
 */
package eu.kanade.presentation.theme.kotori

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    accent: KotoriAccent = KotoriLibraryAccent,
    baseColor: Color = KotoriColors.bgBase,
    content: @Composable () -> Unit,
) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        reveal.animateTo(1f, animationSpec = tween(durationMillis = 650))
    }
    val transition = rememberInfiniteTransition(label = "kodomoAurora")
    val drift1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "kodomoAuroraDrift1",
    )
    val drift2 by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "kodomoAuroraDrift2",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseColor)
            .drawBehind {
                val radius = size.width * 0.68f
                val scale1 = 1f + drift1 * 0.15f
                val scale2 = 1f + drift2 * 0.15f
                val center1 = Offset(
                    x = size.width * 0.92f + drift1 * 30f,
                    y = size.height * 0.06f - drift1 * 24f,
                )
                val center2 = Offset(
                    x = size.width * 0.06f - drift2 * 30f,
                    y = size.height * 0.86f + drift2 * 24f,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.start.copy(alpha = 0.30f * reveal.value), Color.Transparent),
                        center = center1,
                        radius = radius * scale1,
                    ),
                    radius = radius * scale1,
                    center = center1,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.end.copy(alpha = 0.20f * reveal.value), Color.Transparent),
                        center = center2,
                        radius = radius * scale2,
                    ),
                    radius = radius * scale2,
                    center = center2,
                )
            },
    ) {
        content()
    }
}
