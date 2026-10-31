package com.perimetr.guard

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Fmt {
    private val ru = Locale.forLanguageTag("ru")

    fun bytes(b: Long): String = when {
        b >= 1L shl 30 -> String.format(ru, "%.1f ГБ", b / 1073741824.0)
        b >= 1L shl 20 -> String.format(ru, "%.1f МБ", b / 1048576.0)
        b >= 1024 -> "${b / 1024} КБ"
        else -> "$b Б"
    }

    fun time(ts: Long): String = SimpleDateFormat("HH:mm", ru).format(Date(ts))

    fun ago(ts: Long, now: Long = System.currentTimeMillis()): String {
        val m = (now - ts) / 60_000
        return when {
            m < 1 -> "только что"
            m < 60 -> "$m мин назад"
            m < 24 * 60 -> "${m / 60} ч назад"
            else -> SimpleDateFormat("d MMM, HH:mm", ru).format(Date(ts))
        }
    }
}
