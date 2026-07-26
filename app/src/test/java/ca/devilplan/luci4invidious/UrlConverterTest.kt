package ca.devilplan.luci4invidious

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [UrlConverter].
 * These run on the JVM — no device or Robolectric needed.
 */
class UrlConverterTest {

    // ── Standard watch URLs ──────────────────────────────────────────

    @Test
    fun `convert standard youtube watch url`() {
        val result = UrlConverter.convert("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals("https://my.invidious.org/watch?v=dQw4w9WgXcQ", result)
    }

    @Test
    fun `convert youtube without www`() {
        val result = UrlConverter.convert("https://youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals("https://my.invidious.org/watch?v=dQw4w9WgXcQ", result)
    }

    @Test
    fun `convert mobile youtube`() {
        val result = UrlConverter.convert("https://m.youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals("https://my.invidious.org/watch?v=dQw4w9WgXcQ", result)
    }

    @Test
    fun `convert music youtube`() {
        val result = UrlConverter.convert("https://music.youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals("https://my.invidious.org/watch?v=dQw4w9WgXcQ", result)
    }

    // ── youtu.be short links ─────────────────────────────────────────

    @Test
    fun `convert youtu be short link`() {
        val result = UrlConverter.convert("https://youtu.be/dQw4w9WgXcQ")
        assertEquals("https://my.invidious.org/watch?v=dQw4w9WgXcQ", result)
    }

    @Test
    fun `convert youtu be with timestamp`() {
        val result = UrlConverter.convert("https://youtu.be/dQw4w9WgXcQ?t=120")
        assertEquals("https://my.invidious.org/watch?v=dQw4w9WgXcQ&t=120", result)
    }

    // ── Shorts & embeds ──────────────────────────────────────────────

    @Test
    fun `convert youtube shorts`() {
        val result = UrlConverter.convert("https://www.youtube.com/shorts/abc123DEF")
        assertEquals("https://my.invidious.org/shorts/abc123DEF", result)
    }

    @Test
    fun `convert mobile youtube shorts`() {
        val result = UrlConverter.convert("https://m.youtube.com/shorts/abc123DEF")
        assertEquals("https://my.invidious.org/shorts/abc123DEF", result)
    }

    @Test
    fun `convert youtube embed`() {
        val result = UrlConverter.convert("https://www.youtube.com/embed/dQw4w9WgXcQ")
        assertEquals("https://my.invidious.org/embed/dQw4w9WgXcQ", result)
    }

    // ── Query parameter preservation ─────────────────────────────────

    @Test
    fun `preserves timestamp parameter on watch url`() {
        val result = UrlConverter.convert("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s")
        assertEquals("https://my.invidious.org/watch?v=dQw4w9WgXcQ&t=42s", result)
    }

    @Test
    fun `preserves playlist parameter`() {
        val result = UrlConverter.convert(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLrAXtmRdnEQy6nuUf"
        )
        assertEquals(
            "https://my.invidious.org/watch?v=dQw4w9WgXcQ&list=PLrAXtmRdnEQy6nuUf",
            result
        )
    }

    // ── Edge cases & invalid input ───────────────────────────────────

    @Test
    fun `returns null for non youtube url`() {
        assertNull(UrlConverter.convert("https://example.com/watch?v=abc"))
    }

    @Test
    fun `returns null for youtube homepage`() {
        assertNull(UrlConverter.convert("https://www.youtube.com/"))
    }

    @Test
    fun `returns null for youtube root path`() {
        assertNull(UrlConverter.convert("https://youtube.com"))
    }

    @Test
    fun `returns null for malformed url`() {
        assertNull(UrlConverter.convert("not a url at all"))
    }

    @Test
    fun `returns null for empty string`() {
        assertNull(UrlConverter.convert(""))
    }

    @Test
    fun `returns null for http scheme on non-youtube host`() {
        assertNull(UrlConverter.convert("http://example.com/watch?v=abc"))
    }

    // ── HTTP scheme (some legacy links) ──────────────────────────────

    @Test
    fun `convert http youtube watch url`() {
        val result = UrlConverter.convert("http://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals("https://my.invidious.org/watch?v=dQw4w9WgXcQ", result)
    }

    @Test
    fun `convert http youtu be`() {
        val result = UrlConverter.convert("http://youtu.be/dQw4w9WgXcQ")
        assertEquals("https://my.invidious.org/watch?v=dQw4w9WgXcQ", result)
    }

    // ── Case insensitivity ───────────────────────────────────────────

    @Test
    fun `host is case insensitive`() {
        val result = UrlConverter.convert("https://WWW.YouTube.com/watch?v=dQw4w9WgXcQ")
        assertEquals("https://my.invidious.org/watch?v=dQw4w9WgXcQ", result)
    }
}