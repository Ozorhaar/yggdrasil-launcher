package com.ozorhaar.hreinsa

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkCleanerTest {
    private fun c(s: String) = LinkCleaner.clean(s).text

    @Test fun utmAndClickIds() {
        assertEquals("https://example.com/story?id=7#top", c("https://example.com/story?utm_source=x&id=7&fbclid=abc&utm_medium=y#top"))
        assertEquals("https://example.com/a", c("https://example.com/a?gclid=123"))
    }

    @Test fun siteSpecific() {
        assertEquals("https://youtu.be/dQw4w9WgXcQ?t=42", c("https://youtu.be/dQw4w9WgXcQ?si=AbCdEf&t=42"))
        assertEquals("https://open.spotify.com/track/123", c("https://open.spotify.com/track/123?si=99887766"))
        assertEquals("https://x.com/user/status/1", c("https://x.com/user/status/1?s=20&t=abc"))
        // "si" on an ordinary site may mean something: kept
        assertEquals("https://shop.example/p?si=3", c("https://shop.example/p?si=3"))
    }

    @Test fun wrappers() {
        assertEquals("https://example.com/page", c("https://www.google.com/url?q=https%3A%2F%2Fexample.com%2Fpage%3Futm_source%3Dg&sa=D"))
        assertEquals("https://news.example/x", c("https://l.facebook.com/l.php?u=https%3A%2F%2Fnews.example%2Fx%3Ffbclid%3D1&h=AT0"))
    }

    @Test fun amazon() {
        assertEquals("https://www.amazon.com/dp/B0ABCDEFGH",
            c("https://www.amazon.com/Some-Long-Name/dp/B0ABCDEFGH/ref=sr_1_3?crid=XYZ&keywords=thing&qid=1&sr=8-3"))
    }

    @Test fun textAroundLinksIsKept() {
        assertEquals("Look at this: https://example.com/a. Cool!", c("Look at this: https://example.com/a?utm_campaign=z. Cool!"))
        assertEquals("No links here", c("No links here"))
        assertEquals(2, LinkCleaner.clean("https://a.example/?utm_source=1&utm_medium=2").removed)
    }
}
