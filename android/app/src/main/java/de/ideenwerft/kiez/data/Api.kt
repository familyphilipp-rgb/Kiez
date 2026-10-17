package de.ideenwerft.kiez.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.source
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApiException(message: String, val status: Int = 0) : Exception(message)

// Verbindung zum Kiez-Server (dieselbe REST-API wie die Web-App)
class Api(private val baseUrl: String, val client: OkHttpClient, private val prefs: Prefs) {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }
    private val jsonType = "application/json".toMediaType()
    private val registerLock = Mutex()

    fun absolute(path: String): String = if (path.startsWith("http")) path else baseUrl.trimEnd('/') + path

    private fun url(path: String, vararg query: Pair<String, Any?>): HttpUrl {
        val b = absolute(path).toHttpUrl().newBuilder()
        for ((k, v) in query) if (v != null) b.addQueryParameter(k, v.toString())
        return b.build()
    }

    private suspend fun ensureToken(): String = prefs.token ?: registerLock.withLock {
        prefs.token ?: register().also { prefs.token = it }
    }

    private suspend fun register(): String {
        val req = Request.Builder().url(url("/api/register")).post(ByteArray(0).toRequestBody(jsonType)).build()
        val body = execute(req)
        return json.decodeFromString<RegisterResponse>(body).token
    }

    private suspend fun call(build: (Request.Builder) -> Request.Builder, retried: Boolean = false): String {
        val token = ensureToken()
        val req = build(Request.Builder()).header("Authorization", "Bearer $token").build()
        return try {
            execute(req)
        } catch (e: ApiException) {
            if (e.status == 401 && !retried) {
                prefs.token = null
                call(build, retried = true)
            } else throw e
        }
    }

    private suspend fun execute(req: Request): String = withContext(Dispatchers.IO) {
        val res = try { client.newCall(req).await() } catch (e: IOException) {
            throw ApiException("Keine Verbindung zum Kiez-Server")
        }
        res.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val msg = runCatching { json.decodeFromString<ErrorResponse>(text).error }.getOrNull()
                throw ApiException(msg?.ifBlank { null } ?: "Fehler ${it.code}", it.code)
            }
            text
        }
    }

    private suspend inline fun <reified T> get(path: String, vararg query: Pair<String, Any?>): T =
        json.decodeFromString(call({ it.url(url(path, *query)).get() }))

    private suspend inline fun <reified B, reified T> postJson(path: String, body: B): T =
        json.decodeFromString(call({ it.url(url(path)).post(json.encodeToString(body).toRequestBody(jsonType)) }))

    // --- Endpunkte ---
    suspend fun feedRaw(c: Coords, sort: Sort, tag: String?): String =
        call({ it.url(url("/api/posts", "lat" to c.lat, "lng" to c.lng, "sort" to sort.key, "tag" to tag)).get() })

    fun parseFeed(raw: String): FeedResponse = json.decodeFromString(raw)

    suspend fun post(id: Long, c: Coords?): DetailResponse = get("/api/posts/$id", "lat" to c?.lat, "lng" to c?.lng)

    suspend fun createPost(p: NewPost): Post = postJson("/api/posts", p)

    suspend fun reply(id: Long, text: String, mediaId: String?) {
        call({ it.url(url("/api/posts/$id/replies")).post(json.encodeToString(NewPost(text, mediaId)).toRequestBody(jsonType)) })
    }

    suspend fun vote(id: Long, value: Int): VoteResponse = postJson("/api/posts/$id/vote", VoteRequest(value))

    suspend fun report(id: Long) { call({ it.url(url("/api/posts/$id/report")).post(ByteArray(0).toRequestBody(jsonType)) }) }

    suspend fun delete(id: Long) { call({ it.url(url("/api/posts/$id")).delete() }) }

    suspend fun me(): Me = get("/api/me")

    suspend fun activity(kind: ActivityKind, c: Coords?): PostsResponse =
        get("/api/me/activity", "kind" to kind.key, "lat" to c?.lat, "lng" to c?.lng)

    suspend fun place(c: Coords): String? = get<PlaceResponse>("/api/place", "lat" to c.lat, "lng" to c.lng).name

    // Datei als Rohdaten hochladen, mit Fortschritt (0..1)
    suspend fun upload(resolver: ContentResolver, uri: Uri, mime: String, onProgress: (Float) -> Unit): UploadResponse {
        val size = if (uri.scheme == "file") java.io.File(uri.path!!).length()
        else resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
        } ?: -1L
        val body = object : RequestBody() {
            override fun contentType(): MediaType = mime.toMediaType()
            override fun contentLength(): Long = size
            override fun writeTo(sink: BufferedSink) {
                val input = resolver.openInputStream(uri) ?: throw IOException("Datei nicht lesbar")
                input.source().use { src ->
                    var sent = 0L
                    while (true) {
                        val n = src.read(sink.buffer, 64 * 1024)
                        if (n == -1L) break
                        sent += n
                        sink.emit()
                        if (size > 0) onProgress((sent.toFloat() / size).coerceAtMost(1f))
                    }
                }
            }
        }
        return json.decodeFromString(call({ it.url(url("/api/media")).post(body) }))
    }
}

suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) = cont.resume(response)
        override fun onFailure(call: Call, e: IOException) { if (!cont.isCancelled) cont.resumeWithException(e) }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
