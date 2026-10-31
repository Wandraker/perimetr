package com.perimetr.guard.ui

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.perimetr.guard.Level
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun Panel(modifier: Modifier = Modifier, level: Level? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val border = when (level) {
        Level.CRIT -> P.Crit.copy(alpha = 0.4f)
        Level.WARN -> P.Warn.copy(alpha = 0.3f)
        else -> P.Line
    }
    val bg: Brush = if (level == Level.CRIT) Brush.verticalGradient(listOf(P.CritDim, P.Surface)) else SolidColor(P.Surface)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg, shape)
            .border(1.dp, border, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun Pill(text: String, level: Level?) {
    val (fg, bg) = when (level) {
        Level.CRIT -> P.Crit to P.CritDim
        Level.WARN -> P.Warn to P.Warn.copy(alpha = 0.16f)
        Level.OK -> P.Ok to P.Ok.copy(alpha = 0.14f)
        null -> P.Muted to P.Surface2
    }
    Text(
        text,
        color = fg,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.3.sp,
        maxLines = 1,
        modifier = Modifier
            .background(bg, RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun SectionHead(title: String, trailing: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 2.dp, start = 2.dp, end = 2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, color = P.Fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, color = P.Faint, fontSize = 12.sp, fontFamily = Mono)
    }
}

@Composable
fun Hint(text: String) {
    Text(
        text,
        color = P.Muted,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, P.Line, RoundedCornerShape(14.dp))
            .padding(14.dp),
    )
}

private val badgeColors = listOf(
    Color(0xFFFF8A5C),
    Color(0xFFFFD166),
    Color(0xFF6EC1FF),
    Color(0xFFB388FF),
    Color(0xFF4DD0E1),
    Color(0xFF3DDC84),
    Color(0xFFFF7EB6),
)

private object IconCache {
    private val cache = LruCache<String, ImageBitmap>(160)

    fun get(ctx: Context, pkg: String): ImageBitmap? {
        cache.get(pkg)?.let { return it }
        return try {
            ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap().also { cache.put(pkg, it) }
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
fun AppIcon(pkg: String, label: String, size: Dp = 42.dp) {
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(size * 0.28f)
    val bmp by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) { IconCache.get(ctx, pkg) }
    }
    val b = bmp
    if (b != null) {
        Image(b, contentDescription = null, modifier = Modifier.size(size).clip(shape))
    } else {
        val c = badgeColors[(pkg.hashCode() and 0x7fffffff) % badgeColors.size]
        Box(Modifier.size(size).clip(shape).background(c), contentAlignment = Alignment.Center) {
            Text(
                label.firstOrNull()?.uppercase() ?: "?",
                color = P.Bg,
                fontWeight = FontWeight.ExtraBold,
                fontSize = (size.value * 0.38f).sp,
            )
        }
    }
}

@Composable
fun AppHeader(pkg: String, label: String, sub: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppIcon(pkg, label)
        Column(Modifier.weight(1f)) {
            Text(label, color = P.Fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = P.Faint, fontSize = 11.5.sp, fontFamily = Mono, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
fun ScoreRing(score: Int, color: Color) {
    Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(P.Line, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
            drawArc(
                color,
                -90f,
                360f * score / 100f,
                false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", color = P.Fg, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            Text("из 100", color = P.Faint, fontSize = 10.sp, fontFamily = Mono)
        }
    }
}

@Composable
fun ActionButton(text: String, modifier: Modifier = Modifier, danger: Boolean = false, quiet: Boolean = false, onClick: () -> Unit) {
    val colors = when {
        danger -> ButtonDefaults.buttonColors(containerColor = P.Crit, contentColor = Color.White)
        quiet -> ButtonDefaults.buttonColors(containerColor = P.Surface2, contentColor = P.Muted)
        else -> ButtonDefaults.buttonColors(containerColor = P.Accent, contentColor = P.Bg)
    }
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = colors,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ToggleCard(icon: ImageVector, title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(P.Surface)
            .border(1.dp, P.Line, shape)
            .clickable { onChange(!checked) }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        IconTile(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = P.Fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(desc, color = P.Muted, fontSize = 12.5.sp, lineHeight = 18.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = P.Bg,
                checkedTrackColor = P.Accent,
                uncheckedThumbColor = P.Faint,
                uncheckedTrackColor = P.Surface2,
                uncheckedBorderColor = P.Line,
            ),
        )
    }
}

@Composable
fun IconTile(icon: ImageVector, tint: Color = P.Accent, bg: Color = P.AccentDim) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun FlowBar(left: String, right: String, hot: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(P.Surface2)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(left, color = P.Muted, fontSize = 12.sp, fontFamily = Mono, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(right, color = if (hot) P.Crit else P.Fg, fontSize = 12.sp, fontFamily = Mono, fontWeight = FontWeight.SemiBold)
    }
}
