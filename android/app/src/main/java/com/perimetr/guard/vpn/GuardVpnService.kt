package com.perimetr.guard.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.system.Os
import android.system.OsConstants
import com.perimetr.guard.MainActivity
import com.perimetr.guard.R
import com.perimetr.guard.data.Db
import com.perimetr.guard.data.Labels
import com.perimetr.guard.data.Night
import com.perimetr.guard.data.NightRules
import com.perimetr.guard.data.Owners
import com.perimetr.guard.data.Prefs
import com.perimetr.guard.data.ProxyApps
import com.perimetr.guard.data.Rules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class GuardVpnService : VpnService() {

    private val life = Any()
    private var tun: ParcelFileDescriptor? = null
    private var reader: Thread? = null
    private var out: FileOutputStream? = null
    private val writeLock = Any()
    private lateinit var pool: ExecutorService
    private lateinit var watchdog: ScheduledExecutorService
    private val lastLogged = ConcurrentHashMap<String, Long>()
    private val alerted = ConcurrentHashMap<String, Boolean>()

    private var relayLocal: ParcelFileDescriptor? = null
    private var relayRemote: ParcelFileDescriptor? = null
    private var relayOut: FileOutputStream? = null
    private var relayThread: Thread? = null

    @Volatile
    private var proxyOn = false

    @Volatile
    private var proxyFailures = 0

    @Volatile
    private var waitingForProxy = false

    private val dnsFails = AtomicInteger(0)

    @Volatile
    private var failStreakStart = 0L

    private val failedOpen = AtomicBoolean(false)

    @Volatile
    private var upstreamCache: Triple<Long, Network?, List<InetAddress>> = Triple(0L, null, emptyList())

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        pool = Executors.newFixedThreadPool(16)
        watchdog = Executors.newSingleThreadScheduledExecutor()
        watchdog.scheduleWithFixedDelay({ checkProxy() }, 10, 10, TimeUnit.SECONDS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Prefs.vpnWanted = false
                shutdown()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RESTART -> {
                pool.execute { restart() }
                return START_STICKY
            }
        }
        synchronized(life) {
            if (tun == null) start()
        }
        return START_STICKY
    }

    override fun onRevoke() {
        Prefs.vpnWanted = false
        shutdown()
        stopSelf()
    }

    override fun onDestroy() {
        shutdown()
        watchdog.shutdownNow()
        pool.shutdownNow()
        super.onDestroy()
    }

    private fun restart() {
        synchronized(life) {
            if (tun == null) return
            shutdown()
            start()
        }
    }

    private fun start() {
        val db = Db.get(this)
        Rules.load(db)
        pool.execute { db.prune(System.currentTimeMillis() - 14 * Night.DAY) }

        val wantProxy = Prefs.proxyMode
        var proxy = false
        var reason: String? = null
        waitingForProxy = false
        if (wantProxy) {
            reason = when {
                !TProxyService.loaded -> "Не удалось загрузить модуль туннеля на этом устройстве."
                !proxyReachable() -> {
                    waitingForProxy = true
                    "ByeDPI не отвечает на 127.0.0.1:${Prefs.proxyPort}. Запусти его в режиме прокси, Периметр подхватит его сам."
                }
                else -> null
            }
            proxy = reason == null
        }

        var pfd = establish(proxy)
        if (pfd == null && proxy) {
            proxy = false
            reason = "Android не дал поднять туннель для всего трафика."
            pfd = establish(false)
        }
        if (pfd == null) {
            _proxyActive.value = false
            stopSelf()
            return
        }
        tun = pfd
        out = FileOutputStream(pfd.fileDescriptor)

        if (proxy) {
            reason = startRelay()
            proxy = reason == null
            if (!proxy) {
                try {
                    pfd.close()
                } catch (_: Exception) {
                }
                val plain = establish(false)
                if (plain == null) {
                    tun = null
                    out = null
                    _proxyActive.value = false
                    stopSelf()
                    return
                }
                tun = plain
                out = FileOutputStream(plain.fileDescriptor)
            }
        }

        proxyOn = proxy
        proxyFailures = 0
        dnsFails.set(0)
        failStreakStart = 0L
        failedOpen.set(false)
        _proxyActive.value = proxy
        _proxyError.value = if (wantProxy) reason else null
        Prefs.vpnWanted = true
        goForeground(proxy)
        _running.value = true
        val input = FileInputStream(tun!!.fileDescriptor)
        reader = Thread({ loop(input) }, "perimetr-tun").also { it.start() }
        if (proxy) pool.execute { learnProxyOwner() }
    }

    private fun establish(proxy: Boolean): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession("Периметр")
            .setMtu(if (proxy) PROXY_MTU else 16384)
            .addAddress(TUN_ADDR, 32)
            .addDnsServer(DNS_ADDR)
            .setBlocking(true)
        if (proxy) {
            builder.addRoute("0.0.0.0", 0)
            try {
                builder.addAddress(TUN_ADDR6, 128)
                builder.addRoute("::", 0)
            } catch (_: Exception) {
            }
            ProxyApps.installed(packageManager).forEach { pkg ->
                try {
                    builder.addDisallowedApplication(pkg)
                } catch (_: Exception) {
                }
            }
        } else {
            builder.addRoute(DNS_ADDR, 32)
        }
        try {
            builder.addDisallowedApplication(packageName)
        } catch (_: Exception) {
        }
        return try {
            builder.establish()
        } catch (_: Exception) {
            null
        }
    }

    private fun startRelay(): String? {
        return try {
            val pair = ParcelFileDescriptor.createReliableSocketPair()
            val local = pair[0]
            val remote = pair[1]
            for (fd in listOf(local.fileDescriptor, remote.fileDescriptor)) {
                try {
                    Os.setsockoptInt(fd, OsConstants.SOL_SOCKET, OsConstants.SO_SNDBUF, SOCKET_BUFFER)
                    Os.setsockoptInt(fd, OsConstants.SOL_SOCKET, OsConstants.SO_RCVBUF, SOCKET_BUFFER)
                } catch (_: Exception) {
                }
            }
            val config = File(filesDir, "tunnel.yml")
            config.writeText(
                """
                tunnel:
                  mtu: $PROXY_MTU
                socks5:
                  port: ${Prefs.proxyPort}
                  address: 127.0.0.1
                  udp: 'udp'
                misc:
                  task-stack-size: 81920
                  log-level: error
                """.trimIndent() + "\n",
            )
            if (!TProxyService.TProxyStartService(config.absolutePath, remote.fd)) {
                local.close()
                remote.close()
                return "Модуль туннеля не запустился."
            }
            relayLocal = local
            relayRemote = remote
            relayOut = FileOutputStream(local.fileDescriptor)
            val input = FileInputStream(local.fileDescriptor)
            relayThread = Thread({ relayBack(input) }, "perimetr-relay").also { it.start() }
            null
        } catch (_: Throwable) {
            "Модуль туннеля не запустился."
        }
    }

    private fun stopRelay() {
        try {
            if (TProxyService.loaded) TProxyService.TProxyStopService()
        } catch (_: Throwable) {
        }
        relayThread?.interrupt()
        relayThread = null
        relayOut = null
        try {
            relayLocal?.close()
        } catch (_: Exception) {
        }
        try {
            relayRemote?.close()
        } catch (_: Exception) {
        }
        relayLocal = null
        relayRemote = null
    }

    private fun goForeground(proxy: Boolean) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            try {
                nm?.deleteNotificationChannel("monitor")
            } catch (_: Exception) {
            }
            nm?.createNotificationChannel(
                NotificationChannel(MONITOR_CHANNEL, "Монитор трафика", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
            )
            val pi = PendingIntent.getActivity(
                this,
                2,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val text = if (proxy) {
                "Монитор работает, трафик идёт через ByeDPI."
            } else {
                "Монитор трафика работает. Нажми, чтобы открыть."
            }
            val n = Notification.Builder(this, MONITOR_CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("Периметр на страже")
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .build()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(MONITOR_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(MONITOR_ID, n)
            }
        } catch (_: Exception) {
        }
    }

    private fun shutdown() {
        synchronized(life) {
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (_: Exception) {
            }
            _running.value = false
            _proxyActive.value = false
            proxyOn = false
            reader?.interrupt()
            reader = null
            stopRelay()
            try {
                tun?.close()
            } catch (_: Exception) {
            }
            tun = null
            synchronized(writeLock) { out = null }
        }
    }

    private fun loop(input: FileInputStream) {
        val buf = ByteArray(65535)
        while (!Thread.currentThread().isInterrupted) {
            val n = try {
                input.read(buf)
            } catch (_: Exception) {
                break
            }
            if (n < 0) break
            if (n == 0) continue
            val q = DnsPacket.parse(buf, n)
            if (q != null) {
                try {
                    pool.execute { handle(q) }
                } catch (_: Exception) {
                    break
                }
                continue
            }
            if (proxyOn) {
                try {
                    relayOut?.write(buf, 0, n)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun relayBack(input: FileInputStream) {
        val buf = ByteArray(65535)
        while (!Thread.currentThread().isInterrupted) {
            val n = try {
                input.read(buf)
            } catch (_: Exception) {
                break
            }
            if (n < 0) break
            if (n == 0) continue
            synchronized(writeLock) {
                try {
                    out?.write(buf, 0, n)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun handle(q: DnsQuery) {
        val uid = ownerUid(q)
        val pkg = Owners.pkgFor(packageManager, uid)
        val domain = q.name.trimEnd('.')
        val block = domain.isNotEmpty() && Rules.shouldBlock(pkg, domain)
        if (block) {
            write(DnsPacket.wrap(q, DnsPacket.nxdomain(q.payload)))
        } else {
            val answer = forward(q.payload)
            write(DnsPacket.wrap(q, answer ?: DnsPacket.servfail(q.payload)))
            health(answer != null)
        }
        if (domain.isNotEmpty()) record(uid, pkg, domain, block)
    }

    private fun health(ok: Boolean) {
        if (ok) {
            dnsFails.set(0)
            failStreakStart = 0L
            return
        }
        val now = System.currentTimeMillis()
        if (dnsFails.incrementAndGet() == 1) failStreakStart = now
        if (dnsFails.get() >= 15 && now - failStreakStart > 20_000 && networkUp() && failedOpen.compareAndSet(false, true)) {
            pool.execute { failOpen() }
        }
    }

    private fun networkUp(): Boolean = try {
        val cm = getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (_: Exception) {
        false
    }

    private fun failOpen() {
        Prefs.vpnWanted = false
        Prefs.lastFailOpen = System.currentTimeMillis()
        shutdown()
        Notifier.failOpen(this)
        stopSelf()
    }

    private fun record(uid: Int, pkg: String, domain: String, blocked: Boolean) {
        val now = System.currentTimeMillis()
        val key = "$pkg|$domain|$blocked"
        val prev = lastLogged[key]
        if (prev != null && now - prev < 30_000) return
        lastLogged[key] = now
        if (lastLogged.size > 5000) lastLogged.clear()
        val db = Db.get(this)
        val fresh = !db.seen(pkg, domain)
        try {
            db.log(now, uid, pkg, domain, blocked)
        } catch (_: Exception) {
        }
        if (fresh && Prefs.nightAlerts && Night.isNight(now) && Owners.isApp(pkg) && pkg !in Rules.trustedApps &&
            !NightRules.quietDomain(domain) && !screenOn()
        ) {
            val first = db.firstSeen(pkg)
            if (first != null && now - first > Night.DAY && alerted.putIfAbsent("$pkg|$domain", true) == null) {
                Notifier.nightContact(this, Labels(this).of(pkg), domain, pkg)
            }
        }
    }

    private fun screenOn(): Boolean = try {
        getSystemService(PowerManager::class.java)?.isInteractive ?: false
    } catch (_: Exception) {
        false
    }

    private fun ownerUid(q: DnsQuery): Int = try {
        val cm = getSystemService(ConnectivityManager::class.java)
        cm.getConnectionOwnerUid(
            OsConstants.IPPROTO_UDP,
            InetSocketAddress(InetAddress.getByAddress(q.src), q.srcPort),
            InetSocketAddress(InetAddress.getByAddress(q.dst), q.dstPort),
        )
    } catch (_: Exception) {
        -1
    }

    private fun proxyReachable(): Boolean = try {
        Socket().use { s ->
            s.connect(InetSocketAddress("127.0.0.1", Prefs.proxyPort), 700)
            true
        }
    } catch (_: Exception) {
        false
    }

    private fun learnProxyOwner() {
        try {
            Socket().use { s ->
                s.connect(InetSocketAddress("127.0.0.1", Prefs.proxyPort), 700)
                val cm = getSystemService(ConnectivityManager::class.java)
                val uid = cm.getConnectionOwnerUid(
                    OsConstants.IPPROTO_TCP,
                    InetSocketAddress(InetAddress.getByName("127.0.0.1"), Prefs.proxyPort),
                    InetSocketAddress(s.localAddress, s.localPort),
                )
                if (uid < 0) return
                val pkg = packageManager.getPackagesForUid(uid)?.firstOrNull() ?: return
                if (pkg == packageName) return
                if (pkg !in ProxyApps.installed(packageManager)) {
                    Prefs.proxyPkg = pkg
                    restart()
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun checkProxy() {
        if (tun == null || !Prefs.proxyMode) {
            proxyFailures = 0
            return
        }
        val reachable = proxyReachable()
        if (proxyOn && !reachable) {
            proxyFailures++
            if (proxyFailures >= 2) {
                proxyFailures = 0
                restart()
            }
        } else if (!proxyOn && waitingForProxy && reachable) {
            restart()
        } else {
            proxyFailures = 0
        }
    }

    private fun upstreams(): List<InetAddress> {
        val now = System.currentTimeMillis()
        val cm = getSystemService(ConnectivityManager::class.java)
        val network = try {
            cm.activeNetwork
        } catch (_: Exception) {
            null
        }
        val cached = upstreamCache
        if (now - cached.first < 60_000 && cached.second == network && cached.third.isNotEmpty()) return cached.third
        val list = ArrayList<InetAddress>()
        try {
            cm.getLinkProperties(network)?.dnsServers?.let { list.addAll(it) }
        } catch (_: Exception) {
        }
        list.removeAll { it.hostAddress == DNS_ADDR || it.isLoopbackAddress }
        for (fallback in FALLBACK_DNS) list.add(InetAddress.getByName(fallback))
        val result = list.distinct().take(6)
        upstreamCache = Triple(now, network, result)
        return result
    }

    private fun forward(payload: ByteArray): ByteArray? {
        if (proxyOn) Doh.query(payload)?.let { return it }
        repeat(2) {
            race(payload)?.let { return it }
        }
        return null
    }

    private fun race(payload: ByteArray): ByteArray? {
        val servers = upstreams()
        if (servers.isEmpty()) return null
        try {
            DatagramSocket().use { s ->
                protect(s)
                for (server in servers) {
                    try {
                        s.send(DatagramPacket(payload, payload.size, server, 53))
                    } catch (_: Exception) {
                    }
                }
                val deadline = System.currentTimeMillis() + 2500
                val rb = ByteArray(8192)
                while (true) {
                    val left = deadline - System.currentTimeMillis()
                    if (left <= 0) return null
                    s.soTimeout = left.toInt()
                    val rp = DatagramPacket(rb, rb.size)
                    s.receive(rp)
                    val answer = rb.copyOf(rp.length)
                    if (DnsPacket.sameId(payload, answer)) return answer
                }
            }
        } catch (_: Exception) {
            return null
        }
    }

    private fun write(packet: ByteArray) {
        synchronized(writeLock) {
            try {
                out?.write(packet)
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        const val ACTION_START = "com.perimetr.guard.START"
        const val ACTION_STOP = "com.perimetr.guard.STOP"
        const val ACTION_RESTART = "com.perimetr.guard.RESTART"
        private const val MONITOR_CHANNEL = "guard"
        private const val MONITOR_ID = 7701
        private const val TUN_ADDR = "10.111.0.1"
        private const val TUN_ADDR6 = "fd00:111::1"
        private const val DNS_ADDR = "10.111.0.2"
        private const val PROXY_MTU = 8500
        private const val SOCKET_BUFFER = 4 * 1024 * 1024
        private val FALLBACK_DNS = listOf("1.1.1.1", "8.8.8.8", "77.88.8.8")

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running

        private val _proxyActive = MutableStateFlow(false)
        val proxyActive: StateFlow<Boolean> = _proxyActive

        private val _proxyError = MutableStateFlow<String?>(null)
        val proxyError: StateFlow<String?> = _proxyError
    }
}
