package com.perimetr.guard

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.perimetr.guard.data.Access
import com.perimetr.guard.data.AppAccess
import com.perimetr.guard.data.Db
import com.perimetr.guard.data.DomainStat
import com.perimetr.guard.data.Domains
import com.perimetr.guard.data.Labels
import com.perimetr.guard.data.NetStats
import com.perimetr.guard.data.Night
import com.perimetr.guard.data.Owners
import com.perimetr.guard.data.PermAudit
import com.perimetr.guard.data.Prefs
import com.perimetr.guard.data.ProxyApps
import com.perimetr.guard.data.Rdap
import com.perimetr.guard.data.Rules
import com.perimetr.guard.data.Trackers
import com.perimetr.guard.vpn.GuardVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class Level { OK, WARN, CRIT }

data class DomainRow(
    val domain: String,
    val count: Int,
    val last: Long,
    val blockedHits: Int,
    val tracker: Boolean,
    val ageDays: Long?,
    val ruleBlocked: Boolean,
) {
    val young: Boolean get() = ageDays != null && ageDays < 90
}

data class AppRow(
    val pkg: String,
    val label: String,
    val domains: List<DomainRow>,
    val total: Int,
    val last: Long,
    val appBlocked: Boolean,
    val level: Level,
)

data class NightRow(
    val pkg: String,
    val label: String,
    val tx: Long,
    val photos: Boolean,
    val domains: List<DomainRow>,
    val level: Level,
    val trusted: Boolean,
    val reason: String,
)

data class AgeCheck(val done: Int, val total: Int)

data class UiState(
    val loaded: Boolean = false,
    val vpnOn: Boolean = false,
    val usageAccess: Boolean = false,
    val notifications: Boolean = true,
    val batteryFree: Boolean = true,
    val proxyMode: Boolean = false,
    val proxyPort: Int = 1080,
    val proxyActive: Boolean = false,
    val proxyError: String? = null,
    val proxyApps: List<String> = emptyList(),
    val killedAt: Long = 0,
    val failOpenAt: Long = 0,
    val bgDataRestricted: Boolean = false,
    val secure: Boolean = true,
    val autoBlock: Boolean = true,
    val nightAlerts: Boolean = true,
    val apps: List<AppRow> = emptyList(),
    val night: List<NightRow> = emptyList(),
    val nightFrom: Long = 0,
    val nightTo: Long = 0,
    val audit: List<AppAccess> = emptyList(),
    val screenWatchers: List<AppAccess> = emptyList(),
    val blockedToday: Int = 0,
    val period: Long = Night.DAY,
    val blockedInPeriod: Int = 0,
    val domainRules: List<String> = emptyList(),
    val appRules: List<Pair<String, String>> = emptyList(),
    val score: Int = 100,
    val headline: String = "",
    val ageCheck: AgeCheck? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private val mutex = Mutex()
    private var auditCache: List<AppAccess>? = null
    @Volatile
    private var period: Long = Night.DAY

    fun setPeriod(p: Long) {
        period = p
        _state.update { it.copy(period = p) }
        refreshLight()
    }

    init {
        Prefs.init(app)
        viewModelScope.launch {
            GuardVpnService.proxyActive.collect { on -> _state.update { it.copy(proxyActive = on) } }
        }
        viewModelScope.launch {
            GuardVpnService.proxyError.collect { e -> _state.update { it.copy(proxyError = e) } }
        }
        viewModelScope.launch {
            GuardVpnService.running.collect { on ->
                _state.update { it.copy(vpnOn = on) }
                refreshLight()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { compute(true) }
    }

    fun refreshLight() {
        viewModelScope.launch(Dispatchers.IO) { compute(false) }
    }

    fun setProxyMode(on: Boolean) {
        Prefs.proxyMode = on
        _state.update { it.copy(proxyMode = on) }
        restartVpn()
    }

    fun setProxyPort(port: Int) {
        if (port !in 1..65535 || port == Prefs.proxyPort) return
        Prefs.proxyPort = port
        _state.update { it.copy(proxyPort = port) }
        if (Prefs.proxyMode) restartVpn()
    }

    private fun restartVpn() {
        if (!GuardVpnService.running.value) return
        val app = getApplication<Application>()
        try {
            app.startService(Intent(app, GuardVpnService::class.java).setAction(GuardVpnService.ACTION_RESTART))
        } catch (_: Exception) {
        }
    }

    fun ackBattery() {
        Prefs.batteryAck = true
        refreshLight()
    }

    fun clearFailOpen() {
        Prefs.lastFailOpen = 0L
        refreshLight()
    }

    fun clearKill() {
        Prefs.lastKill = 0L
        refreshLight()
    }

    fun setAutoBlock(on: Boolean) {
        Prefs.autoBlock = on
        refreshLight()
    }

    fun setNightAlerts(on: Boolean) {
        Prefs.nightAlerts = on
        refreshLight()
    }

    fun toggleDomain(domain: String) = rule(Rules.DOMAIN, domain, domain !in Rules.blockedDomains)

    fun toggleApp(pkg: String) = rule(Rules.APP, pkg, pkg !in Rules.blockedApps)

    fun toggleTrust(pkg: String) = rule(Rules.TRUST, pkg, pkg !in Rules.trustedApps)

    private fun rule(kind: String, value: String, on: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = Db.get(getApplication())
            db.setRule(kind, value, on)
            Rules.load(db)
            compute(false)
        }
    }

    fun checkAges() {
        if (_state.value.ageCheck != null) return
        _state.update { it.copy(ageCheck = AgeCheck(0, 0)) }
        viewModelScope.launch(Dispatchers.IO) {
            val db = Db.get(getApplication())
            val known = db.ages()
            val s = _state.value
            val todo = (s.apps.flatMap { it.domains } + s.night.flatMap { it.domains })
                .asSequence()
                .map { Domains.registrable(it.domain) }
                .filter { Domains.lookupable(it) && !known.containsKey(it) }
                .distinct()
                .take(40)
                .toList()
            todo.forEachIndexed { i, d ->
                _state.update { it.copy(ageCheck = AgeCheck(i, todo.size)) }
                try {
                    db.saveAge(d, Rdap.created(d))
                } catch (_: Exception) {
                }
            }
            _state.update { it.copy(ageCheck = null) }
            compute(false)
        }
    }

    private suspend fun compute(fullAudit: Boolean) = mutex.withLock {
        val app = getApplication<Application>()
        val db = Db.get(app)
        Rules.load(db)
        val labels = Labels(app)
        val now = System.currentTimeMillis()

        val audit = auditCache.takeUnless { fullAudit } ?: PermAudit.scan(app).also { auditCache = it }
        val photoPkgs = audit.filter { it.seesAllPhotos }.map { it.pkg }.toSet()
        val ages = db.ages()

        fun row(st: DomainStat): DomainRow {
            val created = ages[Domains.registrable(st.domain)]
            return DomainRow(
                domain = st.domain,
                count = st.count,
                last = st.last,
                blockedHits = st.blocked,
                tracker = Trackers.isTracker(st.domain),
                ageDays = created?.let { (now - it) / Night.DAY },
                ruleBlocked = Rules.domainBlocked(st.domain),
            )
        }

        val day = db.stats(now - Night.DAY, now + 1)
        val span = period
        val ranged = if (span == Night.DAY) day else db.stats(now - span, now + 1)
        val apps = ranged.groupBy { it.pkg }.map { (pkg, list) ->
            val rows = list.map(::row).sortedWith(
                compareByDescending<DomainRow> { it.young }.thenByDescending { it.tracker }.thenByDescending { it.last },
            )
            val level = when {
                rows.any { it.young } -> Level.CRIT
                rows.any { it.tracker } -> Level.WARN
                else -> Level.OK
            }
            AppRow(pkg, labels.of(pkg), rows, list.sumOf { it.count }, list.maxOf { it.last }, pkg in Rules.blockedApps, level)
        }.sortedWith(compareByDescending<AppRow> { it.level.ordinal }.thenByDescending { it.last })

        val (from, to) = Night.window(now)
        val usage = NetStats.hasAccess(app)
        val traffic = if (usage) NetStats.perUid(app, from, to) else emptyMap()
        val nightDns = db.stats(from, to + 1).groupBy { it.pkg }
        val tx = HashMap<String, Long>()
        for ((uid, t) in traffic) {
            if (uid < Process.FIRST_APPLICATION_UID || uid == Process.myUid()) continue
            val pkg = labels.pkgForUid(uid) ?: continue
            tx[pkg] = (tx[pkg] ?: 0L) + t[0]
        }
        val candidates = (tx.filterValues { it >= 256 * 1024 }.keys + nightDns.keys.filter { Owners.isApp(it) }).distinct()
        val night = candidates.map { pkg ->
            val sent = tx[pkg] ?: 0L
            val rows = nightDns[pkg].orEmpty().map(::row).sortedByDescending { it.young }
            val photos = pkg in photoPkgs
            val young = rows.filter { it.young }
            val trusted = pkg in Rules.trustedApps
            val level = when {
                trusted -> Level.OK
                photos && sent >= 5 * MB -> Level.CRIT
                young.isNotEmpty() && sent >= MB -> Level.CRIT
                sent >= 20 * MB || young.isNotEmpty() || (photos && sent >= MB) -> Level.WARN
                else -> Level.OK
            }
            NightRow(pkg, labels.of(pkg), sent, photos, rows, level, trusted, reason(sent, photos, young, rows, trusted))
        }.filter { it.level != Level.OK || it.tx >= 5 * MB || it.trusted }
            .sortedWith(compareByDescending<NightRow> { it.level.ordinal }.thenByDescending { it.tx })

        val crit = night.count { it.level == Level.CRIT }
        val warn = night.count { it.level == Level.WARN }
        val screenWatchers = audit.filter { Access.ACCESSIBILITY in it.access }
        val untrustedWatchers = screenWatchers.filter { it.pkg !in Rules.trustedApps }
        val youngApps = apps.count { a -> a.pkg !in Rules.trustedApps && a.domains.any { it.young } }
        var score = 100 - 30 * crit - 10 * warn - minOf(30, 10 * youngApps) - minOf(15, 3 * photoPkgs.size) - minOf(20, 10 * untrustedWatchers.size)
        val vpnOn = GuardVpnService.running.value
        if (!vpnOn) score -= 10
        if (!usage) score -= 5
        score = score.coerceIn(0, 100)

        val headline = when {
            crit > 0 -> night.first { it.level == Level.CRIT }.let { "${it.label}: ${it.reason}" }
            untrustedWatchers.isNotEmpty() -> "${untrustedWatchers.first().label} видит и управляет экраном через «Специальные возможности». Проверь, ставил ли ты это сам."
            !vpnOn -> "Монитор выключен. Сливы через сеть сейчас не видны."
            youngApps > 0 -> "Есть приложения, которые ходят на домены моложе 90 дней."
            warn > 0 -> "Ночью была активность, которую стоит проверить."
            else -> "Подозрительных выгрузок не найдено."
        }

        val notifications = Build.VERSION.SDK_INT < 33 ||
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        val bgRestricted = try {
            app.getSystemService(ConnectivityManager::class.java)?.restrictBackgroundStatus ==
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        } catch (_: Exception) {
            false
        }
        val batteryFree = Prefs.batteryAck ||
            (app.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(app.packageName) ?: true)

        _state.update {
            it.copy(
                loaded = true,
                batteryFree = batteryFree,
                proxyMode = Prefs.proxyMode,
                proxyPort = Prefs.proxyPort,
                proxyApps = ProxyApps.installed(app.packageManager).map { p -> labels.of(p) },
                killedAt = Prefs.lastKill.takeIf { k -> now - k < Night.DAY } ?: 0L,
                failOpenAt = Prefs.lastFailOpen.takeIf { k -> now - k < Night.DAY } ?: 0L,
                bgDataRestricted = bgRestricted,
                vpnOn = vpnOn,
                usageAccess = usage,
                notifications = notifications,
                secure = Prefs.secure,
                autoBlock = Prefs.autoBlock,
                nightAlerts = Prefs.nightAlerts,
                apps = apps,
                night = night,
                nightFrom = from,
                nightTo = to,
                audit = audit,
                screenWatchers = screenWatchers,
                blockedToday = day.sumOf { d -> d.blocked },
                period = span,
                blockedInPeriod = ranged.sumOf { d -> d.blocked },
                domainRules = Rules.blockedDomains.sorted(),
                appRules = Rules.blockedApps.map { p -> p to labels.of(p) }.sortedBy { p -> p.second.lowercase() },
                score = score,
                headline = headline,
            )
        }
    }

    private fun reason(sent: Long, photos: Boolean, young: List<DomainRow>, rows: List<DomainRow>, trusted: Boolean): String {
        val parts = ArrayList<String>()
        if (sent > 0) parts.add("отправило ${Fmt.bytes(sent)}")
        if (photos) parts.add("видит все фото, включая скрытые")
        if (young.isNotEmpty()) {
            val y = young.first()
            parts.add("ходит на ${y.domain}, домену ${y.ageDays} дн.")
        } else if (rows.isNotEmpty()) {
            parts.add("серверов: ${rows.size}")
        }
        if (trusted) parts.add("ты отметил его как доверенное")
        return parts.joinToString(", ").replaceFirstChar { it.uppercase() } + "."
    }

    companion object {
        const val MB = 1024L * 1024L
    }
}
