package com.tanoo.ambient.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import com.tanoo.ambient.Sky
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 16.sp,
    weight: FontWeight = FontWeight.Normal,
    color: Color = P.text,
    maxLines: Int = 1,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            fontFamily = Inter,
            fontSize = size,
            fontWeight = weight,
            color = color,
            letterSpacing = letterSpacing,
            lineHeight = lineHeight,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The one focusable building block. Mirrors tvOS focus: the item lifts (scale + shadow)
 * and gains a crisp edge so it reads from across the room on a projector.
 */
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    container: Color = P.card,
    focusedContainer: Color = container,
    onLongClick: (() -> Unit)? = null,
    focusedScale: Float = 1.06f,
    showBorder: Boolean = true,
    contentColor: Color = P.text,
    focusedContentColor: Color = contentColor,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = P
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            contentColor = contentColor,
            focusedContainerColor = focusedContainer,
            focusedContentColor = focusedContentColor,
            pressedContainerColor = focusedContainer,
            pressedContentColor = focusedContentColor,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale, pressedScale = focusedScale * 0.97f),
        border = ClickableSurfaceDefaults.border(
            border = if (showBorder) Border(BorderStroke(1.dp, p.cardBorder), shape = shape) else Border.None,
            focusedBorder = Border(BorderStroke(2.dp, p.focusBorder), shape = shape),
            pressedBorder = Border(BorderStroke(2.dp, p.focusBorder), shape = shape),
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = p.focusGlow, elevation = 16.dp),
        ),
        content = content,
    )
}

/** Capsule button used in the top bar ("Apple TV", settings…). */
@Composable
fun Pill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = CircleShape,
        container = P.fill.copy(alpha = if (P.isDark) 0.7f else 1f),
        focusedContainer = if (P.isDark) Color(0xFFF5F5F7) else Color(0xFF1D1D1F),
        focusedScale = 1.08f,
        showBorder = false,
        focusedContentColor = if (P.isDark) Color(0xFF1D1D1F) else Color(0xFFF5F5F7),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = if (label == null) 13.dp else 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            icon?.invoke()
            if (label != null) Txt(label, size = 16.sp, weight = FontWeight.Medium, color = LocalContentColorOnPill())
        }
    }
}

/** Pills invert on focus (light capsule, dark text), like tvOS. */
@Composable
fun LocalContentColorOnPill(): Color = androidx.tv.material3.LocalContentColor.current.let { c ->
    if (c == Color.Unspecified) P.text else c
}

// ——— Glyphs ———————————————————————————————————————————————————————————————————
// Drawn rather than bundled so they stay crisp at any size and pick up theme colours.

@Composable
fun Glyph(size: Dp, color: Color, modifier: Modifier = Modifier, draw: DrawScope.(Color) -> Unit) {
    Canvas(modifier.size(size)) { draw(color) }
}

fun DrawScope.gearGlyph(c: Color) {
    val r = size.minDimension / 2
    val center = Offset(size.width / 2, size.height / 2)
    val w = r * 0.16f
    for (i in 0 until 8) {
        val a = i * PI / 4
        drawLine(
            c,
            Offset(center.x + cos(a).toFloat() * r * 0.55f, center.y + sin(a).toFloat() * r * 0.55f),
            Offset(center.x + cos(a).toFloat() * r * 0.95f, center.y + sin(a).toFloat() * r * 0.95f),
            strokeWidth = w * 1.6f, cap = StrokeCap.Round,
        )
    }
    drawCircle(c, r * 0.62f, center, style = Stroke(w * 1.5f))
    drawCircle(c, r * 0.2f, center, style = Stroke(w))
}

fun DrawScope.hdmiGlyph(c: Color) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.08f, h * 0.3f)
        lineTo(w * 0.92f, h * 0.3f)
        lineTo(w * 0.92f, h * 0.55f)
        lineTo(w * 0.78f, h * 0.72f)
        lineTo(w * 0.22f, h * 0.72f)
        lineTo(w * 0.08f, h * 0.55f)
        close()
    }
    drawPath(path, c, style = Stroke(w * 0.09f))
    drawLine(c, Offset(w * 0.3f, h * 0.48f), Offset(w * 0.7f, h * 0.48f), strokeWidth = w * 0.08f, cap = StrokeCap.Round)
}

fun DrawScope.gridGlyph(c: Color) {
    val s = size.minDimension
    val d = s * 0.11f
    for (x in 0..2) for (y in 0..2) {
        drawCircle(c, d, Offset(s * (0.2f + 0.3f * x), s * (0.2f + 0.3f * y)))
    }
}

fun DrawScope.chevronDownGlyph(c: Color) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.2f, h * 0.38f); lineTo(w * 0.5f, h * 0.66f); lineTo(w * 0.8f, h * 0.38f)
    }
    drawPath(path, c, style = Stroke(w * 0.12f, cap = StrokeCap.Round))
}

fun DrawScope.plusGlyph(c: Color) {
    val w = size.width
    drawLine(c, Offset(w * 0.5f, w * 0.18f), Offset(w * 0.5f, w * 0.82f), strokeWidth = w * 0.1f, cap = StrokeCap.Round)
    drawLine(c, Offset(w * 0.18f, w * 0.5f), Offset(w * 0.82f, w * 0.5f), strokeWidth = w * 0.1f, cap = StrokeCap.Round)
}

fun DrawScope.checkGlyph(c: Color) {
    val w = size.width
    val path = Path().apply { moveTo(w * 0.22f, w * 0.52f); lineTo(w * 0.42f, w * 0.72f); lineTo(w * 0.78f, w * 0.3f) }
    drawPath(path, c, style = Stroke(w * 0.12f, cap = StrokeCap.Round))
}

fun DrawScope.playGlyph(c: Color) {
    val w = size.width
    val path = Path().apply { moveTo(w * 0.3f, w * 0.18f); lineTo(w * 0.84f, w * 0.5f); lineTo(w * 0.3f, w * 0.82f); close() }
    drawPath(path, c)
}

fun DrawScope.pauseGlyph(c: Color) {
    val w = size.width
    drawRoundRect(c, Offset(w * 0.24f, w * 0.18f), Size(w * 0.18f, w * 0.64f), CornerRadius(w * 0.05f))
    drawRoundRect(c, Offset(w * 0.58f, w * 0.18f), Size(w * 0.18f, w * 0.64f), CornerRadius(w * 0.05f))
}

fun DrawScope.skipGlyph(c: Color, forward: Boolean) {
    val w = size.width
    fun tri(x0: Float) = Path().apply {
        if (forward) { moveTo(x0, w * 0.25f); lineTo(x0 + w * 0.32f, w * 0.5f); lineTo(x0, w * 0.75f) }
        else { moveTo(x0 + w * 0.32f, w * 0.25f); lineTo(x0, w * 0.5f); lineTo(x0 + w * 0.32f, w * 0.75f) }
        close()
    }
    drawPath(tri(w * 0.16f), c)
    drawPath(tri(w * 0.48f), c)
}

/** Sunrise (arrow up) / sunset (arrow down) over a horizon line. */
fun DrawScope.sunEventGlyph(c: Color, rising: Boolean) {
    val w = size.width
    val stroke = w * 0.08f
    drawLine(c, Offset(w * 0.08f, w * 0.8f), Offset(w * 0.92f, w * 0.8f), strokeWidth = stroke, cap = StrokeCap.Round)
    drawArc(c, 180f, 180f, false, Offset(w * 0.25f, w * 0.55f), Size(w * 0.5f, w * 0.5f), style = Stroke(stroke))
    val tip = if (rising) w * 0.08f else w * 0.42f
    val tail = if (rising) w * 0.42f else w * 0.08f
    drawLine(c, Offset(w * 0.5f, tail), Offset(w * 0.5f, tip), strokeWidth = stroke, cap = StrokeCap.Round)
    val dir = if (rising) 1 else -1
    drawLine(c, Offset(w * 0.5f, tip), Offset(w * 0.38f, tip + dir * w * 0.12f), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(c, Offset(w * 0.5f, tip), Offset(w * 0.62f, tip + dir * w * 0.12f), strokeWidth = stroke, cap = StrokeCap.Round)
}

fun DrawScope.weatherGlyph(c: Color, sky: Sky, isDay: Boolean) {
    val w = size.width
    val stroke = w * 0.07f
    fun sun(cx: Float, cy: Float, r: Float) {
        drawCircle(c, r, Offset(cx, cy), style = Stroke(stroke))
        for (i in 0 until 8) {
            val a = i * PI / 4
            drawLine(
                c,
                Offset(cx + cos(a).toFloat() * r * 1.45f, cy + sin(a).toFloat() * r * 1.45f),
                Offset(cx + cos(a).toFloat() * r * 1.85f, cy + sin(a).toFloat() * r * 1.85f),
                strokeWidth = stroke, cap = StrokeCap.Round,
            )
        }
    }
    fun moon(cx: Float, cy: Float, r: Float) {
        val path = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(Offset(cx, cy), r))
        }
        val bite = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(Offset(cx + r * 0.55f, cy - r * 0.45f), r * 0.9f))
        }
        val crescent = Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, path, bite)
        drawPath(crescent, c)
    }
    fun cloud(y: Float) {
        val path = Path().apply {
            moveTo(w * 0.22f, y)
            lineTo(w * 0.78f, y)
            arcTo(androidx.compose.ui.geometry.Rect(w * 0.6f, y - w * 0.36f, w * 0.96f, y), 90f, -180f, false)
            arcTo(androidx.compose.ui.geometry.Rect(w * 0.3f, y - w * 0.5f, w * 0.74f, y - w * 0.06f), -10f, -150f, false)
            arcTo(androidx.compose.ui.geometry.Rect(w * 0.04f, y - w * 0.36f, w * 0.4f, y), -90f, -180f, false)
            close()
        }
        drawPath(path, c, style = Stroke(stroke, cap = StrokeCap.Round))
    }
    when (sky) {
        Sky.CLEAR -> if (isDay) sun(w / 2, w / 2, w * 0.2f) else moon(w / 2, w / 2, w * 0.32f)
        Sky.PARTLY -> {
            if (isDay) sun(w * 0.36f, w * 0.32f, w * 0.13f) else moon(w * 0.36f, w * 0.32f, w * 0.18f)
            cloud(w * 0.84f)
        }
        Sky.CLOUDY -> cloud(w * 0.72f)
        Sky.FOG -> for (i in 0..2) {
            drawLine(c, Offset(w * 0.12f, w * (0.36f + i * 0.16f)), Offset(w * 0.88f, w * (0.36f + i * 0.16f)), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        Sky.DRIZZLE, Sky.RAIN -> {
            cloud(w * 0.6f)
            val n = if (sky == Sky.RAIN) 3 else 2
            for (i in 0 until n) {
                val x = w * (0.32f + i * 0.18f)
                drawLine(c, Offset(x, w * 0.72f), Offset(x - w * 0.06f, w * 0.9f), strokeWidth = stroke, cap = StrokeCap.Round)
            }
        }
        Sky.SNOW -> {
            cloud(w * 0.6f)
            for (i in 0 until 3) drawCircle(c, w * 0.04f, Offset(w * (0.32f + i * 0.18f), w * (0.78f + (i % 2) * 0.08f)))
        }
        Sky.STORM -> {
            cloud(w * 0.6f)
            val bolt = Path().apply {
                moveTo(w * 0.54f, w * 0.62f); lineTo(w * 0.42f, w * 0.8f); lineTo(w * 0.54f, w * 0.8f); lineTo(w * 0.46f, w * 0.98f)
            }
            drawPath(bolt, c, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}
