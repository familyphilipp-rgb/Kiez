package de.ideenwerft.kiez.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.ideenwerft.kiez.ui.theme.Eyebrow
import de.ideenwerft.kiez.ui.theme.LocalKiez

fun timeAgo(ts: Long): String {
    val s = (System.currentTimeMillis() - ts) / 1000
    return when {
        s < 60 -> "gerade eben"
        s < 3600 -> "${s / 60} Min."
        s < 86400 -> "${s / 3600} Std."
        else -> "${s / 86400} T."
    }
}

private val HASHTAG = Regex("#[\\p{L}\\p{N}_]{2,30}")

// Text mit anklickbaren Hashtags
@Composable
fun richText(text: String, onTag: (String) -> Unit): AnnotatedString {
    val color = LocalKiez.current.primary
    return remember(text, color) {
        buildAnnotatedString {
            var last = 0
            for (m in HASHTAG.findAll(text)) {
                append(text.substring(last, m.range.first))
                val tag = m.value.drop(1).lowercase()
                withLink(LinkAnnotation.Clickable("tag:$tag") { onTag(tag) }) {
                    withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) { append(m.value) }
                }
                last = m.range.last + 1
            }
            append(text.substring(last))
        }
    }
}

class Haptics(private val enabled: Boolean, private val h: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    fun tick() { if (enabled) h.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    fun strong() { if (enabled) h.performHapticFeedback(HapticFeedbackType.LongPress) }
}

@Composable
fun rememberHaptics(enabled: Boolean): Haptics {
    val h = LocalHapticFeedback.current
    return remember(enabled, h) { Haptics(enabled, h) }
}

@Composable
fun EyebrowText(text: String, color: Color = LocalKiez.current.primary, modifier: Modifier = Modifier) =
    Text(text.uppercase(), style = Eyebrow, color = color, modifier = modifier)

@Composable
fun ScreenHeader(eyebrow: String, title: String, lead: String? = null) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp)) {
        EyebrowText(eyebrow)
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = LocalKiez.current.text)
        if (lead != null) {
            Spacer(Modifier.height(6.dp))
            Text(lead, style = MaterialTheme.typography.bodyMedium, color = LocalKiez.current.mutedText)
        }
    }
}

// Segment-Schalter mit gleitender Pille
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val k = LocalKiez.current
    val index = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    BoxWithConstraints(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(k.muted).padding(4.dp),
    ) {
        val w = maxWidth / options.size
        val offset by animateFloatAsState(index.toFloat(), spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow), label = "seg")
        Surface(
            modifier = Modifier.offset(x = w * offset).width(w).height(38.dp),
            shape = RoundedCornerShape(10.dp), color = if (k.isDark) k.card else Color.White, shadowElevation = 1.dp,
        ) {}
        Row(Modifier.fillMaxWidth().height(38.dp)) {
            options.forEachIndexed { i, (value, label) ->
                val color by animateColorAsState(if (i == index) k.text else k.mutedText, label = "segc")
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(value) },
                    contentAlignment = Alignment.Center,
                ) { Text(label, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1) }
            }
        }
    }
}

@Composable
fun TagChip(text: String, active: Boolean = false, onClick: () -> Unit) {
    val k = LocalKiez.current
    val bg by animateColorAsState(if (active) k.primary else k.card, label = "chipbg")
    val fg by animateColorAsState(if (active) Color.White else k.text, label = "chipfg")
    Surface(
        onClick = onClick, shape = CircleShape, color = bg, contentColor = fg,
        border = if (active) null else BorderStroke(1.dp, k.border),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) }
}

// Leichtes Eindrücken beim Antippen
@Composable
fun Modifier.pressScale(source: MutableInteractionSource, scale: Float = 0.97f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) scale else 1f, spring(dampingRatio = 0.6f), label = "press")
    return graphicsLayer { scaleX = s; scaleY = s }
}

@Composable
fun EmptyState(title: String, text: String, icon: ImageVector = Icons.Rounded.Tag, action: (@Composable () -> Unit)? = null) {
    val k = LocalKiez.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(68.dp).clip(RoundedCornerShape(22.dp)).background(k.primarySoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = k.primary, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = k.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = k.mutedText, textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(20.dp)); action() }
    }
}

// Ladeplatzhalter mit Schimmer
@Composable
fun SkeletonCard() {
    val k = LocalKiez.current
    val t = rememberInfiniteTransition(label = "sk")
    val a by t.animateFloat(0.45f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "ska")
    Surface(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), shape = RoundedCornerShape(22.dp), color = k.card,
        border = BorderStroke(1.dp, k.border)) {
        Column(Modifier.padding(18.dp).graphicsLayer { alpha = a }) {
            Bar(0.3f, 10.dp); Spacer(Modifier.height(12.dp)); Bar(0.9f, 14.dp); Spacer(Modifier.height(8.dp)); Bar(0.6f, 14.dp)
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun Bar(fraction: Float, h: Dp) =
    Box(Modifier.fillMaxWidth(fraction).height(h).clip(RoundedCornerShape(7.dp)).background(LocalKiez.current.muted))

val ListPadding = PaddingValues(top = 4.dp, bottom = 120.dp)
val Gap = Arrangement.spacedBy(0.dp)
