package com.perimetr.guard.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class DomainStat(
    val pkg: String,
    val uid: Int,
    val domain: String,
    val count: Int,
    val last: Long,
    val blocked: Int,
)

class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "perimetr.db", null, 1) {

    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE dns (ts INTEGER NOT NULL, uid INTEGER NOT NULL, pkg TEXT NOT NULL, domain TEXT NOT NULL, blocked INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX dns_ts ON dns(ts)")
        db.execSQL("CREATE INDEX dns_pkg_domain ON dns(pkg, domain)")
        db.execSQL("CREATE TABLE rules (kind TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY(kind, value))")
        db.execSQL("CREATE TABLE domain_age (domain TEXT PRIMARY KEY, created INTEGER, checked INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun log(ts: Long, uid: Int, pkg: String, domain: String, blocked: Boolean) {
        val v = ContentValues()
        v.put("ts", ts)
        v.put("uid", uid)
        v.put("pkg", pkg)
        v.put("domain", domain)
        v.put("blocked", if (blocked) 1 else 0)
        writableDatabase.insert("dns", null, v)
    }

    fun seen(pkg: String, domain: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM dns WHERE pkg=? AND domain=? LIMIT 1", arrayOf(pkg, domain))
            .use { it.moveToFirst() }

    fun firstSeen(pkg: String): Long? =
        readableDatabase.rawQuery("SELECT MIN(ts) FROM dns WHERE pkg=?", arrayOf(pkg)).use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
        }

    fun stats(from: Long, to: Long): List<DomainStat> =
        readableDatabase.rawQuery(
            "SELECT pkg, MAX(uid), domain, COUNT(*), MAX(ts), SUM(blocked) FROM dns WHERE ts>=? AND ts<? GROUP BY pkg, domain",
            arrayOf(from.toString(), to.toString()),
        ).use { c ->
            val out = ArrayList<DomainStat>()
            while (c.moveToNext()) {
                out.add(DomainStat(c.getString(0), c.getInt(1), c.getString(2), c.getInt(3), c.getLong(4), c.getInt(5)))
            }
            out
        }

    fun rules(): List<Pair<String, String>> =
        readableDatabase.rawQuery("SELECT kind, value FROM rules", null).use { c ->
            val out = ArrayList<Pair<String, String>>()
            while (c.moveToNext()) out.add(c.getString(0) to c.getString(1))
            out
        }

    fun setRule(kind: String, value: String, on: Boolean) {
        if (on) {
            val v = ContentValues()
            v.put("kind", kind)
            v.put("value", value)
            writableDatabase.insertWithOnConflict("rules", null, v, SQLiteDatabase.CONFLICT_IGNORE)
        } else {
            writableDatabase.delete("rules", "kind=? AND value=?", arrayOf(kind, value))
        }
    }

    fun ages(): Map<String, Long?> =
        readableDatabase.rawQuery("SELECT domain, created FROM domain_age", null).use { c ->
            val out = HashMap<String, Long?>()
            while (c.moveToNext()) out[c.getString(0)] = if (c.isNull(1)) null else c.getLong(1)
            out
        }

    fun saveAge(domain: String, created: Long?) {
        val v = ContentValues()
        v.put("domain", domain)
        if (created == null) v.putNull("created") else v.put("created", created)
        v.put("checked", System.currentTimeMillis())
        writableDatabase.insertWithOnConflict("domain_age", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun prune(before: Long) {
        writableDatabase.delete("dns", "ts<?", arrayOf(before.toString()))
    }

    companion object {
        @Volatile
        private var instance: Db? = null

        fun get(ctx: Context): Db =
            instance ?: synchronized(this) {
                instance ?: Db(ctx.applicationContext).also { instance = it }
            }
    }
}
