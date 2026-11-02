package com.perimetr.guard.vpn

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.perimetr.guard.data.Prefs

object Revive {
    private const val RESUME_ID = 4202

    fun run(ctx: Context, markKill: Boolean): Boolean {
        Prefs.init(ctx)
        if (!Prefs.vpnWanted || GuardVpnService.running.value) return false
        val ready = try {
            VpnService.prepare(ctx) == null
        } catch (_: Exception) {
            false
        }
        if (!ready) return false
        return try {
            ctx.startForegroundService(Intent(ctx, GuardVpnService::class.java).setAction(GuardVpnService.ACTION_START))
            if (markKill) Prefs.lastKill = System.currentTimeMillis()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun delayMinutes(streak: Int): Long = minOf(60L, 5L shl streak.coerceIn(0, 4))

    fun scheduleResume(ctx: Context) {
        val js = ctx.getSystemService(JobScheduler::class.java) ?: return
        val job = JobInfo.Builder(RESUME_ID, ComponentName(ctx, ResumeJob::class.java))
            .setMinimumLatency(delayMinutes(Prefs.failStreak) * 60_000L)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .build()
        try {
            js.schedule(job)
            Prefs.resumePending = true
            Prefs.failStreak += 1
        } catch (_: Exception) {
        }
    }

    fun cancelResume(ctx: Context) {
        Prefs.resumePending = false
        try {
            ctx.getSystemService(JobScheduler::class.java)?.cancel(RESUME_ID)
        } catch (_: Exception) {
        }
    }
}

class ResumeJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Prefs.init(this)
        if (Prefs.resumePending) {
            Prefs.resumePending = false
            Prefs.vpnWanted = true
            Revive.run(applicationContext, markKill = false)
        }
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean = false
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                WatchJob.schedule(ctx)
                Revive.run(ctx, markKill = false)
            }
        }
    }
}
