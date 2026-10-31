package com.perimetr.guard.data

import android.content.pm.PackageManager

object ProxyApps {
    val known = listOf(
        "io.github.romanvht.byedpi",
        "io.github.dovecoteescapee.byedpi",
    )

    @Suppress("DEPRECATION")
    fun installed(pm: PackageManager): List<String> {
        val extra = Prefs.proxyPkg
        return (known + listOfNotNull(extra)).distinct().filter { pkg ->
            try {
                pm.getApplicationInfo(pkg, 0)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
