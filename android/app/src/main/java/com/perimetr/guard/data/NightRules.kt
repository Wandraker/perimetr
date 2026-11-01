package com.perimetr.guard.data

object NightRules {
    const val MB = 1024L * 1024L

    enum class Verdict { OK, WARN, CRIT }

    fun verdict(sent: Long, seesAllPhotos: Boolean, youngDomain: Boolean, trusted: Boolean): Verdict = when {
        trusted -> Verdict.OK
        seesAllPhotos && sent >= 50 * MB -> Verdict.CRIT
        youngDomain && sent >= MB -> Verdict.CRIT
        youngDomain -> Verdict.WARN
        seesAllPhotos && sent >= 20 * MB -> Verdict.WARN
        sent >= 100 * MB -> Verdict.WARN
        else -> Verdict.OK
    }

    private val quiet = setOf(
        "googleusercontent.com",
        "gstatic.com",
        "ggpht.com",
        "ytimg.com",
        "googlevideo.com",
        "gvt1.com",
        "gvt2.com",
        "yastatic.net",
        "avatars.mds.yandex.net",
        "userapi.com",
        "vkuser.net",
        "fbcdn.net",
        "cdninstagram.com",
        "twimg.com",
        "mzstatic.com",
    )

    fun quietDomain(domain: String): Boolean {
        var d = domain
        while (true) {
            if (d in quiet) return true
            val i = d.indexOf('.')
            if (i < 0) return false
            d = d.substring(i + 1)
        }
    }
}
