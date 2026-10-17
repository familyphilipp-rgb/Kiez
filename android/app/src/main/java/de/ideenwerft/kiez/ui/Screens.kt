package de.ideenwerft.kiez.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.ideenwerft.kiez.BuildConfig
import de.ideenwerft.kiez.data.ActivityKind
import de.ideenwerft.kiez.ui.theme.LocalKiez

// --- Meine ---
@Composable
fun MineScreen(vm: KiezViewModel, openPost: (Long) -> Unit) {
    val listState = rememberLazyListState()
    val active by rememberActiveVideo(listState, vm.activity)
    LaunchedEffect(Unit) { vm.loadActivity() }
    LazyColumn(state = listState, contentPadding = ListPadding, modifier = Modifier.fillMaxSize()) {
        item("head") { ScreenHeader("Meine", "Deine Spuren im Kiez", "Nur du siehst diese Übersicht.") }
        item("seg") {
            Segmented(ActivityKind.entries.map { it to it.label }, vm.activityKind, { vm.changeActivity(it) }, Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
        if (vm.activity.isEmpty()) {
            when (val s = vm.activityState) {
                LoadState.Loading -> items(3) { SkeletonCard() }
                is LoadState.Failed -> item("err") {
                    EmptyState("Keine Verbindung", s.message, Icons.Rounded.CloudOff) { Button(onClick = { vm.loadActivity() }) { Text("Nochmal versuchen") } }
                }
                LoadState.Ready -> item("empty") {
                    EmptyState(
                        "Noch nichts hier",
                        when (vm.activityKind) {
                            ActivityKind.POSTS -> "Deine Posts erscheinen hier."
                            ActivityKind.REPLIES -> "Posts, auf die du geantwortet hast, erscheinen hier."
                            ActivityKind.VOTED -> "Posts, für die du gevotet hast, erscheinen hier."
                        },
                        Icons.Rounded.Edit,
                    )
                }
            }
        }
        items(vm.activity, key = { it.id }) { p ->
            PostCard(p, vm, videoActive = active == p.id, modifier = Modifier.animateItem(), onOpen = { openPost(p.id) })
        }
    }
}

// --- Kanäle ---
@Composable
fun ChannelsScreen(vm: KiezViewModel, openTag: (String) -> Unit) {
    val k = LocalKiez.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(2) }) {
            ScreenHeader("Kanäle", "Was im Kiez läuft", "Jeder #Hashtag wird zum Kanal. Tippe einen an, um nur seine Posts zu sehen.")
        }
        if (vm.trending.isEmpty()) {
            item(span = { GridItemSpan(2) }) {
                EmptyState("Noch keine Kanäle", "Schreib einen Post mit #hashtag, dann taucht er hier auf.", Icons.Rounded.Tag)
            }
        }
        items(vm.trending, key = { it.tag }) { t ->
            val i = vm.trending.indexOf(t)
            val c = lerp(k.primary, Color(0xFF22262E), (i % 4) * 0.18f)
            Surface(
                onClick = { openTag(t.tag) },
                shape = RoundedCornerShape(22.dp), color = c,
                modifier = Modifier.aspectRatio(1.15f),
            ) {
                Box(Modifier.background(Brush.linearGradient(listOf(lerp(c, Color.White, 0.1f), lerp(c, Color.Black, 0.3f)))).padding(16.dp)) {
                    Text("#", color = Color.White.copy(alpha = 0.25f), fontSize = 64.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopEnd))
                    Column(Modifier.align(Alignment.BottomStart)) {
                        Text("#${t.tag}", style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 2)
                        Text(if (t.count == 1) "1 Post" else "${t.count} Posts", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                    }
                }
            }
        }
    }
}

// --- Profil ---
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(vm: KiezViewModel, chooseLocation: () -> Unit) {
    val k = LocalKiez.current
    var editServer by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refreshMe() }
    if (editServer) ServerDialog(vm) { editServer = false }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 120.dp)) {
        ScreenHeader("Profil", "Dein Kiez-Karma")
        KarmaCard(vm)

        Section("Standort") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (vm.usesGps()) Icons.Rounded.MyLocation else Icons.Rounded.LocationOn, null, tint = k.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(vm.place?.label ?: "Kein Ort gewählt", style = MaterialTheme.typography.titleMedium, color = k.text)
                    Text(if (vm.usesGps()) "Per GPS, auf ca. 100 m gerundet" else "Fest gewählter Ort", style = MaterialTheme.typography.bodySmall, color = k.mutedText)
                }
                OutlinedButton(onClick = chooseLocation) { Text("Ändern") }
            }
        }

        Section("Darstellung") {
            Segmented(listOf("auto" to "Auto", "light" to "Hell", "dark" to "Dunkel"), vm.theme, { vm.changeTheme(it) })
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Vibration", style = MaterialTheme.typography.titleMedium, color = k.text)
                    Text("Kurzes Feedback beim Voten und Tippen", style = MaterialTheme.typography.bodySmall, color = k.mutedText)
                }
                Switch(vm.haptics, { vm.changeHaptics(it) }, colors = SwitchDefaults.colors(checkedTrackColor = k.primary))
            }
        }

        Section("Server") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Dns, null, tint = k.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(vm.serverUrl.removePrefix("https://"), style = MaterialTheme.typography.titleMedium, color = k.text, maxLines = 2)
                    Text(if (vm.customServer) "Eigener Server" else "Standard-Server", style = MaterialTheme.typography.bodySmall, color = k.mutedText)
                }
                OutlinedButton(onClick = { editServer = true }) { Text("Ändern") }
            }
        }

        Section("Anonym") {
            Row {
                Icon(Icons.Rounded.Shield, null, tint = k.primary)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Kiez kennt keinen Namen, keine Telefonnummer und keine E-Mail. Dein Standort wird gerundet, Fotos und Videos werden ohne Metadaten gespeichert. Posts mit −5 Punkten verschwinden.",
                    style = MaterialTheme.typography.bodyMedium, color = k.mutedText,
                )
            }
        }

        Text(
            "Kiez ${BuildConfig.VERSION_NAME} · Ideenwerft Friedenau\nOrtsnamen: GeoNames (CC BY 4.0)",
            style = MaterialTheme.typography.bodySmall, color = k.mutedText,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
        )
    }
}

@Composable
private fun ServerDialog(vm: KiezViewModel, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(vm.serverUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server-Adresse") },
        text = {
            Column {
                Text(
                    "Die Adresse, unter der dein Kiez-Server läuft. Jeder Server ist ein eigener Kiez mit eigenen Posts.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    text, { text = it }, singleLine = true, placeholder = { Text("https://kiez.example.de") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
        },
        confirmButton = { TextButton(onClick = { if (vm.changeServer(text)) onDismiss() }) { Text("Speichern") } },
        dismissButton = {
            TextButton(onClick = { vm.changeServer(null); onDismiss() }) { Text("Standard") }
        },
    )
}

@Composable
private fun KarmaCard(vm: KiezViewModel) {
    val k = LocalKiez.current
    val me = vm.me
    val level = me?.level
    val next = level?.next
    val from = level?.from ?: 0
    val progress = if (next != null && next.at > from) ((vm.karma - from).toFloat() / (next.at - from)).coerceIn(0f, 1f) else 1f
    Surface(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), shape = RoundedCornerShape(26.dp), color = k.primary, shadowElevation = 4.dp) {
        Column(Modifier.background(Brush.linearGradient(listOf(lerp(k.primary, Color.White, 0.08f), lerp(k.primary, Color.Black, 0.3f)))).padding(22.dp)) {
            EyebrowText(level?.name ?: "Neuling", color = Color.White.copy(alpha = 0.85f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${vm.karma}", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.width(8.dp))
                Text("Karma", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(bottom = 12.dp))
            }
            Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))) {
                Box(Modifier.fillMaxWidth(progress).height(8.dp).clip(CircleShape).background(Color.White))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (next != null) "Noch ${next.at - vm.karma} bis ${next.name}" else "Höchste Stufe erreicht",
                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(18.dp))
            Row {
                Stat("Posts", me?.posts ?: 0, Modifier.weight(1f))
                Stat("Antworten", me?.replies ?: 0, Modifier.weight(1f))
                Stat("Votes", me?.votes ?: 0, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, modifier: Modifier) {
    Column(modifier) {
        Text("$value", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val k = LocalKiez.current
    EyebrowText(title, modifier = Modifier.padding(start = 22.dp, top = 22.dp, bottom = 8.dp))
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp), shape = RoundedCornerShape(22.dp), color = k.card,
        border = BorderStroke(1.dp, k.border),
    ) { Column(Modifier.padding(18.dp)) { content() } }
}

// --- Ort wählen ---
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LocationSheet(vm: KiezViewModel, useGps: () -> Unit, onDismiss: () -> Unit) {
    val k = LocalKiez.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = k.card) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            EyebrowText("Standort")
            Spacer(Modifier.height(4.dp))
            Text("Wo ist dein Kiez?", style = MaterialTheme.typography.titleLarge, color = k.text)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { useGps(); onDismiss() }, modifier = Modifier.fillMaxWidth(), shape = CircleShape) {
                Icon(Icons.Rounded.MyLocation, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Meinen Standort verwenden")
            }
            Spacer(Modifier.height(18.dp))
            EyebrowText("Oder einen Ort ausprobieren", color = k.mutedText)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CITIES.keys.forEach { name ->
                    TagChip(name, active = vm.place?.gps == false && vm.place?.label == name) { vm.chooseCity(name); onDismiss() }
                }
            }
        }
    }
}
