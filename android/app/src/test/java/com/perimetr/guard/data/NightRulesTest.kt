package com.perimetr.guard.data

import com.perimetr.guard.data.NightRules.MB
import com.perimetr.guard.data.NightRules.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightRulesTest {

    @Test
    fun messengerWithFewMegabytesIsQuiet() {
        assertEquals(Verdict.OK, NightRules.verdict(5 * MB, seesAllPhotos = true, youngDomain = false, trusted = false))
    }

    @Test
    fun largePhotoUploadIsCritical() {
        assertEquals(Verdict.CRIT, NightRules.verdict(312 * MB, seesAllPhotos = true, youngDomain = false, trusted = false))
        assertEquals(Verdict.WARN, NightRules.verdict(25 * MB, seesAllPhotos = true, youngDomain = false, trusted = false))
    }

    @Test
    fun youngDomainAlwaysFlagged() {
        assertEquals(Verdict.WARN, NightRules.verdict(0, seesAllPhotos = false, youngDomain = true, trusted = false))
        assertEquals(Verdict.CRIT, NightRules.verdict(2 * MB, seesAllPhotos = false, youngDomain = true, trusted = false))
    }

    @Test
    fun trustedIsOk() {
        assertEquals(Verdict.OK, NightRules.verdict(500 * MB, seesAllPhotos = true, youngDomain = true, trusted = true))
    }

    @Test
    fun quietCdnDomains() {
        assertTrue(NightRules.quietDomain("lh3.googleusercontent.com"))
        assertTrue(NightRules.quietDomain("fonts.gstatic.com"))
        assertFalse(NightRules.quietDomain("img-sync-hk.top"))
        assertFalse(NightRules.quietDomain("evilgstatic.com"))
    }
}
