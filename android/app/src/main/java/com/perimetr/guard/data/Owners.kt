package com.perimetr.guard.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process

object Owners {
    const val UNKNOWN = "unknown"
    private const val SYSTEM_PREFIX = "system:"

    fun pkgFor(pm: PackageManager, uid: Int): String {
        if (uid < 0) return UNKNOWN
        if (uid < Process.FIRST_APPLICATION_UID) return SYSTEM_PREFIX + uid
        return pm.getPackagesForUid(uid)?.firstOrNull() ?: "uid:$uid"
    }

    fun isApp(pkg: String): Boolean = pkg != UNKNOWN && !pkg.startsWith(SYSTEM_PREFIX) && !pkg.startsWith("uid:")
}

class Labels(ctx: Context) {
    private val pm = ctx.packageManager
    private val cache = HashMap<String, String>()

    @Suppress("DEPRECATION")
    fun of(pkg: String): String = cache.getOrPut(pkg) {
        when {
            pkg == Owners.UNKNOWN -> "Неизвестное приложение"
            pkg.startsWith("system:") -> "Система Android"
            pkg.startsWith("uid:") -> "Приложение #" + pkg.removePrefix("uid:")
            else -> try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) {
                pkg
            }
        }
    }

    fun pkgForUid(uid: Int): String? = pm.getPackagesForUid(uid)?.firstOrNull()
}
