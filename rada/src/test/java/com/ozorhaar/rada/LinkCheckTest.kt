package com.ozorhaar.rada

import com.ozorhaar.rada.LinkCheck.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkCheckTest {
    private fun lv(s: String) = LinkCheck.check(s)!!.level

    @Test fun realSitesAreFine() {
        assertEquals(Level.OK, lv("https://www.chase.com/personal"))
        assertEquals(Level.OK, lv("https://secure.paypal.com/signin"))
        assertEquals(Level.OK, lv("https://www.ohioturnpike.org/pay"))
        assertEquals(Level.OK, lv("https://groups.io/g/test"))
        assertEquals("bbc.co.uk", LinkCheck.check("https://news.bbc.co.uk/x")!!.site)
    }

    @Test fun scamShapes() {
        assertEquals(Level.DANGER, lv("https://chase-secure-alert.co/login"))
        assertEquals(Level.DANGER, lv("https://ohioturnpike-pay.top/x"))
        assertEquals(Level.DANGER, lv("https://usps.redeliver-help.com/track"))
        assertEquals(Level.DANGER, lv("http://192.168.4.20/pay"))
        assertEquals(Level.DANGER, lv("https://xn--pypal-4ve.com/"))
        assertEquals(Level.DANGER, lv("https://www.paypal.com@evil.example/"))
        assertEquals(Level.CAUTION, lv("https://bit.ly/3abc"))
        assertEquals(Level.CAUTION, lv("https://parking-menu.xyz/"))
        assertEquals(Level.CAUTION, lv("http://example.com/"))
    }

    @Test fun notLinks() {
        assertNull(LinkCheck.check("WIFI:S:home;T:WPA;P:secret;;"))
        assertNull(LinkCheck.check("hello there"))
    }
}
