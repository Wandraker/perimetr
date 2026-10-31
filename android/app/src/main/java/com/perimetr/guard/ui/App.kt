package com.perimetr.guard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.perimetr.guard.Actions
import com.perimetr.guard.Level
import com.perimetr.guard.MainViewModel
import com.perimetr.guard.UiState
import kotlinx.coroutines.delay

enum class Tab(val label: String, val title: String, val icon: ImageVector) {
    SHIELD("Щит", "Периметр", Icons.Filled.Shield),
    MONITOR("Монитор", "Монитор трафика", Icons.Filled.NetworkCheck),
    PERMS("Доступы", "Аудит доступов", Icons.Filled.PrivacyTip),
    PROTECT("Защита", "Защита", Icons.Filled.Lock),
}

@Composable
fun PerimetrApp(vm: MainViewModel, act: Actions) {
    val s by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.SHIELD) }
    val owner = LocalLifecycleOwner.current

    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(4000)
                vm.refreshLight()
            }
        }
    }

    var batteryLater by rememberSaveable { mutableStateOf(false) }
    if (s.loaded && !s.batteryFree && !batteryLater) {
        AlertDialog(
            onDismissRequest = { batteryLater = true },
            containerColor = P.Surface,
            icon = { Icon(Icons.Filled.BatteryChargingFull, contentDescription = null, tint = P.Accent) },
            title = { Text("Разреши работу в фоне", color = P.Fg, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Иначе система будет выключать монитор, чтобы сэкономить батарею, и сливы перестанут быть видны. " +
                        "Периметр почти не тратит заряд: он обрабатывает только запросы к серверам.",
                    color = P.Muted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    batteryLater = true
                    act.requestBattery()
                }) {
                    Text("Разрешить", color = P.Accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        batteryLater = true
                        vm.ackBattery()
                    }) {
                        Text("Уже разрешил", color = P.Muted)
                    }
                    TextButton(onClick = { batteryLater = true }) {
                        Text("Позже", color = P.Muted)
                    }
                }
            },
        )
    }

    Scaffold(
        containerColor = P.Bg,
        topBar = { TopBar(tab, s) },
        bottomBar = { BottomTabs(tab, s) { tab = it } },
    ) { pad ->
        when (tab) {
            Tab.SHIELD -> ShieldScreen(s, vm, act, pad) { tab = it }
            Tab.MONITOR -> MonitorScreen(s, vm, act, pad)
            Tab.PERMS -> PermsScreen(s, act, pad)
            Tab.PROTECT -> ProtectScreen(s, vm, act, pad) { tab = it }
        }
    }
}

@Composable
private fun TopBar(tab: Tab, s: UiState) {
    Column(Modifier.background(P.Bg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = P.Accent, modifier = Modifier.size(26.dp))
            Text(tab.title, color = P.Fg, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            if (s.vpnOn) Pill("НА СТРАЖЕ", Level.OK) else Pill("МОНИТОР ВЫКЛ", Level.WARN)
        }
        HorizontalDivider(color = P.Line)
    }
}

@Composable
private fun BottomTabs(tab: Tab, s: UiState, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = P.Surface, tonalElevation = 0.dp) {
        Tab.entries.forEach { t ->
            val badge = when (t) {
                Tab.SHIELD -> s.night.count { it.level == Level.CRIT }
                Tab.MONITOR -> s.apps.count { it.level == Level.CRIT && !it.appBlocked }
                else -> 0
            }
            NavigationBarItem(
                selected = t == tab,
                onClick = { onSelect(t) },
                icon = {
                    if (badge > 0) {
                        BadgedBox(badge = { Badge(containerColor = P.Crit) { Text("$badge") } }) {
                            Icon(t.icon, contentDescription = t.label)
                        }
                    } else {
                        Icon(t.icon, contentDescription = t.label)
                    }
                },
                label = { Text(t.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = P.Accent,
                    selectedTextColor = P.Accent,
                    indicatorColor = P.AccentDim,
                    unselectedIconColor = P.Faint,
                    unselectedTextColor = P.Faint,
                ),
            )
        }
    }
}
