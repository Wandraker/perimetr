package com.perimetr.guard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.perimetr.guard.Actions
import com.perimetr.guard.AppRow
import com.perimetr.guard.DomainRow
import com.perimetr.guard.Fmt
import com.perimetr.guard.Level
import com.perimetr.guard.MainViewModel
import com.perimetr.guard.UiState
import com.perimetr.guard.data.Night

enum class TrafficFilter(val label: String) {
    ALL("Все"),
    BLOCKED("Заблокировано"),
    TRACKERS("Трекеры"),
    YOUNG("Молодые домены"),
}

private fun matches(f: TrafficFilter, a: AppRow, d: DomainRow): Boolean = when (f) {
    TrafficFilter.ALL -> true
    TrafficFilter.BLOCKED -> a.appBlocked || d.ruleBlocked || d.blockedHits > 0
    TrafficFilter.TRACKERS -> d.tracker
    TrafficFilter.YOUNG -> d.young
}

private fun applyFilter(apps: List<AppRow>, f: TrafficFilter, query: String): List<AppRow> {
    val q = query.trim().lowercase()
    return apps.mapNotNull { a ->
        val appHit = q.isEmpty() || a.label.lowercase().contains(q) || a.pkg.lowercase().contains(q)
        val ds = a.domains.filter { d -> matches(f, a, d) && (appHit || d.domain.contains(q)) }
        if (ds.isEmpty()) null else a.copy(domains = ds)
    }
}

@Composable
fun MonitorScreen(s: UiState, vm: MainViewModel, act: Actions, pad: PaddingValues) {
    var filter by rememberSaveable { mutableStateOf(TrafficFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = applyFilter(s.apps, filter, query)
    val periodLabel = if (s.period == Night.DAY) "24 часа" else "7 дней"

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
            ToggleCard(
                Icons.Filled.VpnKey,
                "Монитор трафика",
                if (s.vpnOn && s.proxyActive) {
                    "Работает вместе с ByeDPI. Периметр видит, к каким серверам обращаются приложения, режет заблокированные и отправляет трафик в ByeDPI."
                } else if (s.vpnOn) {
                    "Работает. Периметр видит, к каким серверам обращаются приложения, и режет заблокированные. Остальной трафик идёт напрямую."
                } else {
                    "Выключен. Включи, чтобы видеть, кто и куда отправляет данные. Если пользуешься ByeDPI, сначала включи «Совместимость с ByeDPI» в «Защите», иначе Android отключит его: одновременно работает только один VPN."
                },
                s.vpnOn,
            ) { act.setVpn(it) }
        }
        item { AgeCheckCard(s, vm) }
        item {
            SearchField(query) { query = it }
        }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TrafficFilter.entries.forEach { f ->
                    Chip(f.label, f == filter) { filter = f }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("24 ч", s.period == Night.DAY) { vm.setPeriod(Night.DAY) }
                Chip("7 дней", s.period == 7 * Night.DAY) { vm.setPeriod(7 * Night.DAY) }
                Text(
                    "заблокировано: ${s.blockedInPeriod}",
                    color = P.Faint,
                    fontSize = 12.sp,
                    fontFamily = Mono,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                    maxLines = 1,
                )
            }
        }
        if (filter == TrafficFilter.BLOCKED && (s.domainRules.isNotEmpty() || s.appRules.isNotEmpty())) {
            item { RulesCard(s, vm) }
        }
        item {
            SectionHead("За $periodLabel", if (shown.isNotEmpty()) "${shown.size} прил." else null)
        }
        if (shown.isEmpty()) {
            item {
                Hint(
                    when {
                        query.isNotBlank() -> "Ничего не нашлось по запросу «${query.trim()}»."
                        filter == TrafficFilter.BLOCKED -> "За $periodLabel ничего не заблокировано."
                        filter == TrafficFilter.TRACKERS -> "Трекеров за $periodLabel не было."
                        filter == TrafficFilter.YOUNG -> "Молодых доменов не найдено. Нажми «Проверить новые домены», если ещё не проверял."
                        s.vpnOn -> "Пока тихо. Записи появятся, как только приложения начнут выходить в сеть."
                        else -> "Здесь будет список: какое приложение, к какому серверу, когда и сколько раз обращалось."
                    },
                )
            }
        }
        items(shown, key = { "a_" + it.pkg }) { AppCard(it, vm) }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("Приложение или домен", color = P.Faint) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = P.Faint) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Очистить", tint = P.Faint)
                }
            }
        },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = P.Fg,
            unfocusedTextColor = P.Fg,
            focusedBorderColor = P.Accent,
            unfocusedBorderColor = P.Line,
            focusedContainerColor = P.Surface,
            unfocusedContainerColor = P.Surface,
            cursorColor = P.Accent,
        ),
    )
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Text(
        text,
        color = if (selected) P.Bg else P.Muted,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) P.Accent else P.Surface, shape)
            .border(1.dp, if (selected) P.Accent else P.Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun RulesCard(s: UiState, vm: MainViewModel) {
    Panel {
        Text("Мои правила блокировки", color = P.Fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Column {
            var first = true
            s.appRules.forEach { (pkg, label) ->
                if (!first) HorizontalDivider(color = P.Line)
                first = false
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AppIcon(pkg, label, 28.dp)
                    Column(Modifier.weight(1f)) {
                        Text(label, color = P.Fg, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("приложение без сети", color = P.Faint, fontSize = 11.sp)
                    }
                    TextButton(onClick = { vm.toggleApp(pkg) }) {
                        Text("Вернуть", color = P.Muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
            s.domainRules.forEach { d ->
                if (!first) HorizontalDivider(color = P.Line)
                first = false
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        d,
                        color = P.Fg,
                        fontSize = 12.5.sp,
                        fontFamily = Mono,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.toggleDomain(d) }) {
                        Text("Вернуть", color = P.Muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AgeCheckCard(s: UiState, vm: MainViewModel) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(Icons.Filled.Schedule)
            Column(Modifier.weight(1f)) {
                Text("Возраст доменов", color = P.Fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Сервер, зарегистрированный меньше 90 дней назад, — частый признак слива. Имена доменов отправятся на rdap.org для проверки.",
                    color = P.Muted,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                )
            }
        }
        val check = s.ageCheck
        if (check != null) {
            val progress = if (check.total == 0) 0f else check.done.toFloat() / check.total
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = P.Accent,
                trackColor = P.Surface2,
            )
            Text("Проверено ${check.done} из ${check.total}", color = P.Faint, fontSize = 11.5.sp, fontFamily = Mono)
        } else {
            ActionButton("Проверить новые домены", Modifier.fillMaxWidth(), quiet = s.apps.isEmpty()) { vm.checkAges() }
        }
    }
}

@Composable
private fun AppCard(a: AppRow, vm: MainViewModel) {
    var expanded by rememberSaveable(a.pkg) { mutableStateOf(false) }
    Panel(level = if (a.appBlocked) null else a.level.takeIf { it != Level.OK }) {
        AppHeader(a.pkg, a.label, "${a.total} запросов · ${Fmt.ago(a.last)}") {
            when {
                a.appBlocked -> Pill("БЕЗ СЕТИ", null)
                a.level == Level.CRIT -> Pill("МОЛОДОЙ ДОМЕН", Level.CRIT)
                a.level == Level.WARN -> Pill("ТРЕКЕРЫ", Level.WARN)
                else -> Pill("НОРМА", Level.OK)
            }
        }
        val shown = if (expanded) a.domains else a.domains.take(4)
        Column {
            shown.forEachIndexed { i, d ->
                if (i > 0) HorizontalDivider(color = P.Line)
                DomainLine(d) { vm.toggleDomain(d.domain) }
            }
        }
        if (a.domains.size > 4) {
            Text(
                if (expanded) "Свернуть" else "Показать все серверы (${a.domains.size})",
                color = P.Accent,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 2.dp).clickableNoRipple { expanded = !expanded },
            )
        }
        ActionButton(
            if (a.appBlocked) "Вернуть приложению интернет" else "Отрезать приложение от сети",
            Modifier.fillMaxWidth(),
            danger = !a.appBlocked && a.level == Level.CRIT,
            quiet = a.appBlocked || a.level != Level.CRIT,
        ) { vm.toggleApp(a.pkg) }
    }
}

@Composable
private fun DomainLine(d: DomainRow, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                d.domain,
                color = if (d.ruleBlocked) P.Faint else P.Fg,
                fontSize = 12.5.sp,
                fontFamily = Mono,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (d.ruleBlocked) Pill("ЗАБЛОКИРОВАН", Level.CRIT)
                else if (d.blockedHits > 0 && d.tracker) Pill("ТРЕКЕР · СРЕЗАН", Level.OK)
                else if (d.tracker) Pill("ТРЕКЕР", Level.WARN)
                if (d.young) Pill("ДОМЕНУ ${d.ageDays} ДН.", Level.CRIT)
                Text("×${d.count} · ${Fmt.time(d.last)}", color = P.Faint, fontSize = 11.sp, fontFamily = Mono, maxLines = 1)
            }
        }
        TextButton(onClick = onToggle) {
            Text(
                if (d.ruleBlocked) "Вернуть" else "Блок",
                color = if (d.ruleBlocked) P.Muted else P.Crit,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
        }
    }
}
