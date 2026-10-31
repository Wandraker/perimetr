package com.perimetr.guard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.perimetr.guard.data.Access
import com.perimetr.guard.data.Prefs
import com.perimetr.guard.ui.PerimetrApp
import com.perimetr.guard.ui.PerimetrTheme
import com.perimetr.guard.vpn.GuardVpnService
import com.perimetr.guard.vpn.WatchCheck
import com.perimetr.guard.vpn.WatchJob

class Actions(
    val setVpn: (Boolean) -> Unit,
    val openUsageAccess: () -> Unit,
    val requestNotifications: () -> Unit,
    val openAppSettings: (String) -> Unit,
    val openAccess: (Access, String) -> Unit,
    val openVpnSettings: () -> Unit,
    val setSecure: (Boolean) -> Unit,
    val requestBattery: () -> Unit,
    val openDataSettings: () -> Unit,
)

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val vpnPrepare = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) launchVpn()
    }

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refreshLight()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        applySecure(Prefs.secure)
        WatchJob.schedule(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            savedInstanceState == null
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        enableEdgeToEdge()
        val actions = Actions(
            setVpn = { on -> if (on) startVpn() else stopVpn() },
            openUsageAccess = ::openUsageAccess,
            requestNotifications = {
                if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            openAppSettings = { pkg ->
                open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
            },
            openAccess = { access, pkg ->
                val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                val target = when (access) {
                    Access.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    Access.NOTIFICATIONS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    Access.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$pkg"))
                    Access.DEVICE_ADMIN -> Intent(Settings.ACTION_SECURITY_SETTINGS)
                    else -> details
                }
                if (!open(target)) open(details)
            },
            openVpnSettings = { open(Intent(Settings.ACTION_VPN_SETTINGS)) },
            setSecure = { on ->
                Prefs.secure = on
                applySecure(on)
                vm.refreshLight()
            },
            requestBattery = {
                val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                if (!open(direct)) open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            },
            openDataSettings = {
                val direct = Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, Uri.parse("package:$packageName"))
                if (!open(direct)) open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            },
        )
        setContent {
            PerimetrTheme {
                PerimetrApp(vm, actions)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Thread { WatchCheck.run(applicationContext) }.start()
        restoreVpn()
        vm.refresh()
    }

    private fun applySecure(on: Boolean) {
        if (on) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun restoreVpn() {
        if (!Prefs.vpnWanted || GuardVpnService.running.value) return
        val needsConsent = try {
            VpnService.prepare(this) != null
        } catch (_: Exception) {
            true
        }
        if (needsConsent) {
            Prefs.vpnWanted = false
        } else {
            Prefs.lastKill = System.currentTimeMillis()
            launchVpn()
        }
    }

    private fun startVpn() {
        val intent = try {
            VpnService.prepare(this)
        } catch (_: Exception) {
            null
        }
        if (intent != null) vpnPrepare.launch(intent) else launchVpn()
    }

    private fun launchVpn() {
        startService(Intent(this, GuardVpnService::class.java).setAction(GuardVpnService.ACTION_START))
    }

    private fun stopVpn() {
        startService(Intent(this, GuardVpnService::class.java).setAction(GuardVpnService.ACTION_STOP))
    }

    private fun openUsageAccess() {
        val withPkg = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:$packageName"))
        if (!open(withPkg)) open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun open(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}
