package com.perimetr.guard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.perimetr.guard.Actions
import com.perimetr.guard.Level
import com.perimetr.guard.MainViewModel
import com.perimetr.guard.UiState
import com.perimetr.guard.data.Trackers

@Composable
fun ProtectScreen(s: UiState, vm: MainViewModel, act: Actions, pad: PaddingValues, go: (Tab) -> Unit) {
    val ctxPkg = LocalContext.current.packageName
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
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconTile(Icons.Filled.Visibility)
                    Column(Modifier.weight(1f)) {
                        Text("Тайные скриншоты и запись экрана", color = P.Fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (s.screenWatchers.isEmpty()) "Сейчас никто не видит твой экран через «Специальные возможности»." else "Видят экран: ${s.screenWatchers.size} прил.",
                            color = if (s.screenWatchers.isEmpty()) P.Ok else P.Warn,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Text(
                    "Обычная запись экрана тайно невозможна: Android всегда спрашивает разрешение и показывает значок. " +
                        "Молча видеть экран и делать скриншоты могут только приложения со «Специальными возможностями». " +
                        "Периметр проверяет это каждые 15 минут, даже когда монитор выключен, и пришлёт тревогу, если такой доступ появится у нового приложения. " +
                        "Уже сделанные скриншоты в галерее уносят приложения с доступом ко всем фото.",
                    color = P.Muted,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                )
                ActionButton("Проверить доступы", Modifier.fillMaxWidth(), quiet = s.screenWatchers.isEmpty()) { go(Tab.PERMS) }
            }
        }
        item {
            ToggleCard(
                Icons.Filled.Block,
                "Авто-блок трекеров",
                "Сразу резать обращения к ${Trackers.size} рекламным и аналитическим сервисам, без спроса.",
                s.autoBlock,
            ) { vm.setAutoBlock(it) }
        }
        item {
            ToggleCard(
                Icons.Filled.NightsStay,
                "Тревога о ночной активности",
                "Уведомление, когда приложение с 00:00 до 06:00 при выключенном экране впервые обращается к новому серверу. Картинки и видео с серверов Google, Яндекса, VK и соцсетей не в счёт. Первые сутки Периметр запоминает обычное поведение и молчит.",
                s.nightAlerts,
            ) { vm.setNightAlerts(it) }
        }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconTile(Icons.Filled.BatteryChargingFull)
                    Column(Modifier.weight(1f)) {
                        Text("Чтобы монитор не выключался", color = P.Fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (s.batteryFree) "Работа в фоне разрешена." else "Система может выключить монитор, чтобы сэкономить батарею.",
                            color = if (s.batteryFree) P.Ok else P.Warn,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Text(
                    "1. Разреши работу в фоне без ограничений.\n" +
                        "2. Включи для Периметра «Постоянная VPN»: тогда Android сам перезапустит монитор после перезагрузки или сбоя.\n" +
                        "3. На Xiaomi, Realme, OPPO, OnePlus, Huawei в сведениях о приложении включи «Автозапуск» и разреши фоновую активность.",
                    color = P.Muted,
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                )
                if (!s.batteryFree) {
                    ActionButton("Разрешить работу в фоне", Modifier.fillMaxWidth()) { act.requestBattery() }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("Постоянная VPN", Modifier.weight(1f), quiet = true) { act.openVpnSettings() }
                    ActionButton("Автозапуск", Modifier.weight(1f), quiet = true) { act.openAppSettings(ctxPkg) }
                }
            }
        }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconTile(Icons.Filled.Dns, tint = P.Warn, bg = P.Warn.copy(alpha = 0.14f))
                    Column(Modifier.weight(1f)) {
                        Text("Частный DNS", color = P.Fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Если в настройках сети «Частный DNS» задан конкретный сервер, запросы идут мимо Периметра. Поставь «Автоматически».",
                            color = P.Muted,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
        }
        item {
            ToggleCard(
                Icons.Filled.Screenshot,
                "Запрет скриншотов самого Периметра",
                "Обычно не нужно: в Периметре нет паролей и фото. Включи, если не хочешь, чтобы кто-то заснял список твоих приложений.",
                s.secure,
            ) { act.setSecure(it) }
        }
        item { ProxyCard(s, vm) }
        item { SectionHead("Честно о границах") }
        item {
            Hint(
                "Периметр видит, к какому серверу обращается приложение и сколько оно отправило. Содержимое зашифрованного трафика он не видит. " +
                    "Приложение, которое ходит по IP-адресу напрямую или через свой зашифрованный DNS, монитор не поймает. Но объём такой выгрузки всё равно виден в «Прошлой ночи». " +
                    "Отобрать доступ к экрану автоматически Android не даёт: Периметр находит такие приложения и ведёт в нужные настройки, отключаешь ты сам.",
            )
        }
        item { Licenses() }
    }
}

@Composable
private fun ProxyCard(s: UiState, vm: MainViewModel) {
    var portText by rememberSaveable(s.proxyPort) { mutableStateOf(s.proxyPort.toString()) }
    Panel(level = if (s.proxyMode && s.proxyError != null && s.vpnOn) Level.WARN else null) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(Icons.Filled.VpnLock)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Совместимость с ByeDPI", color = P.Fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                val (status, color) = when {
                    !s.proxyMode -> "Выключено. Android держит только один VPN: пока работает ByeDPI как VPN, монитор Периметра на паузе." to P.Muted
                    !s.vpnOn -> "Включится вместе с монитором трафика." to P.Muted
                    s.proxyActive -> "Работает: монитор следит, трафик идёт через ByeDPI на 127.0.0.1:${s.proxyPort}." to P.Ok
                    else -> (s.proxyError ?: "Подключаюсь к ByeDPI…") to P.Warn
                }
                Text(status, color = color, fontSize = 12.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            Switch(
                checked = s.proxyMode,
                onCheckedChange = { vm.setProxyMode(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = P.Bg,
                    checkedTrackColor = P.Accent,
                    uncheckedThumbColor = P.Faint,
                    uncheckedTrackColor = P.Surface2,
                    uncheckedBorderColor = P.Line,
                ),
            )
        }
        Text(
            "Как включить:\n" +
                "1. В ByeDPI выбери режим «Прокси» вместо VPN и запусти его.\n" +
                "2. Включи этот переключатель и монитор трафика.\n" +
                "Периметр станет единственным VPN: сам следит за приложениями и отправляет трафик в ByeDPI. Если ByeDPI остановится, Периметр переключится на обычный режим, а когда ByeDPI вернётся, подхватит его сам.",
            color = P.Muted,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
        )
        Text(
            if (s.proxyApps.isEmpty()) "ByeDPI не найден на телефоне. Если используешь другую сборку, Периметр распознает её сам при первом подключении."
            else "Найден: ${s.proxyApps.joinToString()}",
            color = if (s.proxyApps.isEmpty()) P.Warn else P.Faint,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Порт прокси", color = P.Fg, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = portText,
                onValueChange = { v -> portText = v.filter { it.isDigit() }.take(5) },
                modifier = Modifier.width(110.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { portText.toIntOrNull()?.let { vm.setProxyPort(it) } }),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = P.Fg,
                    unfocusedTextColor = P.Fg,
                    focusedBorderColor = P.Accent,
                    unfocusedBorderColor = P.Line,
                    cursorColor = P.Accent,
                ),
            )
            ActionButton("Сохранить", quiet = portText == s.proxyPort.toString()) { portText.toIntOrNull()?.let { vm.setProxyPort(it) } }
        }
    }
}

@Composable
private fun Licenses() {
    val ctx = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    val text = remember {
        try {
            ctx.assets.open("third_party_licenses.txt").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            ""
        }
    }
    Panel {
        Text("Открытые компоненты", color = P.Fg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(
            "Совместимость с ByeDPI работает на hev-socks5-tunnel (MIT), hev-task-system (MIT), lwIP (BSD) и libyaml (MIT).",
            color = P.Muted,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
        )
        ActionButton(if (open) "Скрыть лицензии" else "Показать лицензии", Modifier.fillMaxWidth(), quiet = true) { open = !open }
        if (open) {
            Text(text, color = P.Faint, fontSize = 11.sp, lineHeight = 15.sp, fontFamily = Mono)
        }
    }
}
