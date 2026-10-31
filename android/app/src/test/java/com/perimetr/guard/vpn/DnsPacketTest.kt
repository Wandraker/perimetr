package com.perimetr.guard.vpn

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DnsPacketTest {

    private fun dnsQuery(name: String, id: Int = 0x1234): ByteArray {
        val out = ArrayList<Byte>()
        out += (id shr 8).toByte()
        out += id.toByte()
        out += 0x01
        out += 0x00
        out += listOf<Byte>(0, 1, 0, 0, 0, 0, 0, 0)
        for (label in name.split('.')) {
            out += label.length.toByte()
            label.forEach { out += it.code.toByte() }
        }
        out += 0
        out += listOf<Byte>(0, 1, 0, 1)
        return out.toByteArray()
    }

    private fun ipUdp(dns: ByteArray, srcPort: Int = 40000, dstPort: Int = 53): ByteArray {
        val total = 28 + dns.size
        val p = ByteArray(total)
        p[0] = 0x45
        p[2] = (total shr 8).toByte()
        p[3] = total.toByte()
        p[8] = 64
        p[9] = 17
        byteArrayOf(10, 111, 0, 1).copyInto(p, 12)
        byteArrayOf(10, 111, 0, 2).copyInto(p, 16)
        p[20] = (srcPort shr 8).toByte()
        p[21] = srcPort.toByte()
        p[22] = (dstPort shr 8).toByte()
        p[23] = dstPort.toByte()
        val udpLen = 8 + dns.size
        p[24] = (udpLen shr 8).toByte()
        p[25] = udpLen.toByte()
        dns.copyInto(p, 28)
        return p
    }

    private fun u16(b: ByteArray, o: Int) = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

    private fun headerChecksumValid(p: ByteArray): Boolean {
        var sum = 0L
        for (i in 0 until 20 step 2) sum += u16(p, i)
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum == 0xFFFFL
    }

    @Test
    fun parsesQueryNameAndPorts() {
        val pkt = ipUdp(dnsQuery("img-sync-hk.top"))
        val q = DnsPacket.parse(pkt, pkt.size)
        assertNotNull(q)
        assertEquals("img-sync-hk.top", q!!.name)
        assertEquals(40000, q.srcPort)
        assertEquals(53, q.dstPort)
    }

    @Test
    fun lowercasesName() {
        val pkt = ipUdp(dnsQuery("Ads.Example.COM"))
        assertEquals("ads.example.com", DnsPacket.parse(pkt, pkt.size)!!.name)
    }

    @Test
    fun ignoresNonDnsAndNonUdp() {
        val notDns = ipUdp(dnsQuery("a.com"), dstPort = 443)
        assertNull(DnsPacket.parse(notDns, notDns.size))
        val tcp = ipUdp(dnsQuery("a.com")).also { it[9] = 6 }
        assertNull(DnsPacket.parse(tcp, tcp.size))
        val v6 = ipUdp(dnsQuery("a.com")).also { it[0] = 0x60 }
        assertNull(DnsPacket.parse(v6, v6.size))
        assertNull(DnsPacket.parse(ByteArray(10), 10))
    }

    @Test
    fun nxdomainKeepsIdAndQuestion() {
        val query = dnsQuery("doubleclick.net", id = 0xBEEF)
        val r = DnsPacket.nxdomain(query)
        assertEquals(0xBEEF, u16(r, 0))
        assertEquals(0x80, r[2].toInt() and 0x80)
        assertEquals(0x01, r[2].toInt() and 0x01)
        assertEquals(3, r[3].toInt() and 0x0F)
        assertEquals(1, u16(r, 4))
        assertEquals(0, u16(r, 6))
        assertEquals(query.size, r.size)
        assertArrayEquals(query.copyOfRange(12, query.size), r.copyOfRange(12, r.size))
    }

    @Test
    fun servfailAndIdMatch() {
        val query = dnsQuery("example.com", id = 0x0A0B)
        val r = DnsPacket.servfail(query)
        assertEquals(2, r[3].toInt() and 0x0F)
        assertEquals(0x0A0B, u16(r, 0))
        assert(DnsPacket.sameId(query, r))
        assert(!DnsPacket.sameId(query, DnsPacket.servfail(dnsQuery("example.com", id = 0x0A0C))))
    }

    @Test
    fun wrapSwapsAddressesAndHasValidChecksum() {
        val pkt = ipUdp(dnsQuery("example.com"))
        val q = DnsPacket.parse(pkt, pkt.size)!!
        val answer = byteArrayOf(0x12, 0x34, 0x81.toByte(), 0x80.toByte(), 0, 1, 0, 1, 0, 0, 0, 0)
        val out = DnsPacket.wrap(q, answer)
        assertEquals(28 + answer.size, out.size)
        assertEquals(out.size, u16(out, 2))
        assertArrayEquals(byteArrayOf(10, 111, 0, 2), out.copyOfRange(12, 16))
        assertArrayEquals(byteArrayOf(10, 111, 0, 1), out.copyOfRange(16, 20))
        assertEquals(53, u16(out, 20))
        assertEquals(40000, u16(out, 22))
        assertEquals(8 + answer.size, u16(out, 24))
        assertArrayEquals(answer, out.copyOfRange(28, out.size))
        assert(headerChecksumValid(out))
    }
}
