package de.ideenwerft.kiez.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.ideenwerft.kiez.data.Sort
import de.ideenwerft.kiez.ui.theme.LocalKiez
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(vm: KiezViewModel, openPost: (Long) -> Unit, requestLocation: () -> Unit) {
    if (vm.place == null) {
        NoLocation(vm, requestLocation)
        return
    }
    val k = LocalKiez.current
    val haptics = rememberHaptics(vm.haptics)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val active by rememberActiveVideo(listState, vm.feed)

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = { haptics.tick(); vm.loadFeed(pull = true) }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(state = listState, contentPadding = ListPadding, modifier = Modifier.fillMaxSize()) {
                item("sort") {
                    Segmented(
                        Sort.entries.map { it to it.label }, vm.sort,
                        { haptics.tick(); vm.changeSort(it) },
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
                if (vm.trending.isNotEmpty() || vm.tag != null) {
                    item("tags") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            vm.tag?.let { t -> item("active") { TagChip("#$t  ✕", active = true) { vm.openTag(null) } } }
                            items(vm.trending.filter { it.tag != vm.tag }, key = { it.tag }) { t ->
                                TagChip("#${t.tag}") { haptics.tick(); vm.openTag(t.tag) }
                            }
                        }
                    }
                }
                if (vm.offline) item("offline") { OfflineBanner() }

                if (vm.feed.isEmpty()) {
                    when (val s = vm.feedState) {
                        LoadState.Loading -> items(4) { SkeletonCard() }
                        is LoadState.Failed -> item("error") {
                            EmptyState("Keine Verbindung", s.message, Icons.Rounded.CloudOff) {
                                Button(onClick = { vm.loadFeed() }) { Text("Nochmal versuchen") }
                            }
                        }
                        LoadState.Ready -> item("empty") {
                            EmptyState(
                                if (vm.tag != null) "Noch nichts zu #${vm.tag}" else "Hier ist es noch ruhig",
                                "Schreib den ersten Post in deinem Kiez. Alle im Umkreis von ${vm.radiusKm} km sehen ihn.",
                                Icons.Rounded.Forum,
                            )
                        }
                    }
                }
                items(vm.feed, key = { it.id }) { p ->
                    PostCard(
                        p, vm, videoActive = active == p.id,
                        modifier = Modifier.animateItem(),
                        onOpen = { openPost(p.id) },
                        onTag = { vm.openTag(it) },
                    )
                }
            }
        }

        AnimatedVisibility(
            vm.freshCount > 0,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
            enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut(),
        ) {
            Surface(
                onClick = { haptics.tick(); scope.launch { listState.animateScrollToItem(0) }; vm.loadFeed() },
                shape = CircleShape, color = k.primary, contentColor = Color.White, shadowElevation = 8.dp,
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ArrowUpward, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (vm.freshCount == 1) "1 neuer Post" else "${vm.freshCount} neue Posts", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun OfflineBanner() {
    val k = LocalKiez.current
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp), color = k.muted, contentColor = k.mutedText,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CloudOff, null, Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("Offline. Du siehst die zuletzt geladenen Posts.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoLocation(vm: KiezViewModel, requestLocation: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            "Wo ist dein Kiez?",
            "Kiez zeigt dir, was im Umkreis von 10 km los ist. Gib deinen Standort frei oder wähle einen Ort.",
            Icons.Rounded.LocationOn,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = requestLocation, enabled = !vm.locating) {
                    if (vm.locating) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.MyLocation, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (vm.locating) "Suche Standort …" else "Standort freigeben")
                }
                Spacer(Modifier.height(18.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CITIES.keys.forEach { name -> TagChip(name) { vm.chooseCity(name) } }
                }
            }
        }
    }
}
