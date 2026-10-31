package com.perimetr.guard.vpn

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.perimetr.guard.data.Access
import com.perimetr.guard.data.Db
import com.perimetr.guard.data.Labels
import com.perimetr.guard.data.Prefs
import com.perimetr.guard.data.Rules
import com.perimetr.guard.data.Watchers

object WatchCheck {
    fun run(ctx: Context) {
        try {
            Prefs.init(ctx)
            Rules.load(Db.get(ctx))
            val keys = Watchers.screen(ctx).flatMap { (pkg, set) -> set.map { "$pkg|${it.name}" } }.toSet()
            val known = Prefs.knownWatchers
            if (known != null) {
                val labels = Labels(ctx)
                (keys - known).forEach { k ->
                    val (pkg, name) = k.split('|')
                    if (pkg !in Rules.trustedApps) Notifier.watcher(ctx, labels.of(pkg), Access.valueOf(name), pkg)
                }
            }
            Prefs.knownWatchers = keys
        } catch (_: Exception) {
        }
    }
}

class WatchJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            WatchCheck.run(applicationContext)
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        private const val ID = 4201

        fun schedule(ctx: Context) {
            val js = ctx.getSystemService(JobScheduler::class.java) ?: return
            if (js.getPendingJob(ID) != null) return
            val job = JobInfo.Builder(ID, ComponentName(ctx, WatchJob::class.java))
                .setPeriodic(15 * 60 * 1000L)
                .setPersisted(true)
                .build()
            try {
                js.schedule(job)
            } catch (_: Exception) {
            }
        }
    }
}
