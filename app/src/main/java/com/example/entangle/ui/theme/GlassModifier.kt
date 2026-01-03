package com.example.entangle.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * GlassModifier: Industrial Glassmorphism
 * 
 * Applies a premium "frosted glass" effect.
 * - Android 12+ (S): Real-time RenderEffect blur
 * - Legacy: Translucent fallback with gradient border
 */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(12.dp),
    backgroundColor: Color = Color.Black,
    alpha: Float = 0.6f,
    blurRadius: Dp = 20.dp
): Modifier = composed {
    
    val isBlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    
    this
        .clip(shape)
        .then(
            if (isBlurSupported) {
                // Real blur for Android 12+
                Modifier.blur(blurRadius)
            } else {
                Modifier
            }
        )
        .background(backgroundColor.copy(alpha = alpha), shape)
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.2f),
                    Color.White.copy(alpha = 0.05f)
                )
            ),
            shape = shape
        )
}
