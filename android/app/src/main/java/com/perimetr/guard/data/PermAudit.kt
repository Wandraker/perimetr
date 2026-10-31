package com.perimetr.guard.data

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

enum class Access(val title: String, val hint: String) {
    ACCESSIBILITY(
        "Видят и управляют экраном",
        "Сервисы «Специальных возможностей» молча читают всё на экране, делают скриншоты и нажимают кнопки за тебя. Так работают банковские трояны. Оставь только то, что ставил сам: менеджер паролей, экранный диктор.",
    ),
    NOTIFICATIONS(
        "Читают все уведомления",
        "Видят текст каждого уведомления, включая коды из SMS и сообщения из мессенджеров.",
    ),
    DEVICE_ADMIN(
        "Администратор устройства",
        "Может блокировать экран, стирать данные и мешать своему удалению. Нужен только корпоративным и родительским приложениям.",
    ),
    OVERLAY(
        "Поверх других приложений",
        "Может рисовать окна поверх банка или страницы входа. Так подделывают экраны ввода пароля. Нормально для чат-пузырей и фонариков-кнопок.",
    ),
    PHOTOS_FULL(
        "Все фото и видео",
        "Видят всю галерею, включая скрытые альбомы. Индикатора, как у камеры, нет. Поставь «Выбранные фото» или «Запретить».",
    ),
    ALL_FILES(
        "Все файлы",
        "Полный доступ к памяти телефона: фото, документы, загрузки, сканы паспорта. Нужен только файловым менеджерам.",
    ),
    CAMERA("Камера", "Могут снимать, пока приложение открыто. Android показывает зелёную точку."),
    MIC("Микрофон", "Могут записывать звук, пока приложение открыто. Android показывает зелёную точку."),
    LOCATION("Геолокация", "Знают, где ты находишься. Проверь, кому нужен доступ «Всегда»."),
    CONTACTS("Контакты", "Могут выгрузить всю твою записную книжку."),
    SMS("SMS", "Читают сообщения, включая коды из банков."),
    PHOTOS_PARTIAL("Только выбранные фото", "Видят лишь то, что ты сам выбрал. Это безопасный вариант."),
}

data class AppAccess(
    val pkg: String,
    val uid: Int,
    val label: String,
    val access: Set<Access>,
    val internet: Boolean,
) {
    val seesAllPhotos: Boolean get() = Access.PHOTOS_FULL in access || Access.ALL_FILES in access
}

object PermAudit {
    private const val VISUAL_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
    private const val OP_ALL_FILES = "android:manage_external_storage"
    private const val OP_OVERLAY = "android:system_alert_window"

    @Suppress("DEPRECATION")
    fun scan(ctx: Context): List<AppAccess> {
        val pm = ctx.packageManager
        val pkgs: List<PackageInfo> = if (Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        val screen = Watchers.screen(ctx)
        val out = ArrayList<AppAccess>()
        for (pi in pkgs) {
            val ai: ApplicationInfo = pi.applicationInfo ?: continue
            if (pi.packageName == ctx.packageName) continue
            if (ai.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue
            val req = pi.requestedPermissions ?: emptyArray()
            val flags = pi.requestedPermissionsFlags ?: IntArray(0)
            val granted = HashSet<String>()
            for (i in req.indices) {
                if (i < flags.size && flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0) granted.add(req[i])
            }
            val access = HashSet<Access>()
            val fullMedia = Manifest.permission.READ_MEDIA_IMAGES in granted || Manifest.permission.READ_EXTERNAL_STORAGE in granted
            if (fullMedia) access.add(Access.PHOTOS_FULL)
            else if (VISUAL_SELECTED in granted) access.add(Access.PHOTOS_PARTIAL)
            if (Manifest.permission.MANAGE_EXTERNAL_STORAGE in req) {
                val allowed = try {
                    ops?.unsafeCheckOpNoThrow(OP_ALL_FILES, ai.uid, pi.packageName) == AppOpsManager.MODE_ALLOWED
                } catch (_: Exception) {
                    false
                }
                if (allowed) access.add(Access.ALL_FILES)
            }
            if (Manifest.permission.CAMERA in granted) access.add(Access.CAMERA)
            if (Manifest.permission.RECORD_AUDIO in granted) access.add(Access.MIC)
            if (Manifest.permission.ACCESS_FINE_LOCATION in granted || Manifest.permission.ACCESS_COARSE_LOCATION in granted) {
                access.add(Access.LOCATION)
            }
            if (Manifest.permission.READ_CONTACTS in granted) access.add(Access.CONTACTS)
            if (Manifest.permission.READ_SMS in granted) access.add(Access.SMS)
            screen[pi.packageName]?.let { access.addAll(it) }
            if (Manifest.permission.SYSTEM_ALERT_WINDOW in req) {
                val allowed = try {
                    ops?.unsafeCheckOpNoThrow(OP_OVERLAY, ai.uid, pi.packageName) == AppOpsManager.MODE_ALLOWED
                } catch (_: Exception) {
                    false
                }
                if (allowed) access.add(Access.OVERLAY)
            }
            if (access.isEmpty()) continue
            val label = try {
                ai.loadLabel(pm).toString()
            } catch (_: Exception) {
                pi.packageName
            }
            out.add(AppAccess(pi.packageName, ai.uid, label, access, Manifest.permission.INTERNET in req))
        }
        return out.sortedBy { it.label.lowercase() }
    }
}
