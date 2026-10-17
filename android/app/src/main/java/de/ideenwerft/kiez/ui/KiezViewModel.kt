package de.ideenwerft.kiez.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.ideenwerft.kiez.KiezApp
import de.ideenwerft.kiez.data.ActivityKind
import de.ideenwerft.kiez.data.ApiException
import de.ideenwerft.kiez.data.Coords
import de.ideenwerft.kiez.data.Me
import de.ideenwerft.kiez.data.NewPost
import de.ideenwerft.kiez.data.PickedMedia
import de.ideenwerft.kiez.data.Post
import de.ideenwerft.kiez.data.SavedPlace
import de.ideenwerft.kiez.data.Sort
import de.ideenwerft.kiez.data.Trend
import de.ideenwerft.kiez.data.currentLocation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Orte zum Ausprobieren, falls der Standort nicht freigegeben ist
val CITIES = linkedMapOf(
    "Friedenau" to Coords(52.4707, 13.3304), "Schöneberg" to Coords(52.4826, 13.3532), "Kreuzberg" to Coords(52.4986, 13.4030),
    "Mitte" to Coords(52.52, 13.405), "Hamburg" to Coords(53.551, 9.993), "München" to Coords(48.137, 11.575),
    "Köln" to Coords(50.937, 6.96), "Leipzig" to Coords(51.34, 12.375),
)

sealed interface LoadState {
    data object Loading : LoadState
    data object Ready : LoadState
    data class Failed(val message: String) : LoadState
}

class KiezViewModel : ViewModel() {
    private val app = KiezApp.instance
    private val api = app.api
    private val prefs = app.prefs

    var theme by mutableStateOf(prefs.theme); private set
    var haptics by mutableStateOf(prefs.haptics); private set
    var place by mutableStateOf(prefs.place); private set
    var locating by mutableStateOf(false); private set
    var needsLocation by mutableStateOf(false); private set
    var karma by mutableIntStateOf(0); private set
    var me by mutableStateOf<Me?>(null); private set
    var message by mutableStateOf<String?>(null)

    // Feed
    var sort by mutableStateOf(Sort.NEW); private set
    var tag by mutableStateOf<String?>(null); private set
    val feed = mutableStateListOf<Post>()
    var trending by mutableStateOf<List<Trend>>(emptyList()); private set
    var radiusKm by mutableIntStateOf(10); private set
    var feedState by mutableStateOf<LoadState>(LoadState.Loading); private set
    var refreshing by mutableStateOf(false); private set
    var offline by mutableStateOf(false); private set
    var freshCount by mutableIntStateOf(0); private set
    private var feedJob: Job? = null

    // Meine
    var activityKind by mutableStateOf(ActivityKind.POSTS); private set
    val activity = mutableStateListOf<Post>()
    var activityState by mutableStateOf<LoadState>(LoadState.Loading); private set

    val coords: Coords? get() = place?.coords

    init {
        // Gespeicherten Feed sofort zeigen, dann frisch laden
        prefs.cachedFeed?.let { raw -> runCatching { api.parseFeed(raw) }.getOrNull()?.let { feed.addAll(it.posts); trending = it.trending } }
        if (place != null) { loadFeed(); refreshMe() }
        viewModelScope.launch { pollFresh() }
    }

    fun toast(msg: String) { message = msg }

    // --- Standort ---
    fun onLocationPermission(context: Context, granted: Boolean) {
        if (!granted) { if (place == null) needsLocation = true; return }
        viewModelScope.launch {
            locating = true
            val c = currentLocation(context)
            locating = false
            if (c == null) { if (place == null) needsLocation = true; toast("Standort nicht gefunden"); return@launch }
            setPlace(SavedPlace(c, place?.takeIf { it.gps && near(it.coords, c) }?.label ?: "In deiner Nähe", gps = true))
            runCatching { api.place(c) }.getOrNull()?.let { name -> place?.let { setPlace(it.copy(label = name), reload = false) } }
        }
    }

    private fun near(a: Coords, b: Coords) = kotlin.math.abs(a.lat - b.lat) < 0.01 && kotlin.math.abs(a.lng - b.lng) < 0.015

    fun chooseCity(name: String) { CITIES[name]?.let { setPlace(SavedPlace(it, name, gps = false)) } }

    private fun setPlace(p: SavedPlace, reload: Boolean = true) {
        val moved = place?.coords != p.coords
        place = p; prefs.place = p; needsLocation = false
        if (reload && moved) { tag = null; loadFeed(); refreshMe() }
        else if (reload && feed.isEmpty()) loadFeed()
    }

    fun usesGps() = place?.gps != false

    // --- Feed ---
    fun changeSort(s: Sort) { if (s != sort) { sort = s; loadFeed() } }
    fun openTag(t: String?) { tag = t; loadFeed() }

    fun loadFeed(pull: Boolean = false) {
        val c = coords ?: return
        feedJob?.cancel()
        feedJob = viewModelScope.launch {
            if (pull) refreshing = true else if (feed.isEmpty() || feedState is LoadState.Failed) feedState = LoadState.Loading
            try {
                val raw = api.feedRaw(c, sort, tag)
                val res = api.parseFeed(raw)
                feed.clear(); feed.addAll(res.posts)
                trending = res.trending; radiusKm = res.radiusKm
                if (sort == Sort.NEW && tag == null) prefs.cachedFeed = raw
                feedState = LoadState.Ready; offline = false; freshCount = 0
            } catch (e: ApiException) {
                if (feed.isNotEmpty()) { offline = true; feedState = LoadState.Ready } else feedState = LoadState.Failed(e.message ?: "Fehler")
            } finally { refreshing = false }
        }
    }

    private suspend fun pollFresh() {
        while (true) {
            delay(30_000)
            val c = coords ?: continue
            if (sort != Sort.NEW || feedState !is LoadState.Ready) continue
            runCatching { api.parseFeed(api.feedRaw(c, Sort.NEW, tag)) }.getOrNull()?.let { res ->
                val known = feed.map { it.id }.toSet()
                freshCount = res.posts.count { it.id !in known && !it.mine }
                offline = false
            }
        }
    }

    // Votes werden sofort angezeigt und bei einem Fehler zurückgesetzt
    fun vote(post: Post, value: Int, onResult: (Post) -> Unit) {
        val optimistic = post.copy(myVote = value, score = post.score + value - post.myVote)
        onResult(optimistic); replaceEverywhere(optimistic)
        viewModelScope.launch {
            try {
                val r = api.vote(post.id, value)
                val confirmed = optimistic.copy(score = r.score, myVote = r.myVote)
                onResult(confirmed); replaceEverywhere(confirmed)
            } catch (e: ApiException) { onResult(post); replaceEverywhere(post); toast(e.message ?: "Fehler") }
        }
    }

    private fun replaceEverywhere(p: Post) {
        feed.indexOfFirst { it.id == p.id }.takeIf { it >= 0 }?.let { feed[it] = feed[it].copy(score = p.score, myVote = p.myVote) }
        activity.indexOfFirst { it.id == p.id }.takeIf { it >= 0 }?.let { activity[it] = activity[it].copy(score = p.score, myVote = p.myVote) }
    }

    fun report(post: Post) = viewModelScope.launch {
        try { api.report(post.id); toast("Danke, wurde gemeldet") } catch (e: ApiException) { toast(e.message ?: "Fehler") }
    }

    fun delete(post: Post, then: () -> Unit = {}) = viewModelScope.launch {
        try {
            api.delete(post.id)
            feed.removeAll { it.id == post.id }; activity.removeAll { it.id == post.id }
            toast("Gelöscht"); refreshMe(); then()
        } catch (e: ApiException) { toast(e.message ?: "Fehler") }
    }

    // --- Neuer Post ---
    var uploadProgress by mutableStateOf<Float?>(null); private set
    var sending by mutableStateOf(false); private set

    fun publish(context: Context, text: String, media: PickedMedia?, replyTo: Long? = null, done: () -> Unit) {
        val c = coords
        if (replyTo == null && c == null) { toast("Erst einen Standort wählen"); return }
        if (text.isBlank() && media == null) { toast("Schreib etwas oder füge ein Foto/Video hinzu"); return }
        viewModelScope.launch {
            sending = true
            try {
                val mediaId = media?.let { m ->
                    uploadProgress = 0f
                    api.upload(context.contentResolver, m.uri, m.mime) { uploadProgress = it }.id
                }
                if (replyTo != null) api.reply(replyTo, text.trim(), mediaId)
                else {
                    api.createPost(NewPost(text.trim(), mediaId, c!!.lat, c.lng))
                    sort = Sort.NEW; loadFeed()
                }
                refreshMe(); done()
            } catch (e: ApiException) { toast(e.message ?: "Fehler") } finally { sending = false; uploadProgress = null }
        }
    }

    // --- Meine und Profil ---
    fun changeActivity(k: ActivityKind) { activityKind = k; loadActivity() }

    fun loadActivity() = viewModelScope.launch {
        activityState = LoadState.Loading
        try {
            val res = api.activity(activityKind, coords)
            activity.clear(); activity.addAll(res.posts); activityState = LoadState.Ready
        } catch (e: ApiException) { activityState = LoadState.Failed(e.message ?: "Fehler") }
    }

    fun refreshMe() = viewModelScope.launch {
        runCatching { api.me() }.getOrNull()?.let { me = it; karma = it.karma }
    }

    fun changeTheme(t: String) { theme = t; prefs.theme = t }
    fun changeHaptics(on: Boolean) { haptics = on; prefs.haptics = on }

    suspend fun loadPost(id: Long) = api.post(id, coords)
}
