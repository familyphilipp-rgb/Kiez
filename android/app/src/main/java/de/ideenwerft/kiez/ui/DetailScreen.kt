package de.ideenwerft.kiez.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.ideenwerft.kiez.data.ApiException
import de.ideenwerft.kiez.data.DetailResponse
import de.ideenwerft.kiez.data.PickedMedia
import de.ideenwerft.kiez.ui.theme.LocalKiez
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(vm: KiezViewModel, id: Long, back: () -> Unit, openTag: (String) -> Unit) {
    val k = LocalKiez.current
    var data by remember { mutableStateOf<DetailResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(id, reload) {
        try {
            data = vm.loadPost(id); error = null
        } catch (e: ApiException) {
            error = if (e.status == 404) "Dieser Beitrag ist nicht mehr da." else e.message
        }
    }
    val all = data?.let { listOf(it.post) + it.replies }.orEmpty()
    val active by rememberActiveVideo(listState, all)

    Column(Modifier.fillMaxSize().background(k.background).imePadding()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", tint = k.text) }
            Column {
                EyebrowText("Beitrag")
                Text(vm.place?.label ?: "Kiez", style = MaterialTheme.typography.titleMedium, color = k.text)
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val d = data
            when {
                d != null -> LazyColumn(state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
                    item(d.post.id) {
                        PostCard(
                            d.post, vm, videoActive = active == d.post.id,
                            onTag = { openTag(it) },
                            onChanged = { p -> data = data?.let { it.copy(post = it.post.copy(score = p.score, myVote = p.myVote)) } },
                            onDeleted = back,
                        )
                    }
                    item("count") {
                        Row(Modifier.padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ChatBubbleOutline, null, tint = k.mutedText, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            EyebrowText(
                                when (d.replies.size) { 0 -> "Noch keine Antworten"; 1 -> "1 Antwort"; else -> "${d.replies.size} Antworten" },
                                color = k.mutedText,
                            )
                        }
                    }
                    items(d.replies, key = { it.id }) { r ->
                        PostCard(
                            r, vm, videoActive = active == r.id, reply = true,
                            modifier = Modifier.animateItem(),
                            onTag = { openTag(it) },
                            onChanged = { p ->
                                data = data?.let { cur -> cur.copy(replies = cur.replies.map { if (it.id == p.id) it.copy(score = p.score, myVote = p.myVote) else it }) }
                            },
                            onDeleted = { reload++ },
                        )
                    }
                }
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState("Nicht erreichbar", error ?: "", Icons.Rounded.CloudOff) {
                        Button(onClick = { reload++ }) { Text("Nochmal versuchen") }
                    }
                }
                else -> Column(Modifier.padding(top = 8.dp)) { SkeletonCard(); SkeletonCard() }
            }
        }

        if (data != null) {
            ReplyBar(vm, id) {
                reload++
                scope.launch { listState.animateScrollToItem((data?.replies?.size ?: 0) + 1) }
            }
        }
    }
}

@Composable
private fun ReplyBar(vm: KiezViewModel, postId: Long, sent: () -> Unit) {
    val k = LocalKiez.current
    val context = LocalContext.current
    val haptics = rememberHaptics(vm.haptics)
    var text by rememberSaveable { mutableStateOf("") }
    var media by remember { mutableStateOf<PickedMedia?>(null) }
    var menu by remember { mutableStateOf(false) }
    val picker = rememberMediaPicker(vm) { media = it }

    Surface(color = k.card, shadowElevation = 12.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
            HorizontalDivider(color = k.border, modifier = Modifier.padding(bottom = 4.dp))
            media?.let { m ->
                MediaPreview(m, Modifier.padding(4.dp).size(96.dp)) { media = null }
            }
            vm.uploadProgress?.let { p ->
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().padding(4.dp).clip(CircleShape), color = k.primary, trackColor = k.muted)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.AddPhotoAlternate, "Foto oder Video", tint = k.mutedText) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Galerie") }, leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, null) }, onClick = { menu = false; picker.gallery() })
                        DropdownMenuItem(text = { Text("Foto aufnehmen") }, leadingIcon = { Icon(Icons.Rounded.PhotoCamera, null) }, onClick = { menu = false; picker.photo() })
                        DropdownMenuItem(text = { Text("Video aufnehmen") }, leadingIcon = { Icon(Icons.Rounded.Videocam, null) }, onClick = { menu = false; picker.video() })
                    }
                }
                Box(
                    Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(k.muted)
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { if (it.length <= MAX_LEN) text = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = k.text),
                        cursorBrush = SolidColor(k.primary),
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (text.isEmpty()) Text("Antworten …", style = MaterialTheme.typography.bodyLarge, color = k.mutedText)
                            inner()
                        },
                    )
                }
                val canSend = !vm.sending && (text.isNotBlank() || media != null)
                IconButton(
                    onClick = {
                        haptics.tick()
                        vm.publish(context, text, media, replyTo = postId) { text = ""; media = null; sent() }
                    },
                    enabled = canSend,
                ) {
                    if (vm.sending) CircularProgressIndicator(Modifier.size(20.dp), color = k.primary, strokeWidth = 2.dp)
                    else Icon(Icons.AutoMirrored.Rounded.Send, "Senden", tint = if (canSend) k.primary else k.mutedText)
                }
            }
        }
    }
}
