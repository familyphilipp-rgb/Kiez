package de.ideenwerft.kiez.ui

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.ideenwerft.kiez.data.Post
import de.ideenwerft.kiez.ui.theme.LocalKiez
import de.ideenwerft.kiez.ui.theme.parseColor
import kotlinx.coroutines.launch

// Ein Beitrag: farbig im Feed, schlicht als Antwort
@Composable
fun PostCard(
    post: Post,
    vm: KiezViewModel,
    videoActive: Boolean,
    modifier: Modifier = Modifier,
    reply: Boolean = false,
    onOpen: (() -> Unit)? = null,
    onTag: (String) -> Unit = {},
    onChanged: (Post) -> Unit = {},
    onDeleted: () -> Unit = {},
) {
    val k = LocalKiez.current
    val context = LocalContext.current
    val haptics = rememberHaptics(vm.haptics)
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(post)
    val open by rememberUpdatedState(onOpen)
    val burst = remember { Animatable(0f) }

    val colored = !reply
    val base = parseColor(post.color)
    val fg = if (colored) Color.White else k.text
    val meta = if (colored) Color.White.copy(alpha = 0.78f) else k.mutedText
    val shape = RoundedCornerShape(if (reply) 18.dp else 24.dp)

    fun vote(value: Int) {
        haptics.tick()
        vm.vote(current, value, onChanged)
    }

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        shape = shape,
        color = if (colored) base else k.card,
        border = if (colored) null else BorderStroke(1.dp, k.border),
        shadowElevation = if (colored) 3.dp else 0.dp,
    ) {
        Box(
            Modifier
                .then(if (colored) Modifier.background(Brush.linearGradient(listOf(lerp(base, Color.White, 0.08f), lerp(base, Color.Black, 0.22f)))) else Modifier)
                .pointerInput(post.id) {
                    detectTapGestures(
                        onTap = { open?.invoke() },
                        onDoubleTap = {
                            // Doppeltippen = Daumen hoch, wie bei Jodel
                            if (current.myVote != 1) vote(1) else haptics.tick()
                            scope.launch { burst.snapTo(0.001f); burst.animateTo(1f, tween(700)) }
                        },
                    )
                },
        ) {
            Row(Modifier.padding(start = 18.dp, top = 12.dp, bottom = 12.dp, end = 6.dp)) {
                Column(Modifier.weight(1f).padding(top = 4.dp)) {
                    MetaRow(post, reply, meta, fg, colored)
                    if (post.text.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            richText(post.text) { onTag(it) },
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = if (reply) 16.sp else 18.sp),
                            color = fg,
                        )
                    }
                    post.media?.let {
                        Spacer(Modifier.height(12.dp))
                        PostMedia(it, videoActive, Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!reply) {
                            Icon(Icons.Rounded.ChatBubbleOutline, null, tint = meta, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (post.commentCount == 1) "1 Antwort" else "${post.commentCount} Antworten",
                                style = MaterialTheme.typography.labelLarge, color = meta,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        PostMenu(post, vm, fg = meta, onDeleted = onDeleted, share = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "„${post.text}“\n\nGesehen auf Kiez")
                            }
                            context.startActivity(Intent.createChooser(send, "Teilen"))
                        })
                    }
                }
                VoteColumn(post, fg, colored, up = { vote(if (current.myVote == 1) 0 else 1) }, down = { vote(if (current.myVote == -1) 0 else -1) })
            }
            val b = burst.value
            if (b > 0f && b < 1f) {
                Icon(
                    Icons.Rounded.KeyboardArrowUp, null,
                    tint = if (colored) Color.White else k.primary,
                    modifier = Modifier.align(Alignment.Center).size(120.dp).graphicsLayer {
                        val s = 0.5f + b * 0.9f
                        scaleX = s; scaleY = s
                        alpha = if (b < 0.5f) 1f else (1f - b) * 2f
                    },
                )
            }
        }
    }
}

@Composable
private fun MetaRow(post: Post, reply: Boolean, meta: Color, fg: Color, colored: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (reply) {
            val label = if (post.isOP == true) "OJ" else post.authorNo?.let { "#$it" }
            if (label != null) Badge(label, strong = post.isOP == true)
        }
        if (post.mine) Badge("Du", strong = false, colored = colored)
        if (!post.distance.isNullOrBlank() && !reply) {
            Icon(Icons.Rounded.LocationOn, null, tint = meta, modifier = Modifier.size(14.dp))
            Text(post.distance, style = MaterialTheme.typography.labelMedium, color = meta)
            Text("·", color = meta)
        }
        Text(timeAgo(post.createdAt), style = MaterialTheme.typography.labelMedium, color = meta)
    }
}

@Composable
private fun Badge(text: String, strong: Boolean, colored: Boolean = false) {
    val k = LocalKiez.current
    val bg = when {
        colored -> Color.White.copy(alpha = 0.22f)
        strong -> k.primary
        else -> k.muted
    }
    val fg = if (colored || strong) Color.White else k.text
    Box(Modifier.clip(CircleShape).background(bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

@Composable
private fun VoteColumn(post: Post, fg: Color, colored: Boolean, up: () -> Unit, down: () -> Unit) {
    val k = LocalKiez.current
    val on = if (colored) Color.White else k.primary
    val off = fg.copy(alpha = 0.55f)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
        IconButton(onClick = up) {
            Icon(Icons.Rounded.KeyboardArrowUp, "Hoch", tint = if (post.myVote == 1) on else off, modifier = Modifier.size(if (post.myVote == 1) 34.dp else 30.dp))
        }
        AnimatedContent(
            targetState = post.score,
            transitionSpec = {
                if (targetState > initialState) (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                else (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut())
            },
            label = "score",
        ) { score ->
            Text("$score", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = fg)
        }
        IconButton(onClick = down) {
            Icon(Icons.Rounded.KeyboardArrowDown, "Runter", tint = if (post.myVote == -1) on else off, modifier = Modifier.size(if (post.myVote == -1) 34.dp else 30.dp))
        }
    }
}

@Composable
private fun PostMenu(post: Post, vm: KiezViewModel, fg: Color, onDeleted: () -> Unit, share: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.MoreHoriz, "Mehr", tint = fg)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (post.text.isNotBlank()) {
                DropdownMenuItem(text = { Text("Teilen") }, leadingIcon = { Icon(Icons.Rounded.Share, null) }, onClick = { open = false; share() })
            }
            if (post.mine) {
                DropdownMenuItem(text = { Text("Löschen") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { open = false; confirm = true })
            } else {
                DropdownMenuItem(text = { Text("Melden") }, leadingIcon = { Icon(Icons.Rounded.Flag, null) }, onClick = { open = false; vm.report(post) })
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Beitrag löschen?") },
            text = { Text("Das lässt sich nicht rückgängig machen.") },
            confirmButton = { TextButton(onClick = { confirm = false; vm.delete(post) { onDeleted() } }) { Text("Löschen") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Abbrechen") } },
        )
    }
}
