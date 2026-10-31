package com.perimetr.guard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.perimetr.guard.Actions
import com.perimetr.guard.Fmt
import com.perimetr.guard.Level
import com.perimetr.guard.MainViewModel
import com.perimetr.guard.NightRow
import com.perimetr.guard.UiState
import com.perimetr.guard.data.Access

@Composable
fun ShieldScreen(s: UiState, vm: MainViewModel, act: Actions, pad: PaddingValues, go: (Tab) -> Unit) {
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
        item { Hero(s) }
        if (!s.vpnOn || !s.usageAccess || !s.notifications || !s.batteryFree || s.bgDataRestricted) {
            item { Setup(s, vm, act) }
        }
        if (s.failOpenAt > 0) {
            item { FailOpen(s, vm, act) }
        }
        if (s.killedAt > 0) {
            item { Killed(s, vm, act) }
        }
        if (s.screenWatchers.isNotEmpty()) {
            item { ScreenWatchers(s, act) }
        }
        item { Stats(s) }
        item {
            val window = if (s.nightFrom > 0) "${Fmt.time(s.nightFrom)}–${Fmt.time(s.nightTo)}" else null
            SectionHead("Прошлая ночь", window)
        }
        when {
            s.night.isNotEmpty() -> items(s.night, key = { "n_" + it.pkg }) { NightCard(it, vm, act, go) }
            !s.usageAccess -> item {
                Hint("Выдай доступ к статистике трафика. Тогда Периметр покажет, какие приложения и сколько отправляли с 00:00 до 06:00, пока ты спал.")
            }
            else -> item { Hint("Ночью никто подозрительно не выгружал данные.") }
        }
    }
}

@Composable
private fun Hero(s: UiState) {
    val color = when {
        s.score >= 85 -> P.Ok
        s.score >= 60 -> P.Warn
        else -> P.Crit
    }
    val title = when {
        !s.loaded -> "Проверяю телефон…"
        s.score >= 85 -> "Всё спокойно"
        s.score >= 60 -> "Нужно внимание"
        else -> "Опасно"
    }
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(P.Surface2, P.Surface)), shape)
            .border(1.dp, color.copy(alpha = 0.25f), shape)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScoreRing(s.score, color)
        Text(title, color = P.Fg, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text(s.headline, color = P.Muted, fontSize = 13.5.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Setup(s: UiState, vm: MainViewModel, act: Actions) {
    Panel(level = Level.WARN) {
        Text(if (!s.vpnOn || !s.usageAccess) "Чтобы видеть сливы, включи" else "Чтобы монитор не выключался", color = P.Fg, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        if (!s.vpnOn) {
            SetupRow(Icons.Filled.VpnKey, "Монитор трафика", "Видеть, кто и куда обращается", "Включить") { act.setVpn(true) }
        }
        if (!s.usageAccess) {
            SetupRow(Icons.Filled.BarChart, "Статистика трафика", "Сколько мегабайт ушло ночью", "Выдать") { act.openUsageAccess() }
            val pkg = LocalContext.current.packageName
            Text(
                "Если Android пишет «Доступ для приложения запрещён»: открой сведения о Периметре, нажми ⋮ в правом верхнем углу → «Разрешить ограниченные настройки», потом снова нажми «Выдать».",
                color = P.Muted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            ActionButton("Сведения о Периметре", Modifier.fillMaxWidth(), quiet = true) { act.openAppSettings(pkg) }
        }
        if (!s.batteryFree) {
            SetupRow(Icons.Filled.BatteryChargingFull, "Работа в фоне", "Чтобы система не выключала монитор", "Разрешить") { act.requestBattery() }
            Text(
                "Уже разрешил в настройках телефона",
                color = P.Accent,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { vm.ackBattery() },
            )
        }
        if (s.bgDataRestricted) {
            SetupRow(Icons.Filled.SignalCellularAlt, "Мобильный интернет в фоне", "Без него Периметр не может пропускать запросы, и интернет пропадает", "Разрешить") { act.openDataSettings() }
        }
        if (!s.notifications) {
            SetupRow(Icons.Filled.Notifications, "Уведомления", "Тревога о ночной активности", "Разрешить") { act.requestNotifications() }
        }
    }
}

@Composable
private fun SetupRow(icon: ImageVector, title: String, sub: String, button: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconTile(icon)
        Column(Modifier.weight(1f)) {
            Text(title, color = P.Fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = P.Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        ActionButton(button, onClick = onClick)
    }
}

@Composable
private fun FailOpen(s: UiState, vm: MainViewModel, act: Actions) {
    Panel(level = Level.WARN) {
        Text("Монитор выключался, чтобы не оставить тебя без интернета", color = P.Fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(
            "В ${Fmt.time(s.failOpenAt)} запросы к серверам перестали проходить больше 20 секунд подряд, и Периметр выключил монитор.\n\n" +
                "Частые причины:\n" +
                "• Периметру запрещён мобильный интернет в фоне или включена «Экономия трафика».\n" +
                "• Оболочка телефона ограничила Периметр в фоне.\n" +
                "• Сеть оператора на время пропала.",
            color = P.Muted,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
        )
        ActionButton("Разрешить интернет в фоне", Modifier.fillMaxWidth(), quiet = true) { act.openDataSettings() }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("Включить монитор", Modifier.weight(1f)) {
                vm.clearFailOpen()
                act.setVpn(true)
            }
            ActionButton("Понятно", quiet = true) { vm.clearFailOpen() }
        }
    }
}

@Composable
private fun Killed(s: UiState, vm: MainViewModel, act: Actions) {
    val pkg = LocalContext.current.packageName
    Panel(level = Level.WARN) {
        Text("Система выключала монитор", color = P.Fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(
            "Периметр включил его снова в ${Fmt.time(s.killedAt)}, когда ты открыл приложение. Пока монитор был выключен, сливы не были видны.\n\n" +
                "Чтобы так не было:\n" +
                "1. Закрепи Периметр в недавних приложениях: открой список недавних, нажми ⋮ или потяни карточку вниз и выбери замок.\n" +
                "2. В сведениях о приложении включи «Автозапуск», а в разделе батареи разреши фоновую активность.\n" +
                "3. Включи «Постоянная VPN» для Периметра.",
            color = P.Muted,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("Сведения о приложении", Modifier.weight(1f), quiet = true) { act.openAppSettings(pkg) }
            ActionButton("Понятно", quiet = true) { vm.clearKill() }
        }
    }
}

@Composable
private fun ScreenWatchers(s: UiState, act: Actions) {
    Panel(level = Level.WARN) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(Icons.Filled.Visibility, tint = P.Warn, bg = P.Warn.copy(alpha = 0.14f))
            Column(Modifier.weight(1f)) {
                Text("Видят твой экран", color = P.Fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Эти приложения через «Специальные возможности» читают всё на экране и могут делать скриншоты. Оставь только те, что ставил сам.",
                    color = P.Muted,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                )
            }
        }
        s.screenWatchers.forEach { a ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppIcon(a.pkg, a.label, 32.dp)
                Text(a.label, color = P.Fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                ActionButton("Отключить", danger = true) { act.openAccess(Access.ACCESSIBILITY, a.pkg) }
            }
        }
    }
}

@Composable
private fun Stats(s: UiState) {
    val crit = s.night.count { it.level != Level.OK }
    val photos = s.audit.count { it.seesAllPhotos }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Stat(Modifier.weight(1f), "$crit", "подозрительно ночью", if (crit > 0) P.Crit else P.Fg)
        Stat(Modifier.weight(1f), "${s.blockedToday}", "заблокировано за сутки", P.Ok)
        Stat(Modifier.weight(1f), "$photos", "видят все фото", if (photos > 0) P.Warn else P.Fg)
    }
}

@Composable
private fun Stat(modifier: Modifier, value: String, label: String, color: androidx.compose.ui.graphics.Color) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .background(P.Surface, shape)
            .border(1.dp, P.Line, shape)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
        Text(label, color = P.Muted, fontSize = 11.sp, lineHeight = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun NightCard(r: NightRow, vm: MainViewModel, act: Actions, go: (Tab) -> Unit) {
    Panel(level = r.level.takeIf { it != Level.OK }) {
        AppHeader(r.pkg, r.label, r.pkg) {
            when {
                r.trusted -> Pill("ДОВЕРЕНО", null)
                r.level == Level.CRIT && r.photos -> Pill("ФОТО", Level.CRIT)
                r.level == Level.CRIT -> Pill("СЛИВ", Level.CRIT)
                r.level == Level.WARN -> Pill("ПРОВЕРЬ", Level.WARN)
                else -> Pill("НОРМА", Level.OK)
            }
        }
        if (r.tx > 0) FlowBar("отправлено ночью", "↑ " + Fmt.bytes(r.tx), r.level == Level.CRIT)
        Text(r.reason, color = P.Muted, fontSize = 12.5.sp, lineHeight = 18.sp)
        r.domains.take(4).forEach { d ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    d.domain,
                    color = P.Fg,
                    fontSize = 12.sp,
                    fontFamily = Mono,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (d.young) Pill("${d.ageDays} ДН.", Level.CRIT)
                if (d.tracker) Pill("ТРЕКЕР", Level.WARN)
                Text(Fmt.time(d.last), color = P.Faint, fontSize = 11.sp, fontFamily = Mono)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (r.photos) {
                ActionButton("Забрать доступ к фото", Modifier.weight(1f), danger = r.level == Level.CRIT) { act.openAppSettings(r.pkg) }
            } else {
                ActionButton("Права приложения", Modifier.weight(1f), danger = r.level == Level.CRIT) { act.openAppSettings(r.pkg) }
            }
            ActionButton(if (r.trusted) "Не доверять" else "Доверяю", quiet = true) { vm.toggleTrust(r.pkg) }
        }
        if (r.domains.isNotEmpty()) {
            Text(
                "Заблокировать серверы →",
                color = P.Accent,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clickable { go(Tab.MONITOR) },
            )
        }
    }
}
