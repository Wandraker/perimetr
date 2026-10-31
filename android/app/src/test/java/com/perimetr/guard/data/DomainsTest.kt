package com.perimetr.guard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainsTest {

    @Test
    fun registrableDomain() {
        assertEquals("example.com", Domains.registrable("a.b.example.com"))
        assertEquals("bbc.co.uk", Domains.registrable("www.bbc.co.uk"))
        assertEquals("img-sync-hk.top", Domains.registrable("img-sync-hk.top"))
    }

    @Test
    fun trackerSuffixMatch() {
        assertTrue(Trackers.isTracker("doubleclick.net"))
        assertTrue(Trackers.isTracker("ad.doubleclick.net"))
        assertFalse(Trackers.isTracker("notdoubleclick.net"))
        assertFalse(Trackers.isTracker("api.weather.com"))
    }

    @Test
    fun lookupable() {
        assertTrue(Domains.lookupable("example.com"))
        assertFalse(Domains.lookupable("1.0.0.10.in-addr.arpa"))
        assertFalse(Domains.lookupable("printer.local"))
    }
}
