package de.ideenwerft.kiez.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import de.ideenwerft.kiez.data.PickedMedia
import de.ideenwerft.kiez.data.captureUri
import de.ideenwerft.kiez.data.prepareImage
import de.ideenwerft.kiez.data.videoMedia
import de.ideenwerft.kiez.ui.theme.LocalKiez
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val MAX_LEN = 250

class MediaPicker(val gallery: () -> Unit, val photo: () -> Unit, val video: () -> Unit)

// Galerie, Foto- und Videokamera; Fotos werden vor dem Hochladen verkleinert und von Metadaten befreit
@Composable
fun rememberMediaPicker(vm: KiezViewModel, onPicked: (PickedMedia) -> Unit): MediaPicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picked by rememberUpdatedState(onPicked)
    var pending by rememberSaveable { mutableStateOf<String?>(null) }

    fun image(uri: Uri) {
        scope.launch {
            runCatching { prepareImage(context, uri) }
                .onSuccess { picked(it) }
                .onFailure { vm.toast("Das Foto konnte nicht gelesen werden") }
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val mime = context.contentResolver.getType(uri).orEmpty()
        if (mime.startsWith("video/")) picked(videoMedia(context, uri)) else image(uri)
    }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pending?.let(Uri::parse)
        if (ok && uri != null) image(uri)
    }
    val video = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        val uri = pending?.let(Uri::parse)
        if (ok && uri != null) picked(PickedMedia(uri, "video/mp4", isVideo = true))
    }

    return remember(gallery, photo, video) {
        MediaPicker(
            gallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
            photo = {
                val uri = captureUri(context, "jpg"); pending = uri.toString()
                runCatching { photo.launch(uri) }.onFailure { vm.toast("Keine Kamera-App gefunden") }
            },
            video = {
                val uri = captureUri(context, "mp4"); pending = uri.toString()
                runCatching { video.launch(uri) }.onFailure { vm.toast("Keine Kamera-App gefunden") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ComposeSheet(vm: KiezViewModel, onDismiss: () -> Unit) {
    val k = LocalKiez.current
    val context = LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    var text by rememberSaveable { mutableStateOf("") }
    var media by remember { mutableStateOf<PickedMedia?>(null) }
    val picker = rememberMediaPicker(vm) { media = it }

    fun close() {
        scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = k.card) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 12.dp)) {
            EyebrowText("Neuer Post")
            Spacer(Modifier.height(4.dp))
            Text(
                vm.place?.label?.let { "Was ist los in $it?" } ?: "Was ist los?",
                style = MaterialTheme.typography.titleLarge, color = k.text,
            )
            Spacer(Modifier.height(14.dp))

            Box(
                Modifier.fillMaxWidth().heightIn(min = 150.dp).clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(lerp(k.primary, Color.White, 0.08f), lerp(k.primary, Color.Black, 0.25f))))
                    .padding(18.dp),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { if (it.length <= MAX_LEN) text = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White, fontSize = 19.sp, lineHeight = 26.sp),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    decorationBox = { inner ->
                        if (text.isEmpty()) Text("Teile etwas mit deinem Kiez …", color = Color.White.copy(alpha = 0.7f), fontSize = 19.sp)
                        inner()
                    },
                )
                Text(
                    "${text.length}/$MAX_LEN", style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = if (text.length > MAX_LEN - 20) 1f else 0.7f),
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }

            if (vm.trending.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.trending.take(6).forEach { t ->
                        TagChip("#${t.tag}") {
                            val add = "#${t.tag} "
                            if ((text.trimEnd() + " " + add).length <= MAX_LEN) text = (text.trimEnd() + " " + add).trimStart()
                        }
                    }
                }
            }

            media?.let { m ->
                Spacer(Modifier.height(12.dp))
                MediaPreview(m, Modifier.fillMaxWidth().aspectRatio(16f / 9)) { media = null }
            }

            vm.uploadProgress?.let { p ->
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().clip(CircleShape), color = k.primary, trackColor = k.muted)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (p < 1f) "Lädt hoch … ${(p * 100).toInt()} %" else if (media?.isVideo == true) "Video wird umgewandelt …" else "Wird gespeichert …",
                    style = MaterialTheme.typography.bodySmall, color = k.mutedText,
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MediaButton(Icons.Rounded.PhotoLibrary, "Galerie", picker.gallery)
                MediaButton(Icons.Rounded.PhotoCamera, "Foto", picker.photo)
                MediaButton(Icons.Rounded.Videocam, "Video", picker.video)
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { vm.publish(context, text, media) { close() } },
                    enabled = !vm.sending && (text.isNotBlank() || media != null),
                    shape = CircleShape,
                ) {
                    if (vm.sending) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Posten")
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Anonym. Fotos und Videos werden ohne Ortsdaten gespeichert, Videos auf 60 Sekunden gekürzt.",
                style = MaterialTheme.typography.bodySmall, color = k.mutedText,
            )
        }
    }
    LaunchedEffect(Unit) {
        delay(350)
        runCatching { focus.requestFocus() }
    }
}

@Composable
fun MediaButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val k = LocalKiez.current
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = k.muted, contentColor = k.text) {
        Icon(icon, label, Modifier.padding(11.dp).size(22.dp))
    }
}

@Composable
fun MediaPreview(m: PickedMedia, modifier: Modifier, onRemove: () -> Unit) {
    val k = LocalKiez.current
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(k.muted)) {
        if (m.isVideo) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Rounded.Videocam, null, tint = k.primary, modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(6.dp))
                Text("Video ausgewählt", style = MaterialTheme.typography.labelLarge, color = k.text)
            }
        } else {
            AsyncImage(m.uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Surface(
            onClick = onRemove, shape = CircleShape, color = Color.Black.copy(alpha = 0.55f), contentColor = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        ) { Icon(Icons.Rounded.Close, "Entfernen", Modifier.padding(6.dp).size(18.dp)) }
    }
}
