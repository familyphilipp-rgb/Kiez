package de.ideenwerft.kiez.data

import kotlinx.serialization.Serializable

@Serializable
data class Media(
    val kind: String,
    val url: String,
    val poster: String? = null,
    val width: Int? = null,
    val height: Int? = null,
) {
    val isVideo get() = kind == "video"
    val aspectRatio: Float?
        get() = if (width != null && height != null && width > 0 && height > 0) width.toFloat() / height else null
}

@Serializable
data class Post(
    val id: Long,
    val text: String = "",
    val color: String = "#CD1818",
    val createdAt: Long,
    val score: Int = 0,
    val commentCount: Int = 0,
    val myVote: Int = 0,
    val mine: Boolean = false,
    val distance: String? = null,
    val media: Media? = null,
    val isOP: Boolean? = null,
    val authorNo: Int? = null,
)

@Serializable
data class Trend(val tag: String, val count: Int)

@Serializable
data class FeedResponse(val radiusKm: Int = 10, val trending: List<Trend> = emptyList(), val posts: List<Post> = emptyList())

@Serializable
data class PostsResponse(val posts: List<Post> = emptyList())

@Serializable
data class DetailResponse(val post: Post, val replies: List<Post> = emptyList())

@Serializable
data class VoteResponse(val score: Int, val myVote: Int)

@Serializable
data class NextLevel(val name: String, val at: Int)

@Serializable
data class Level(val name: String, val next: NextLevel? = null, val from: Int = 0)

@Serializable
data class Me(
    val karma: Int = 0,
    val posts: Int = 0,
    val replies: Int = 0,
    val votes: Int = 0,
    val level: Level = Level("Neuling"),
)

@Serializable
data class UploadResponse(val id: String, val kind: String, val url: String, val poster: String? = null)

@Serializable
data class PlaceResponse(val name: String? = null)

@Serializable
data class RegisterResponse(val token: String)

@Serializable
data class ErrorResponse(val error: String = "")

@Serializable
data class NewPost(val text: String, val mediaId: String? = null, val lat: Double? = null, val lng: Double? = null)

@Serializable
data class VoteRequest(val value: Int)

enum class Sort(val key: String, val label: String) { NEW("new", "Neueste"), POPULAR("popular", "Beliebt"), LOUD("loud", "Am lautesten") }

enum class ActivityKind(val key: String, val label: String) { POSTS("posts", "Posts"), REPLIES("replies", "Kommentiert"), VOTED("voted", "Gevotet") }

data class Coords(val lat: Double, val lng: Double)
