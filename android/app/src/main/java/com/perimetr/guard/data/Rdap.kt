package com.perimetr.guard.data

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.OffsetDateTime

object Rdap {
    @Throws(IOException::class)
    fun created(domain: String): Long? {
        var url = URL("https://rdap.org/domain/$domain")
        repeat(4) {
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Accept", "application/rdap+json, application/json")
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: return null
                    url = URL(url, loc)
                    return@repeat
                }
                if (code != 200) return null
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                return parse(text)
            } finally {
                conn.disconnect()
            }
        }
        return null
    }

    private fun parse(text: String): Long? {
        val events = JSONObject(text).optJSONArray("events") ?: return null
        for (i in 0 until events.length()) {
            val e = events.optJSONObject(i) ?: continue
            if (e.optString("eventAction") != "registration") continue
            val date = e.optString("eventDate")
            return try {
                OffsetDateTime.parse(date).toInstant().toEpochMilli()
            } catch (_: Exception) {
                try {
                    Instant.parse(date).toEpochMilli()
                } catch (_: Exception) {
                    null
                }
            }
        }
        return null
    }
}
