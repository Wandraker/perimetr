package com.perimetr.guard.data

object Rules {
    const val DOMAIN = "domain"
    const val APP = "app"
    const val TRUST = "trust"

    @Volatile
    var blockedDomains: Set<String> = emptySet()
        private set

    @Volatile
    var blockedApps: Set<String> = emptySet()
        private set

    @Volatile
    var trustedApps: Set<String> = emptySet()
        private set

    fun load(db: Db) {
        val all = db.rules()
        blockedDomains = all.filter { it.first == DOMAIN }.map { it.second }.toSet()
        blockedApps = all.filter { it.first == APP }.map { it.second }.toSet()
        trustedApps = all.filter { it.first == TRUST }.map { it.second }.toSet()
    }

    fun domainBlocked(domain: String): Boolean {
        val set = blockedDomains
        if (set.isEmpty()) return false
        var d = domain
        while (true) {
            if (d in set) return true
            val i = d.indexOf('.')
            if (i < 0) return false
            d = d.substring(i + 1)
        }
    }

    fun shouldBlock(pkg: String, domain: String): Boolean {
        if (pkg in blockedApps) return true
        if (domainBlocked(domain)) return true
        return Prefs.autoBlock && Trackers.isTracker(domain)
    }
}
