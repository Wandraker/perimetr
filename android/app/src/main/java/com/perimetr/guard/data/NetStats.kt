package com.perimetr.guard.data

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Process
import java.util.Calendar

object NetStats {
    fun hasAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            ctx.checkSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    @Suppress("DEPRECATION")
    fun perUid(ctx: Context, start: Long, end: Long): Map<Int, LongArray> {
        val nsm = ctx.getSystemService(NetworkStatsManager::class.java) ?: return emptyMap()
        val out = HashMap<Int, LongArray>()
        for (type in intArrayOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) {
            try {
                val stats = nsm.querySummary(type, null, start, end)
                val b = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(b)
                    val a = out.getOrPut(b.uid) { LongArray(2) }
                    a[0] += b.txBytes
                    a[1] += b.rxBytes
                }
                stats.close()
            } catch (_: Exception) {
            }
        }
        return out
    }
}

object Night {
    const val HOUR = 3_600_000L
    const val DAY = 24 * HOUR

    fun window(now: Long): Pair<Long, Long> {
        val c = Calendar.getInstance()
        c.timeInMillis = now
        val hour = c.get(Calendar.HOUR_OF_DAY)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        val start = c.timeInMillis
        return if (hour < 6) start to now else start to start + 6 * HOUR
    }

    fun isNight(ts: Long): Boolean {
        val c = Calendar.getInstance()
        c.timeInMillis = ts
        return c.get(Calendar.HOUR_OF_DAY) < 6
    }
}
