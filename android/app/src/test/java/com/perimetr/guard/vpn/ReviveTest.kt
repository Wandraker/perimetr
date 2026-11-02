package com.perimetr.guard.vpn

import org.junit.Assert.assertEquals
import org.junit.Test

class ReviveTest {

    @Test
    fun firstRetryComesQuickly() {
        assertEquals(5L, Revive.delayMinutes(0))
    }

    @Test
    fun retriesBackOff() {
        assertEquals(listOf(5L, 10L, 20L, 40L), (0..3).map { Revive.delayMinutes(it) })
    }

    @Test
    fun retryNeverWaitsMoreThanAnHour() {
        assertEquals(60L, Revive.delayMinutes(4))
        assertEquals(60L, Revive.delayMinutes(50))
        assertEquals(5L, Revive.delayMinutes(-1))
    }
}
