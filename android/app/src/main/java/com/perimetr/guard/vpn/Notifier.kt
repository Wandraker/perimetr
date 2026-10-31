package com.perimetr.guard.vpn

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.perimetr.guard.MainActivity
import com.perimetr.guard.R
import com.perimetr.guard.data.Access

object Notifier {
    private const val CHANNEL = "night"

    fun nightContact(ctx: Context, label: String, domain: String, key: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Ночная активность", NotificationManager.IMPORTANCE_HIGH),
        )
        val intent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            ctx,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("$label выходит в сеть ночью")
            .setContentText("Новый сервер: $domain")
            .setStyle(Notification.BigTextStyle().bigText("Новый сервер: $domain\nОткрой Периметр, чтобы заблокировать."))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(key.hashCode(), n)
    }

    fun watcher(ctx: Context, label: String, access: Access, pkg: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(WATCH_CHANNEL, "Слежка за экраном", NotificationManager.IMPORTANCE_HIGH),
        )
        val intent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            ctx,
            1,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = "$label: ${access.title.lowercase()}. Если ты этого не включал, отключи прямо сейчас."
        val n = Notification.Builder(ctx, WATCH_CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Новый доступ к экрану")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify((pkg + access.name).hashCode(), n)
    }

    fun failOpen(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(HEALTH_CHANNEL, "Состояние монитора", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val intent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            ctx,
            3,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = "Запросы к серверам перестали проходить, поэтому Периметр выключил монитор, чтобы интернет работал. Открой Периметр, чтобы узнать причину."
        val n = Notification.Builder(ctx, HEALTH_CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Монитор выключен, интернет работает")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(7702, n)
    }

    private const val HEALTH_CHANNEL = "health"
    private const val WATCH_CHANNEL = "watchers"
}
