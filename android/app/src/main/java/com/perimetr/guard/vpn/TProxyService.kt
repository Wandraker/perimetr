package com.perimetr.guard.vpn

object TProxyService {
    val loaded: Boolean = try {
        System.loadLibrary("hev-socks5-tunnel")
        true
    } catch (_: Throwable) {
        false
    }

    @JvmStatic
    external fun TProxyStartService(configPath: String, fd: Int): Boolean

    @JvmStatic
    external fun TProxyStopService(): Boolean

    @JvmStatic
    external fun TProxyIsRunning(): Boolean

    @JvmStatic
    external fun TProxyGetStats(): LongArray?
}
