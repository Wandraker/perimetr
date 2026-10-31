package com.perimetr.guard.vpn

import java.net.HttpURLConnection
import java.net.URL

object Doh {
    private val endpoints = listOf(
        "https://1.1.1.1/dns-query",
        "https://dns.google/dns-query",
        "https://common.dot.dns.yandex.net/dns-query",
    )

    fun query(payload: ByteArray): ByteArray? {
        for (e in endpoints) {
            try {
                val c = URL(e).openConnection() as HttpURLConnection
                c.connectTimeout = 4000
                c.readTimeout = 4000
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/dns-message")
                c.setRequestProperty("Accept", "application/dns-message")
                c.outputStream.use { it.write(payload) }
                if (c.responseCode == 200) {
                    val body = c.inputStream.use { it.readBytes() }
                    if (body.size >= 12) return body
                } else {
                    c.errorStream?.close()
                }
            } catch (_: Exception) {
            }
        }
        return null
    }
}
