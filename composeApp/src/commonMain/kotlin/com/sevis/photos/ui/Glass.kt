package com.sevis.photos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A small iOS "Liquid Glass"-flavored palette + a couple of reusable frosted
 * surfaces. Real-time backdrop blur (content visibly blurring through the
 * bars as it scrolls) isn't used here — this app is Android-only today and
 * that effect needs either a compositing library or manual RenderNode work,
 * both of which add real risk for a chrome-only visual pass. Translucency +
 * a soft white edge highlight + elevation gets the same frosted read.
 */
object GlassColors {
    val AccentBlue = Color(0xFF0A84FF)
    val AccentPurple = Color(0xFF8E5CF7)
    val AccentRed = Color(0xFFFF453A)
    // A visible sky-blue-to-lavender wash — the previous near-white values
    // (#F3F5FA -> #E7ECF6) were so close to white they read as "no theme at
    // all" rather than a tinted glass surface.
    val PageTop = Color(0xFFE3EEFF)
    val PageBottom = Color(0xFFF1E8FB)
    val TextPrimary = Color(0xFF1C1C1E)
    val TextSecondary = Color(0xFF6B7280)
    val Hairline = Color.Black.copy(alpha = 0.06f)
}

val GlassPageBackground: Brush
    get() = Brush.verticalGradient(listOf(GlassColors.PageTop, GlassColors.PageBottom))

/** The tinted-glass brush used for frosted chrome (top bar, headers) — a
 * translucent blend of the accent colors rather than plain white-on-white,
 * so bars read as colored glass instead of a flat white strip. */
val GlassBarBackground: Brush
    get() = Brush.verticalGradient(
        listOf(
            GlassColors.AccentBlue.copy(alpha = 0.16f).compositeOverWhite(),
            GlassColors.AccentPurple.copy(alpha = 0.10f).compositeOverWhite()
        )
    )

private fun Color.compositeOverWhite(): Color {
    val a = alpha
    return Color(
        red = red * a + 1f * (1 - a),
        green = green * a + 1f * (1 - a),
        blue = blue * a + 1f * (1 - a),
        alpha = 1f
    )
}

/**
 * Frosted "glass" panel: translucent white fill, a faint white edge highlight
 * (the classic glassmorphism border), and a soft ambient shadow.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    tintAlpha: Float = 0.65f,
    elevation: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(elevation, shape, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
            .clip(shape)
            .background(Color.White.copy(alpha = tintAlpha))
            .border(1.dp, Color.White.copy(alpha = 0.55f), shape),
        content = content
    )
}