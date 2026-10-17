package de.ideenwerft.kiez.ui

import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import de.ideenwerft.kiez.KiezApp
import de.ideenwerft.kiez.R
import de.ideenwerft.kiez.data.Media
import de.ideenwerft.kiez.data.Post
import kotlin.math.abs

// Ton gilt für alle Videos: einmal eingeschaltet, bleibt er an
object VideoSound {
    var muted by mutableStateOf(true)
}

@Composable
fun PostMedia(media: Media, active: Boolean, modifier: Modifier = Modifier) {
    val api = KiezApp.instance.api
    val ratio = (media.aspectRatio ?: if (media.isVideo) 16f / 9 else 4f / 3).coerceIn(0.75f, 1.9f)
    Box(modifier.aspectRatio(ratio).background(Color.Black.copy(alpha = 0.12f))) {
        if (!media.isVideo) {
            AsyncImage(api.absolute(media.url), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            return@Box
        }
        var rendered by remember(active) { mutableStateOf(false) }
        if (active) VideoPlayer(api.absolute(media.url), VideoSound.muted, { rendered = true }, Modifier.matchParentSize())
        if (!rendered && media.poster != null) {
            AsyncImage(api.absolute(media.poster), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
        if (!active) {
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.45f), contentColor = Color.White, modifier = Modifier.align(Alignment.Center)) {
                Icon(Icons.Rounded.PlayArrow, null, Modifier.padding(12.dp).size(30.dp))
            }
        }
        Surface(
            onClick = { VideoSound.muted = !VideoSound.muted },
            shape = CircleShape, color = Color.Black.copy(alpha = 0.45f), contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
        ) {
            Icon(
                if (VideoSound.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                if (VideoSound.muted) "Ton an" else "Ton aus", Modifier.padding(8.dp).size(18.dp),
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(url: String, muted: Boolean, onFirstFrame: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val firstFrame by rememberUpdatedState(onFirstFrame)
    val player = remember(url) {
        val app = KiezApp.instance
        // Erst im Zwischenspeicher suchen, sonst über die gemeinsame Verbindung laden
        val source = CacheDataSource.Factory()
            .setCache(app.videoCache)
            .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(app.http))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(source)).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            prepare()
        }
    }
    LaunchedEffect(player, muted) { player.volume = if (muted) 0f else 1f }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(player, lifecycle) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() = firstFrame()
        }
        player.addListener(listener)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.pause()
                Lifecycle.Event.ON_RESUME -> player.play()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }
    AndroidView(
        factory = { ctx -> (LayoutInflater.from(ctx).inflate(R.layout.kiez_player, null) as PlayerView).also { it.player = player } },
        update = { it.player = player },
        modifier = modifier,
    )
}

// Das Video, das der Bildschirmmitte am nächsten ist, spielt automatisch (stumm)
@Composable
fun rememberActiveVideo(state: LazyListState, posts: List<Post>): State<Long?> {
    val current by rememberUpdatedState(posts)
    return remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            val videos = current.filter { it.media?.isVideo == true }.map { it.id }.toSet()
            info.visibleItemsInfo
                .filter { it.key is Long && it.key in videos }
                .minByOrNull { abs(it.offset + it.size / 2 - center) }
                ?.key as? Long
        }
    }
}
