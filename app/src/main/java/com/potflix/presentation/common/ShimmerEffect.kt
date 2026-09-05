package com.potflix.presentation.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun shimmerBrush(
    targetValue: Float = 1600f,
    showShimmer: Boolean = true
): Brush {
    if (!showShimmer) return Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))

    val shimmerColors = listOf(
        Color(0xFF161616),
        Color(0xFF262626),
        Color(0xFF383838),
        Color(0xFF262626),
        Color(0xFF161616),
    )

    val transition = rememberInfiniteTransition(label = "ShimmerTransition")
    val translateAnimation = transition.animateFloat(
        initialValue = -500f,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1350, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerTranslate"
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(x = translateAnimation.value - 450f, y = 0f),
        end = Offset(x = translateAnimation.value, y = 120f)
    )
}

fun Modifier.shimmerEffect(shape: Shape? = null): Modifier = composed {
    val brush = shimmerBrush()
    val base = if (shape != null) this.clip(shape) else this
    base.background(brush)
}

