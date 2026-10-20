package com.rootpad.bridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rootpad.bridge.core.AppGraph
import com.rootpad.bridge.core.AppState
import com.rootpad.bridge.core.DaemonStatus
import com.rootpad.bridge.core.LocalResolvedLang
import com.rootpad.bridge.core.Strings
import com.rootpad.bridge.service.RootPadService
import com.rootpad.bridge.ui.components.ChipKind
import com.rootpad.bridge.ui.components.HintText
import com.rootpad.bridge.ui.components.KeyValueRow
import com.rootpad.bridge.ui.components.SectionCard
import com.rootpad.bridge.ui.components.StatusChip

@Composable
fun HomeScreen() {
    val lang = LocalResolvedLang.current
    val context = LocalContext.current
    val daemonStatus by AppState.daemonStatus.collectAsStateWithLifecycle()
    val conversionActive by AppState.conversionActive.collectAsStateWithLifecycle()
    val rootStatus by AppState.rootStatus.collectAsStateWithLifecycle()
    val padInfo by AppState.padInfo.collectAsStateWithLifecycle()
    val androidPad by AppState.androidPad.collectAsStateWithLifecycle()
    val devices by AppState.remoteDevices.collectAsStateWithLifecycle()
    val lastError by AppState.lastError.collectAsStateWithLifecycle()
    val logs by AppState.logs.collectAsStateWithLifecycle()
    val evKbd by AppState.evKeyboard.collectAsStateWithLifecycle()
    val evMouse by AppState.evMouse.collectAsStateWithLifecycle()
    val activeProfile = AppGraph.profiles.active

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ---- 変換の開始/停止 ----
        SectionCard(title = Strings.get(lang, "home.conversion")) {
            Text(
                Strings.get(lang, "home.profile") + ": " + activeProfile.name,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            if (!conversionActive) {
                Button(
                    onClick = { RootPadService.start(context) },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(
                        Strings.get(lang, "home.start"),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = { RootPadService.stop(context) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text(
                        Strings.get(lang, "home.stop"),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (conversionActive) {
                Spacer(Modifier.height(4.dp))
                val grab = if (activeProfile.grabDevices)
                    Strings.get(lang, "common.enabled") else Strings.get(lang, "common.disabled")
                HintText(Strings.format(lang, "home.grab_hint", grab))
            }
        }

        // ---- Root 状態 ----
        SectionCard(
            title = Strings.get(lang, "home.root_status"),
            trailing = {
                OutlinedButton(onClick = {
                    Thread {
                        val r = com.rootpad.bridge.root.RootExecutor.checkRoot()
                        AppState.setRootStatus(r.granted, r.detail)
                    }.start()
                }) { Text(Strings.get(lang, "common.recheck"), fontSize = 12.sp) }
            }
        ) {
            val rs = rootStatus
            if (rs == null) {
                StatusChip(Strings.get(lang, "home.unchecked"), ChipKind.IDLE)
            } else if (rs.granted) {
                StatusChip(Strings.get(lang, "home.root_ok"), ChipKind.OK)
                Spacer(Modifier.height(4.dp))
                KeyValueRow(Strings.get(lang, "home.exec_result"), rs.detail)
            } else {
                StatusChip(Strings.get(lang, "home.root_ng"), ChipKind.FAIL)
                Spacer(Modifier.height(4.dp))
                KeyValueRow(Strings.get(lang, "home.reason"), rs.detail)
                HintText(Strings.get(lang, "home.root_hint"))
            }
        }

        // ---- 仮想コントローラー ----
        SectionCard(title = Strings.get(lang, "home.virtual_pad")) {
            when (val ds = daemonStatus) {
                is DaemonStatus.Stopped -> StatusChip(Strings.get(lang, "home.stopped"), ChipKind.IDLE)
                is DaemonStatus.Starting -> StatusChip(Strings.get(lang, "home.starting"), ChipKind.WARN)
                is DaemonStatus.Running -> StatusChip(
                    Strings.get(lang, "home.running") + " (pid=${ds.pid})", ChipKind.OK
                )
                is DaemonStatus.Failed -> StatusChip(Strings.get(lang, "home.error"), ChipKind.FAIL)
            }
            Spacer(Modifier.height(4.dp))
            padInfo?.let { p ->
                KeyValueRow(Strings.get(lang, "home.pad_devicename"), p.name)
                KeyValueRow(Strings.get(lang, "home.pad_node"), p.node)
                KeyValueRow("uinput", p.uinputPath)
            } ?: KeyValueRow(Strings.get(lang, "home.pad_state"), Strings.get(lang, "home.pad_not_created"))
            Spacer(Modifier.height(4.dp))
            val ap = androidPad
            if (ap != null) {
                if (ap.found) {
                    StatusChip(
                        Strings.get(lang, "home.android_recognized"),
                        if (ap.sourcesGamepad || ap.sourcesJoystick) ChipKind.OK else ChipKind.WARN
                    )
                    Spacer(Modifier.height(4.dp))
                    KeyValueRow(Strings.get(lang, "home.recognized_name"), ap.name ?: "?")
                    KeyValueRow(
                        Strings.get(lang, "home.vid_pid"),
                        "0x%04X:0x%04X".format(ap.vendor, ap.product)
                    )
                    KeyValueRow(
                        Strings.get(lang, "home.classification"),
                        buildList {
                            if (ap.sourcesGamepad) add("GAMEPAD")
                            if (ap.sourcesJoystick) add("JOYSTICK")
                        }.joinToString(" + ").ifEmpty { Strings.get(lang, "home.unclassified") }
                    )
                    if (ap.motionAxes.isNotEmpty()) {
                        KeyValueRow(Strings.get(lang, "home.axes"), ap.motionAxes.joinToString(", "))
                    }
                } else {
                    StatusChip(Strings.get(lang, "home.android_not_recognized"), ChipKind.WARN)
                    HintText(Strings.get(lang, "home.android_hint"))
                }
            }
        }

        // ---- 接続デバイス ----
        SectionCard(title = Strings.get(lang, "home.devices")) {
            if (devices.isEmpty()) {
                Text(
                    Strings.get(lang, "home.no_devices"),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                devices.forEach { d ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (d.kind == "kbd") "⌨ " else "🖱 ") + d.name,
                                fontSize = 13.sp
                            )
                            Text(
                                "event${d.eventn}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusChip(
                            if (d.grabbed) Strings.get(lang, "home.grabbed")
                            else Strings.get(lang, "home.normal"),
                            if (d.grabbed) ChipKind.INFO else ChipKind.IDLE
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    Strings.get(lang, "home.key_events") + ": $evKbd",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    Strings.get(lang, "home.mouse_events") + ": $evMouse",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---- エラー ----
        lastError?.let { err ->
            SectionCard(title = Strings.get(lang, "home.last_error")) {
                Text(
                    err,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // ---- ログ ----
        SectionCard(title = Strings.get(lang, "home.logs")) {
            val tail = logs.takeLast(10)
            if (tail.isEmpty()) {
                Text(
                    Strings.get(lang, "home.no_logs"),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                tail.forEach { l ->
                    Text(
                        "${l.time} ${l.text}",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
