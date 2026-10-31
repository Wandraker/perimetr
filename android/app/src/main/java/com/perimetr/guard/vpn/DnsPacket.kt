package com.perimetr.guard.vpn

class DnsQuery(
    val src: ByteArray,
    val dst: ByteArray,
    val srcPort: Int,
    val dstPort: Int,
    val payload: ByteArray,
    val name: String,
)

object DnsPacket {
    private fun u16(b: ByteArray, o: Int): Int = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

    private fun put16(b: ByteArray, o: Int, v: Int) {
        b[o] = (v shr 8 and 0xFF).toByte()
        b[o + 1] = (v and 0xFF).toByte()
    }

    fun parse(b: ByteArray, n: Int): DnsQuery? {
        if (n < 28) return null
        if ((b[0].toInt() shr 4) and 0xF != 4) return null
        val ihl = (b[0].toInt() and 0xF) * 4
        if (ihl < 20) return null
        if (b[9].toInt() and 0xFF != 17) return null
        val total = minOf(u16(b, 2), n)
        if (total < ihl + 8) return null
        val srcPort = u16(b, ihl)
        val dstPort = u16(b, ihl + 2)
        if (dstPort != 53) return null
        val udpLen = u16(b, ihl + 4)
        val start = ihl + 8
        val end = minOf(total, ihl + udpLen)
        if (end - start < 12) return null
        val payload = b.copyOfRange(start, end)
        val name = qname(payload) ?: return null
        return DnsQuery(b.copyOfRange(12, 16), b.copyOfRange(16, 20), srcPort, dstPort, payload, name)
    }

    private fun qname(p: ByteArray): String? {
        if (u16(p, 4) < 1) return null
        var i = 12
        val sb = StringBuilder()
        while (i < p.size) {
            val len = p[i].toInt() and 0xFF
            if (len == 0) return sb.toString().lowercase()
            if (len and 0xC0 != 0) return null
            if (i + 1 + len > p.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (k in 1..len) sb.append((p[i + k].toInt() and 0xFF).toChar())
            i += len + 1
        }
        return null
    }

    private fun questionEnd(p: ByteArray): Int {
        var i = 12
        while (i < p.size) {
            val len = p[i].toInt() and 0xFF
            if (len == 0) return minOf(i + 5, p.size)
            i += len + 1
        }
        return p.size
    }

    fun nxdomain(q: ByteArray): ByteArray = error(q, 3)

    fun servfail(q: ByteArray): ByteArray = error(q, 2)

    fun sameId(q: ByteArray, r: ByteArray): Boolean = r.size >= 12 && q[0] == r[0] && q[1] == r[1]

    private fun error(q: ByteArray, rcode: Int): ByteArray {
        val r = q.copyOf(questionEnd(q))
        r[2] = ((q[2].toInt() and 0x79) or 0x80).toByte()
        r[3] = (0x80 or rcode).toByte()
        put16(r, 4, 1)
        put16(r, 6, 0)
        put16(r, 8, 0)
        put16(r, 10, 0)
        return r
    }

    fun wrap(q: DnsQuery, dns: ByteArray): ByteArray {
        val udpLen = 8 + dns.size
        val total = 20 + udpLen
        val p = ByteArray(total)
        p[0] = 0x45
        put16(p, 2, total)
        p[6] = 0x40
        p[8] = 64
        p[9] = 17
        System.arraycopy(q.dst, 0, p, 12, 4)
        System.arraycopy(q.src, 0, p, 16, 4)
        put16(p, 10, checksum(p, 20))
        put16(p, 20, q.dstPort)
        put16(p, 22, q.srcPort)
        put16(p, 24, udpLen)
        System.arraycopy(dns, 0, p, 28, dns.size)
        return p
    }

    private fun checksum(b: ByteArray, len: Int): Int {
        var sum = 0L
        var i = 0
        while (i < len) {
            sum += u16(b, i)
            i += 2
        }
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }
}
