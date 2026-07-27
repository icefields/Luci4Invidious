package com.devilplan.luci4invidious

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [UrlConverter].
 * These run on the JVM — no device or Robolectric needed.
 */
class UrlConverterTest {

    private lateinit var converter: UrlConverter

    @Before
    fun setUp() {
        converter = UrlConverter("my.invidious.org")
    }

    // ── Standard watch URLs ──────────────────────────────────────────

    @Test
    fun `convert standard youtube watch url`() {
        val result = converter.convert("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals("https://my.invidious.org/watch?v=dQw4w9WgXcQ", result)
    }

    @Test
    fun `convert youtube without www`() {
        val result = converter.convert("https://youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals("https://my.invidious.org/watch?v=dQw4w9WgXcQ", result)
    }

    @Test
    fun `convert mobile youtube`() {
        val result = converter.convert("https://m.youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals("https://my.invidious.org/watch?v=dQw4w9WgXcQ", result)
    }

    @Test
    fun `convert music youtube`() {
        val result = converter.convert("https://music.youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals("https://my.invidious.org/watch?v=dQw4w9WgXcQ", result)
    }

    // ── youtu.be short links ─────────────────────────────────────────

    @Test
    fun `convert youtu be short link`() {
        val result = converter.convert("https://youtu.be/dQw4w9WgXcQ")
        assertEquals("https://my.invidious.org/watch?v=dQw4w9WgXcQ", result)
    }

    @Test
    fun `convert youtu be with timestamp`() {
        val result = converter.convert("https://youtu.be/dQw4w9WgXcQ?t=120")
        assertEquals("https://my.invidious.org/watch?v=dQw4w9WgXcQ&t=120", result)
    }

    @Test
    fun `convert youtu be with multiple params`() {
        val result = converter.convert("https://youtu.be/dQw4w9WgXcQ?t=120&si=abc123")
        assertEquals("https://my.invidious.org/watch?v=dQw4w9WgXcQ&t=120&si=abc123", result)
    }

    // ── Shorts & embeds ──────────────────────────────────────────────

    @Test
    fun `convert youtube shorts`() {
        val result = converter.convert("https://www.youtube.com/shorts/abc123DEF")
        assertEquals("https://my.invidious.org/shorts/abc123DEF", result)
    }

    @Test
    fun `convert mobile youtube shorts`() {
        val result = converter.convert("https://m.youtube.com/shorts/abc123DEF")
        assertEquals("https://my.invidious.org/shorts/abc123DEF", result)
    }

    @Test
    fun `convert youtube shorts without www`() {
        val result = converter.convert("https://youtube.com/shorts/abc123DEF")
        assertEquals("https://my.invidious.org/shorts/abc123DEF", result)
    }

    @Test
    fun `convert youtube embed`() {
        val result = converter.convert("https://www.youtube.com/embed/dQw4w9WgXcQ")
        assertEquals("https://my.invidious.org/embed/dQw4w9WgXcQ", result)
    }

    @Test
    fun `convert youtube embed without www`() {
        val result = converter.convert("https://youtube.com/embed/dQw4w9WgXcQ")
        assertEquals("https://my.invidious.org/embed/dQw4w9WgXcQ", result)
    }

    // ── Query parameter preservation ─────────────────────────────────

    @Test
    fun `preserves timestamp parameter on watch url`() {
        val result = converter.convert("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s")
        assertEquals("https://my.invidious.org/watch?v=dQw4w9WgXcQ&t=42s", result)
    }

    @Test
    fun `preserves playlist parameter`() {
        val result = converter.convert(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLrAXtmRdnEQy6nuUf"
        )
        assertEquals(
            "https://my.invidious.org/watch?v=dQw4w9WgXcQ&list=PLrAXtmRdnEQy6nuUf",
            result
        )
    }

    @Test
    fun `preserves multiple query parameters`() {
        val result = converter.convert(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s&list=PLrAXtmRdnEQy6nuUf"
        )
        assertEquals(
            "https://my.invidious.org/watch?v=dQw4w9WgXcQ&t=42s&list=PLrAXtmRdnEQy6nuUf",
            result
        )
    }

    // ── Edge cases & invalid input ───────────────────────────────────

    @Test
    fun `returns null for non youtube url`() {
        assertNull(converter.convert("https://example.com/watch?v=abc"))
    }

    @Test
    fun `returns null for youtube homepage`() {
        assertNull(converter.convert("https://www.youtube.com/"))
    }

    @Test
    fun `returns null for youtube root path`() {
        assertNull(converter.convert("https://youtube.com"))
    }

    @Test
    fun `returns null for youtube channel url`() {
        assertNull(converter.convert("https://www.youtube.com/@somechannel"))
    }

    @Test
    fun `returns null for malformed url`() {
        assertNull(converter.convert("not a url at all"))
    }

    @Test
    fun `returns null for empty string`() {
        assertNull(converter.convert(""))
    }

    @Test
    fun `returns null for http scheme on non-youtube host`() {
        assertNull(converter.convert("http://example.com/watch?v=abc"))
    }

    @Test
    fun `returns null for null-like input`() {
        assertNull(converter.convert("null"))
    }

    @Test
    fun `returns null for javascript url`() {
        assertNull(converter.convert("javascript:alert(1)"))
    }

    @Test
    fun `returns null for data url`() {
        assertNull(converter.convert("data:text/html,<h1>hello</h1>"))
    }

    // ── HTTP scheme (legacy links) ───────────────────────────────────

    @Test
    fun `convert http youtube watch url`() {
        val result = converter.convert("http://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals("https://my.invidious.org/watch?v=dQw4w9WgXcQ", result)
    }

    @Test
    fun `convert http youtu be`() {
        val result = converter.convert("http://youtu.be/dQw4w9WgXcQ")
        assertEquals("https://my.invidious.org/watch?v=dQw4w9WgXcQ", result)
    }

    @Test
    fun `convert http youtube without www`() {
        val result = converter.convert("http://youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals("https://my.invidious.org/watch?v=dQw4w9WgXcQ", result)
    }

    @Test
    fun `convert http mobile youtube`() {
        val result = converter.convert("http://m.youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals("https://my.invidious.org/watch?v=dQw4w9WgXcQ", result)
    }

    // ── Case insensitivity ───────────────────────────────────────────

    @Test
    fun `host is case insensitive`() {
        val result = converter.convert("https://WWW.YouTube.com/watch?v=dQw4w9WgXcQ")
        assertEquals("https://my.invidious.org/watch?v=dQw4w9WgXcQ", result)
    }

    @Test
    fun `youtu be host is case insensitive`() {
        val result = converter.convert("https://YOUTU.BE/dQw4w9WgXcQ")
        assertEquals("https://my.invidious.org/watch?v=dQw4w9WgXcQ", result)
    }

    // ── isInvidiousHost ──────────────────────────────────────────────

    @Test
    fun `isInvidiousHost returns true for matching host`() {
        assertTrue(converter.isInvidiousHost("https://my.invidious.org/watch?v=abc"))
    }

    @Test
    fun `isInvidiousHost returns false for other host`() {
        assertFalse(converter.isInvidiousHost("https://youtube.com/watch?v=abc"))
    }

    @Test
    fun `isInvidiousHost returns false for malformed url`() {
        assertFalse(converter.isInvidiousHost("not a url"))
    }

    @Test
    fun `isInvidiousHost is case insensitive`() {
        assertTrue(converter.isInvidiousHost("https://My.Invidious.Org/watch?v=abc"))
    }

    @Test
    fun `isInvidiousHost returns false for empty string`() {
        assertFalse(converter.isInvidiousHost(""))
    }

    @Test
    fun `isInvidiousHost returns false for null-like input`() {
        assertFalse(converter.isInvidiousHost("null"))
    }

    // ── homepageUrl ──────────────────────────────────────────────────

    @Test
    fun `homepageUrl returns https url with host`() {
        assertEquals("https://my.invidious.org", converter.homepageUrl())
    }

    // ── buildHomepageUrl (companion) ─────────────────────────────────

    @Test
    fun `buildHomepageUrl builds correct url`() {
        assertEquals("https://example.com", UrlConverter.buildHomepageUrl("example.com"))
    }

    @Test
    fun `buildHomepageUrl with subdomain`() {
        assertEquals(
            "https://invidious.example.com",
            UrlConverter.buildHomepageUrl("invidious.example.com")
        )
    }

    // ── Custom host ──────────────────────────────────────────────────

    @Test
    fun `convert with custom host`() {
        val customConverter = UrlConverter("inv.example.com")
        val result = customConverter.convert("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals("https://inv.example.com/watch?v=dQw4w9WgXcQ", result)
    }

    @Test
    fun `isInvidiousHost with custom host`() {
        val customConverter = UrlConverter("inv.example.com")
        assertTrue(customConverter.isInvidiousHost("https://inv.example.com/watch?v=abc"))
        assertFalse(customConverter.isInvidiousHost("https://my.invidious.org/watch?v=abc"))
    }

    @Test
    fun `homepageUrl with custom host`() {
        val customConverter = UrlConverter("inv.example.com")
        assertEquals("https://inv.example.com", customConverter.homepageUrl())
    }

    // ── youtu.be edge cases ──────────────────────────────────────────

    @Test
    fun `youtu.be with only slash returns null`() {
        assertNull(converter.convert("https://youtu.be/"))
    }

    @Test
    fun `youtu.be with no path returns null`() {
        assertNull(converter.convert("https://youtu.be"))
    }

    @Test
    fun `youtu.be with long video id`() {
        val result = converter.convert("https://youtu.be/dQw4w9WgXcQ123456")
        assertEquals("https://my.invidious.org/watch?v=dQw4w9WgXcQ123456", result)
    }
}