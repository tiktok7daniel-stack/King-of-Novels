package app.riwaya.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.riwaya.R
import app.riwaya.engine.Health
import app.riwaya.ui.theme.Fonts
import app.riwaya.ui.theme.LocalRiwaya
import coil3.compose.AsyncImage
import kotlin.math.absoluteValue

val CardShape = RoundedCornerShape(20.dp)
val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/** A Material Symbol from the app's drawables. */
@Composable
fun Sym(@DrawableRes icon: Int, modifier: Modifier = Modifier, tint: Color = LocalRiwaya.current.text, size: Dp = 22.dp, description: String? = null) {
    Icon(painterResource(icon), contentDescription = description, tint = tint, modifier = modifier.size(size))
}

/** Frosted glass: a faint fill and a hairline edge. */
fun Modifier.glass(shape: Shape = CardShape, fill: Color, line: Color): Modifier =
    clip(shape).background(fill, shape).border(1.dp, line, shape)

@Composable
fun Modifier.glass(shape: Shape = CardShape): Modifier {
    val c = LocalRiwaya.current
    return glass(shape, c.glass, c.glassLine)
}

/** A soft warm glow behind the top of a screen. */
@Composable
fun Modifier.glowBackground(): Modifier {
    val c = LocalRiwaya.current
    return drawBehind {
        drawRect(c.bg)
        drawCircle(Brush.radialGradient(listOf(c.amber.copy(alpha = if (c.dark) 0.18f else 0.14f), Color.Transparent), center = Offset(size.width * 0.25f, 0f), radius = size.width * 0.9f), radius = size.width * 0.9f, center = Offset(size.width * 0.25f, 0f))
        drawCircle(Brush.radialGradient(listOf(c.teal.copy(alpha = if (c.dark) 0.10f else 0.08f), Color.Transparent), center = Offset(size.width * 0.9f, size.height * 0.12f), radius = size.width * 0.6f), radius = size.width * 0.6f, center = Offset(size.width * 0.9f, size.height * 0.12f))
    }
}

@Composable
fun AccentButton(text: String, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val c = LocalRiwaya.current
    val haptic = LocalHapticFeedback.current
    Row(
        modifier
            .height(48.dp)
            .shadow(if (enabled) 10.dp else 0.dp, RoundedCornerShape(14.dp), ambientColor = c.coral, spotColor = c.coral)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) c.accent else Brush.horizontalGradient(listOf(c.glassLine, c.glassLine)))
            .clickable(enabled = enabled, role = Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Sym(icon, tint = c.onAccent, size = 20.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = if (enabled) c.onAccent else c.mute, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null, iconTint: Color? = null, height: Dp = 48.dp, onClick: () -> Unit) {
    val c = LocalRiwaya.current
    Row(
        modifier
            .height(height)
            .glass(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Sym(icon, tint = iconTint ?: c.text, size = 20.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Pill(text: String, selected: Boolean = false, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null, teal: Boolean = false, onClick: (() -> Unit)? = null) {
    val c = LocalRiwaya.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .clip(shape)
            .then(if (selected) Modifier.background(c.accent, shape) else Modifier.border(1.dp, if (teal) c.teal.copy(alpha = 0.5f) else c.glassLine, shape))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Sym(icon, tint = if (selected) c.onAccent else if (teal) c.teal else c.text, size = 16.dp)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text,
            color = if (selected) c.onAccent else if (teal) c.teal else c.text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, trailing: (@Composable () -> Unit)? = null, onAction: (() -> Unit)? = null) {
    val c = LocalRiwaya.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
        trailing?.invoke()
        if (action != null && onAction != null) {
            Text(action, color = c.amber, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(6.dp))
        }
    }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val c = LocalRiwaya.current
    Row(modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Sym(R.drawable.ms_arrow_back, description = stringResource(R.string.back)) }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = c.mute, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
fun RIconButton(@DrawableRes icon: Int, description: String, tint: Color = LocalRiwaya.current.text, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Sym(icon, tint = tint, description = description) }
}

private val coverPalettes = listOf(
    listOf(Color(0xFF3B2A16), Color(0xFF120C08)),
    listOf(Color(0xFF0F2D35), Color(0xFF05090C)),
    listOf(Color(0xFF4A1622), Color(0xFF120509)),
    listOf(Color(0xFF241A45), Color(0xFF09060F)),
    listOf(Color(0xFF1C3317), Color(0xFF070C05)),
    listOf(Color(0xFF4B3A1E), Color(0xFF1B140A)),
)

/** A novel's cover, with a title card of its own color while it loads or when the source has none. */
@Composable
fun Cover(url: String, title: String, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp), showTitle: Boolean = true) {
    val palette = remember(title) { coverPalettes[title.hashCode().absoluteValue % coverPalettes.size] }
    Box(modifier.shadow(8.dp, shape).clip(shape).background(Brush.linearGradient(palette))) {
        if (showTitle) {
            Text(
                title,
                fontFamily = Fonts.display,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 13.sp,
                lineHeight = 15.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            )
        }
        if (url.isNotBlank()) {
            AsyncImage(model = url, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun HealthDot(status: Health.Status?, modifier: Modifier = Modifier) {
    val c = LocalRiwaya.current
    val color = when (status) {
        Health.Status.WORKING -> c.ok
        Health.Status.SLOW, Health.Status.NO_SEARCH -> c.warn
        Health.Status.BROKEN -> c.bad
        null -> c.mute.copy(alpha = 0.5f)
    }
    Box(modifier.size(8.dp).clip(CircleShape).background(color))
}

@Composable
fun healthText(check: Health.Check?): String = when (check?.status) {
    null -> stringResource(R.string.health_unknown)
    Health.Status.WORKING -> stringResource(R.string.health_working)
    Health.Status.SLOW -> stringResource(R.string.health_slow, check!!.ms / 1000f)
    Health.Status.NO_SEARCH -> stringResource(R.string.health_no_search)
    Health.Status.BROKEN -> when (check!!.reason) {
        Health.Reason.CHALLENGE -> stringResource(R.string.health_challenge)
        Health.Reason.TIMEOUT -> stringResource(R.string.health_timeout)
        Health.Reason.NETWORK -> stringResource(R.string.health_network)
        Health.Reason.EMPTY -> stringResource(R.string.health_empty)
        else -> stringResource(R.string.health_error)
    }
}

/** A progress ring around a percentage. */
@Composable
fun ProgressRing(fraction: Float, modifier: Modifier = Modifier, size: Dp = 46.dp) {
    val c = LocalRiwaya.current
    Box(
        modifier.size(size).drawBehind {
            val stroke = 4.dp.toPx()
            drawArc(c.glassLine, 0f, 360f, false, style = Stroke(stroke), topLeft = Offset(stroke / 2, stroke / 2), size = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke))
            drawArc(c.amber, -90f, 360f * fraction.coerceIn(0f, 1f), false, style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round), topLeft = Offset(stroke / 2, stroke / 2), size = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke))
        },
        contentAlignment = Alignment.Center,
    ) {
        Text("${(fraction * 100).toInt()}%", color = c.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** A shimmering block while something loads. */
@Composable
fun Shimmer(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp)) {
    val c = LocalRiwaya.current
    val t = rememberInfiniteTransition(label = "shimmer")
    val a by t.animateFloat(0.04f, 0.12f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Box(modifier.clip(shape).background(c.text.copy(alpha = a)))
}

@Composable
fun EmptyState(@DrawableRes icon: Int, title: String, body: String? = null, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    val c = LocalRiwaya.current
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(64.dp).glass(CircleShape), contentAlignment = Alignment.Center) { Sym(icon, tint = c.amber, size = 30.dp) }
        Text(title, color = c.text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) Text(body, color = c.mute, fontSize = 13.sp, textAlign = TextAlign.Center)
        if (action != null && onAction != null) GhostButton(action, onClick = onAction)
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = LocalRiwaya.current.amber, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
    }
}

@Composable
fun RSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalRiwaya.current
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(checkedTrackColor = c.teal, checkedThumbColor = Color.White, uncheckedTrackColor = c.glassLine, uncheckedThumbColor = c.mute, uncheckedBorderColor = Color.Transparent),
    )
}

/** A row in a settings list. */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = LocalRiwaya.current
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Sym(icon, tint = c.teal, size = 22.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (subtitle != null) Text(subtitle, color = c.mute, fontSize = 12.sp)
        }
        trailing?.invoke()
    }
}

val ScreenPadding = PaddingValues(horizontal = 16.dp)
