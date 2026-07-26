package ca.devilplan.luci4invidious

import java.net.URL
import java.util.Locale

/**
 * Converts YouTube URLs to Invidious URLs.
 * Uses only JDK classes so it can be unit-tested without Robolectric.
 */
object UrlConverter {

    // ── Hardcoded Invidious instance config ────────────────────────────
    const val INVIDIOUS_HOST = "my.invidious.org"
    const val INVIDIOUS_USER = "user"
    const val INVIDIOUS_PASS = "pass"

    // ── Known YouTube hosts ────────────────────────────────────────────
    private val YOUTUBE_HOSTS = setOf(
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "music.youtube.com"
    )

    /**
     * Converts a YouTube URL to the equivalent Invidious URL.
     * Returns null if the input is not a recognised YouTube video URL.
     */
    fun convert(youtubeUrl: String): String? {
        return try {
            val url = URL(youtubeUrl)
            val host = url.host.lowercase(Locale.US)

            when (host) {
                "youtu.be" -> {
                    // youtu.be/VIDEO_ID  →  invidious/watch?v=VIDEO_ID
                    val videoId = url.path.removePrefix("/")
                    if (videoId.isBlank()) return null
                    val extraQuery = url.query?.let { "&$it" } ?: ""
                    "https://$INVIDIOUS_HOST/watch?v=$videoId$extraQuery"
                }

                in YOUTUBE_HOSTS -> {
                    val path = url.path
                    if (path.isBlank() || path == "/") return null
                    val query = url.query?.let { "?$it" } ?: ""
                    "https://$INVIDIOUS_HOST$path$query"
                }

                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}