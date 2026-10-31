package com.perimetr.guard.data

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

object Watchers {
    fun accessibility(ctx: Context): Set<String> {
        val out = HashSet<String>()
        try {
            ctx.getSystemService(AccessibilityManager::class.java)
                ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                ?.forEach { info -> info.resolveInfo?.serviceInfo?.packageName?.let(out::add) }
        } catch (_: Exception) {
        }
        try {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.let { components(it, out) }
        } catch (_: Exception) {
        }
        out.remove(ctx.packageName)
        return out
    }

    fun notificationListeners(ctx: Context): Set<String> {
        val out = HashSet<String>()
        try {
            Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")?.let { components(it, out) }
        } catch (_: Exception) {
        }
        out.remove(ctx.packageName)
        return out
    }

    fun admins(ctx: Context): Set<String> = try {
        ctx.getSystemService(DevicePolicyManager::class.java)?.activeAdmins
            ?.map { it.packageName }
            ?.filter { it != ctx.packageName }
            ?.toSet()
            ?: emptySet()
    } catch (_: Exception) {
        emptySet()
    }

    fun screen(ctx: Context): Map<String, Set<Access>> {
        val out = HashMap<String, MutableSet<Access>>()
        accessibility(ctx).forEach { out.getOrPut(it) { HashSet() }.add(Access.ACCESSIBILITY) }
        notificationListeners(ctx).forEach { out.getOrPut(it) { HashSet() }.add(Access.NOTIFICATIONS) }
        admins(ctx).forEach { out.getOrPut(it) { HashSet() }.add(Access.DEVICE_ADMIN) }
        return out
    }

    private fun components(value: String, out: MutableSet<String>) {
        value.split(':').forEach { c ->
            ComponentName.unflattenFromString(c)?.packageName?.let(out::add)
        }
    }
}
