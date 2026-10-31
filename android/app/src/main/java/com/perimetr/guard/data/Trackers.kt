package com.perimetr.guard.data

object Trackers {
    private val list = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "google-analytics.com",
        "app-measurement.com",
        "adservice.google.com",
        "an.facebook.com",
        "appsflyer.com",
        "appsflyersdk.com",
        "adjust.com",
        "adjust.net.in",
        "branch.io",
        "kochava.com",
        "applovin.com",
        "applvn.com",
        "unityads.unity3d.com",
        "ironsrc.com",
        "ironsrc.mobi",
        "supersonicads.com",
        "vungle.com",
        "chartboost.com",
        "inmobi.com",
        "startappservice.com",
        "adcolony.com",
        "tapjoy.com",
        "fyber.com",
        "mintegral.com",
        "rayjump.com",
        "pangle.io",
        "pangleglobal.com",
        "amplitude.com",
        "mixpanel.com",
        "flurry.com",
        "scorecardresearch.com",
        "adnxs.com",
        "criteo.com",
        "criteo.net",
        "taboola.com",
        "outbrain.com",
        "moatads.com",
        "demdex.net",
        "omtrdc.net",
        "appmetrica.yandex.net",
        "appmetrica.yandex.com",
        "mc.yandex.ru",
        "an.yandex.ru",
        "adfox.ru",
        "ad.mail.ru",
        "r.mradx.net",
        "top-fwz1.mail.ru",
        "mytarget.ru",
        "bidswitch.net",
        "smaato.net",
        "rubiconproject.com",
        "pubmatic.com",
        "openx.net",
        "adform.net",
        "yieldmo.com",
        "bugsnag.com",
        "yandexadexchange.net",
        "adsdk.yandex.ru",
        "ads.adfox.ru",
        "ads.vk.com",
        "vk-analytics.ru",
        "tracker-api.vk-analytics.ru",
        "mradx.net",
    )

    val size: Int get() = list.size

    fun isTracker(domain: String): Boolean {
        var d = domain
        while (true) {
            if (d in list) return true
            val i = d.indexOf('.')
            if (i < 0) return false
            d = d.substring(i + 1)
        }
    }
}

object Domains {
    private val secondLevel = setOf(
        "co.uk", "org.uk", "ac.uk", "gov.uk", "com.au", "net.au", "org.au", "co.jp", "ne.jp", "or.jp",
        "com.br", "com.cn", "net.cn", "org.cn", "com.hk", "com.tw", "co.kr", "co.in", "co.id", "com.tr",
        "com.ua", "com.ru", "msk.ru", "spb.ru", "co.za", "com.mx", "com.sg", "com.my", "com.kz", "org.kz",
    )

    fun registrable(domain: String): String {
        val parts = domain.trimEnd('.').split('.')
        if (parts.size <= 2) return domain
        val last2 = parts.takeLast(2).joinToString(".")
        return if (last2 in secondLevel) parts.takeLast(3).joinToString(".") else last2
    }

    fun lookupable(domain: String): Boolean =
        domain.contains('.') &&
            !domain.endsWith(".arpa") &&
            !domain.endsWith(".local") &&
            !domain.endsWith(".lan") &&
            !domain.endsWith(".home") &&
            domain.any { it.isLetter() }
}
