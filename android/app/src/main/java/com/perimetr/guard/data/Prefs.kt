package com.perimetr.guard.data

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        if (!this::sp.isInitialized) {
            sp = ctx.applicationContext.getSharedPreferences("perimetr", Context.MODE_PRIVATE)
        }
    }

    var secure: Boolean
        get() = sp.getBoolean("secure", false)
        set(v) = sp.edit().putBoolean("secure", v).apply()

    var autoBlock: Boolean
        get() = sp.getBoolean("auto_block", true)
        set(v) = sp.edit().putBoolean("auto_block", v).apply()

    var nightAlerts: Boolean
        get() = sp.getBoolean("night_alerts", true)
        set(v) = sp.edit().putBoolean("night_alerts", v).apply()

    var vpnWanted: Boolean
        get() = sp.getBoolean("vpn_wanted", false)
        set(v) = sp.edit().putBoolean("vpn_wanted", v).apply()

    var batteryAck: Boolean
        get() = sp.getBoolean("battery_ack", false)
        set(v) = sp.edit().putBoolean("battery_ack", v).apply()

    var lastKill: Long
        get() = sp.getLong("last_kill", 0L)
        set(v) = sp.edit().putLong("last_kill", v).apply()

    var lastFailOpen: Long
        get() = sp.getLong("last_fail_open", 0L)
        set(v) = sp.edit().putLong("last_fail_open", v).apply()

    var proxyMode: Boolean
        get() = sp.getBoolean("proxy_mode", false)
        set(v) = sp.edit().putBoolean("proxy_mode", v).apply()

    var proxyPort: Int
        get() = sp.getInt("proxy_port", 1080)
        set(v) = sp.edit().putInt("proxy_port", v).apply()

    var proxyPkg: String?
        get() = sp.getString("proxy_pkg", null)
        set(v) = sp.edit().putString("proxy_pkg", v).apply()

    var knownWatchers: Set<String>?
        get() = if (sp.contains("watchers")) sp.getStringSet("watchers", emptySet())?.toSet() else null
        set(v) = sp.edit().putStringSet("watchers", HashSet(v ?: emptySet())).apply()
}
