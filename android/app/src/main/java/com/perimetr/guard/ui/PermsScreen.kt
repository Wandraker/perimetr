package com.perimetr.guard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.perimetr.guard.Actions
import com.perimetr.guard.Level
import com.perimetr.guard.UiState
import com.perimetr.guard.data.Access
import com.perimetr.guard.data.AppAccess

private val order = listOf(
    Access.ACCESSIBILITY,
    Access.NOTIFICATIONS,
    Access.DEVICE_ADMIN,
    Access.OVERLAY,
    Access.PHOTOS_FULL,
    Access.ALL_FILES,
    Access.CAMERA,
    Access.MIC,
    Access.LOCATION,
    Access.CONTACTS,
    Access.SMS,
    Access.PHOTOS_PARTIAL,
)

private fun iconFor(a: Access): ImageVector = when (a) {
    Access.ACCESSIBILITY -> Icons.Filled.Visibility
    Access.NOTIFICATIONS -> Icons.Filled.NotificationsActive
    Access.DEVICE_ADMIN -> Icons.Filled.AdminPanelSettings
    Access.OVERLAY -> Icons.Filled.Layers
    Access.PHOTOS_FULL -> Icons.Filled.PhotoLibrary
    Access.ALL_FILES -> Icons.Filled.FolderOpen
    Access.CAMERA -> Icons.Filled.PhotoCamera
    Access.MIC -> Icons.Filled.Mic
    Access.LOCATION -> Icons.Filled.LocationOn
    Access.CONTACTS -> Icons.Filled.Contacts
    Access.SMS -> Icons.Filled.Sms
    Access.PHOTOS_PARTIAL -> Icons.Filled.Collections
}

private fun risky(a: Access): Boolean = a == Access.PHOTOS_FULL || a == Access.ALL_FILES || a == Access.SMS ||
    a == Access.ACCESSIBILITY || a == Access.NOTIFICATIONS || a == Access.DEVICE_ADMIN

@Composable
fun PermsScreen(s: UiState, act: Actions, pad: PaddingValues) {
    val groups = order.map { a -> a to s.audit.filter { a in it.access } }.filter { it.second.isNotEmpty() }
    val nightPkgs = s.night.filter { it.level == Level.CRIT }.map { it.pkg }.toSet()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = pad.calculateTopPadding() + 14.dp,
            bottom = pad.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Hint("Сверху те, кто может тайно видеть твой экран и уведомления. Ниже фото, камера и остальное. Пароль на альбоме защищает только от человека с телефоном в руках: приложение с доступом «Все фото» читает и скрытые альбомы.")
        }
        if (!s.loaded) item { Hint("Сканирую установленные приложения…") }
        groups.forEach { (access, apps) ->
            item(key = access.name) { Group(access, apps, nightPkgs, act) }
        }
    }
}

@Composable
private fun Group(access: Access, apps: List<AppAccess>, nightPkgs: Set<String>, act: Actions) {
    var open by rememberSaveable(access.name) { mutableStateOf(access == Access.ACCESSIBILITY || access == Access.PHOTOS_FULL || access == Access.ALL_FILES) }
    val level = when {
        apps.any { it.pkg in nightPkgs } && risky(access) -> Level.CRIT
        risky(access) -> Level.WARN
        else -> null
    }
    Panel(level = level) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconTile(iconFor(access))
            Column(Modifier.weight(1f)) {
                Text(access.title, color = P.Fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("${apps.size} прил.", color = P.Muted, fontSize = 12.sp)
            }
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = P.Faint)
        }
        if (open) {
            Text(access.hint, color = P.Muted, fontSize = 12.5.sp, lineHeight = 18.sp)
            Column {
                apps.forEachIndexed { i, a ->
                    if (i > 0) HorizontalDivider(color = P.Line)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AppIcon(a.pkg, a.label, 32.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(a.label, color = P.Fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (a.pkg in nightPkgs) Pill("ВЫГРУЖАЛО НОЧЬЮ", Level.CRIT)
                                if (a.internet && risky(access)) Pill("ЕСТЬ ИНТЕРНЕТ", null)
                            }
                        }
                        TextButton(onClick = { act.openAccess(access, a.pkg) }) {
                            Text("Изменить", color = P.Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
