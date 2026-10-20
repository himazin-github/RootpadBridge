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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rootpad.bridge.core.AppState
import com.rootpad.bridge.core.LocalResolvedLang
import com.rootpad.bridge.core.ResolvedLang
import com.rootpad.bridge.core.Strings
import com.rootpad.bridge.service.AndroidInputProbe
import com.rootpad.bridge.service.RootPadService
import com.rootpad.bridge.ui.components.ChipKind
import com.rootpad.bridge.ui.components.HintText
import com.rootpad.bridge.ui.components.SectionCard
import com.rootpad.bridge.ui.components.StatusChip

@Composable
fun DiagnosticsScreen() {
    val lang = LocalResolvedLang.current
    val rootStatus by AppState.rootStatus.collectAsStateWithLifecycle()
    val sysInfo by AppState.sysInfo.collectAsStateWithLifecycle()
    val selfTest by AppState.selfTest.collectAsStateWithLifecycle()
    val padInfo by AppState.padInfo.collectAsStateWithLifecycle()
    val androidPad by AppState.androidPad.collectAsStateWithLifecycle()
    val keylayout by AppState.keylayoutInstalled.collectAsStateWithLifecycle()
    val devices by AppState.remoteDevices.collectAsStateWithLifecycle()
    val evKbd by AppState.evKeyboard.collectAsStateWithLifecycle()
    val evMouse by AppState.evMouse.collectAsStateWithLifecycle()
    val currentPad by AppState.currentPad.collectAsStateWithLifecycle()
    val conversionActive by AppState.conversionActive.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionCard(
            title = Strings.get(lang, "diag.system"),
            trailing = {
                OutlinedButton(onClick = {
                    Thread {
                        val r = com.rootpad.bridge.root.RootExecutor.checkRoot()
                        AppState.setRootStatus(r.granted, r.detail)
                    }.start()
                    RootPadService.refreshDevices()
                }) { Text(Strings.get(lang, "common.remeasure"), fontSize = 12.sp) }
            }
        ) {
            // 1. Root
            DiagRow(
                title = Strings.get(lang, "diag.root"),
                value = rootStatus?.let {
                    if (it.granted) it.detail
                    else Strings.get(lang, "diag.root_fail") + ": " + it.detail
                } ?: Strings.get(lang, "home.unchecked"),
                kind = when (rootStatus?.granted) {
                    true -> ChipKind.OK
                    false -> ChipKind.FAIL
                    null -> ChipKind.IDLE
                },
                remedy = if (rootStatus?.granted == false) Strings.get(lang, "diag.root_remedy") else null
            )
            HorizontalDivider()

            // 2. uinput
            val miscuinput = sysInfo["miscuinput"]
            val uinputInfo = sysInfo["uinput"]
            DiagRow(
                title = Strings.get(lang, "diag.uinput"),
                value = when {
                    uinputInfo?.startsWith("ok") == true ->
                        Strings.get(lang, "diag.uinput_ok") + ": $uinputInfo"
                    uinputInfo?.startsWith("fail") == true ->
                        Strings.get(lang, "diag.uinput_fail") + ": $uinputInfo"
                    miscuinput == "1" -> Strings.get(lang, "diag.uinput_misc_exists")
                    miscuinput == "0" -> Strings.get(lang, "diag.uinput_misc_missing")
                    else -> Strings.get(lang, "diag.uinput_unknown")
                },
                kind = when {
                    uinputInfo?.startsWith("ok") == true -> ChipKind.OK
                    uinputInfo?.startsWith("fail") == true || miscuinput == "0" -> ChipKind.FAIL
                    else -> ChipKind.IDLE
                },
                remedy = if (miscuinput == "0" || uinputInfo?.startsWith("fail") == true)
                    Strings.get(lang, "diag.uinput_remedy") else null
            )
            HorizontalDivider()

            // 3. 仮想デバイス作成テスト
            val st = selfTest
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        Strings.get(lang, "diag.selftest"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    OutlinedButton(
                        onClick = { RootPadService.runSelfTest() },
                        enabled = conversionActive
                    ) { Text(Strings.get(lang, "common.run"), fontSize = 12.sp) }
                }
                Spacer(Modifier.height(4.dp))
                when {
                    st == null -> Text(
                        if (conversionActive) Strings.get(lang, "diag.selftest_not_run")
                        else Strings.get(lang, "diag.selftest_after_start"),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    st.ok -> {
                        StatusChip(Strings.get(lang, "diag.selftest_ok"), ChipKind.OK)
                        Text(st.detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> {
                        StatusChip(
                            Strings.get(lang, "common.fail") + ": ${st.detail}", ChipKind.FAIL
                        )
                        st.errno?.let { e ->
                            Text(
                                "errno=$e: " + when (e) {
                                    13 -> Strings.get(lang, "diag.errno_13")
                                    2 -> Strings.get(lang, "diag.errno_2")
                                    22 -> Strings.get(lang, "diag.errno_22")
                                    else -> Strings.get(lang, "diag.errno_other")
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            HorizontalDivider()

            // 4. 仮想パッド
            DiagRow(
                title = Strings.get(lang, "diag.virtual_pad"),
                value = padInfo?.let { "${it.name} → ${it.node} (${it.uinputPath})" }
                    ?: Strings.get(lang, "diag.not_created"),
                kind = if (padInfo != null) ChipKind.OK else ChipKind.IDLE
            )
            HorizontalDivider()

            // 5. Android 認識
            val ap = androidPad
            DiagRow(
                title = Strings.get(lang, "diag.android_recognition"),
                value = when {
                    ap == null -> Strings.get(lang, "diag.recognition_after_start")
                    !ap.found -> Strings.get(lang, "diag.recognition_not_found")
                    ap.sourcesGamepad && ap.sourcesJoystick ->
                        Strings.format(lang, "diag.recognition_both", ap.name)
                    ap.sourcesGamepad ->
                        Strings.format(lang, "diag.recognition_gamepad_only", ap.name)
                    ap.sourcesJoystick ->
                        Strings.format(lang, "diag.recognition_joystick_only", ap.name)
                    else -> Strings.format(lang, "diag.recognition_unclassified", ap.name)
                },
                kind = when {
                    ap == null -> ChipKind.IDLE
                    !ap.found -> ChipKind.WARN
                    ap.sourcesGamepad || ap.sourcesJoystick -> ChipKind.OK
                    else -> ChipKind.WARN
                },
                remedy = if (ap != null && !ap.found) Strings.get(lang, "diag.recognition_remedy") else null
            )
            if (ap != null && ap.found && !ap.sourcesGamepad) {
                HintText(Strings.get(lang, "diag.recognition_keylayout_hint"))
            }
            HorizontalDivider()

            // 6. キーレイアウト
            DiagRow(
                title = Strings.get(lang, "diag.keylayout"),
                value = when (keylayout) {
                    true -> Strings.get(lang, "diag.keylayout_ok")
                    false -> Strings.get(lang, "diag.keylayout_fail")
                    null -> Strings.get(lang, "diag.keylayout_skipped")
                },
                kind = when (keylayout) {
                    true -> ChipKind.OK
                    false -> ChipKind.WARN
                    null -> ChipKind.IDLE
                }
            )
            HorizontalDivider()

            // 7. 物理デバイス
            Column {
                Text(
                    Strings.get(lang, "diag.device_list"),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                if (devices.isEmpty()) {
                    Text(
                        Strings.get(lang, "diag.device_list_empty"),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    devices.forEach { d ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "event${d.eventn} [${d.kind}] ${d.name}",
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            StatusChip(
                                if (d.grabbed) Strings.get(lang, "diag.grab")
                                else Strings.get(lang, "diag.released"),
                                if (d.grabbed) ChipKind.INFO else ChipKind.IDLE
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    Strings.get(lang, "diag.android_devices"),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AndroidInputProbe.listLocalInput().forEach { d ->
                    val kind = listOf(
                        if (d.isKeyboard) "kbd" else null,
                        if (d.isMouse) "mouse" else null,
                        if (d.isGamepad) "pad" else null
                    ).filterNotNull().joinToString(",")
                    Text(
                        "#${d.id} [${kind}] ${d.name}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider()

            // 8. イベントカウンタ
            DiagRow(
                title = Strings.get(lang, "diag.event_counter"),
                value = Strings.format(lang, "diag.event_counter_value", evKbd, evMouse),
                kind = if (evKbd + evMouse > 0) ChipKind.OK else ChipKind.IDLE
            )
            if (conversionActive) {
                Text(
                    Strings.get(lang, "diag.current_output") + ": buttons=0x${Integer.toHexString(currentPad.buttons)} " +
                        "LS=(${currentPad.lx},${currentPad.ly}) RS=(${currentPad.rx},${currentPad.ry}) " +
                        "T=(${currentPad.lt},${currentPad.rt}) HAT=(${currentPad.hatX},${currentPad.hatY})",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider()

            // 9. カーネル情報
            DiagRow(
                title = Strings.get(lang, "diag.kernel"),
                value = sysInfo["kernel"] ?: Strings.get(lang, "diag.not_obtained"),
                kind = ChipKind.INFO
            )
            DiagRow(
                title = Strings.get(lang, "diag.selinux"),
                value = (sysInfo["selinux"] ?: Strings.get(lang, "common.unknown")) + " / " +
                    (sysInfo["ctx"] ?: Strings.get(lang, "diag.ctx_unknown")),
                kind = when (sysInfo["selinux"]) {
                    "permissive" -> ChipKind.OK
                    "enforcing" -> ChipKind.WARN
                    else -> ChipKind.INFO
                },
                remedy = if (sysInfo["selinux"] == "enforcing")
                    Strings.get(lang, "diag.selinux_remedy") else null
            )
        }

        // トラブルシューティング
        SectionCard(title = Strings.get(lang, "trouble.title")) {
            Troubleshoot(
                Strings.get(lang, "trouble.q1"),
                Strings.get(lang, "trouble.a1"),
                lang
            )
            Troubleshoot(
                Strings.get(lang, "trouble.q2"),
                Strings.get(lang, "trouble.a2"),
                lang
            )
            Troubleshoot(
                Strings.get(lang, "trouble.q3"),
                Strings.get(lang, "trouble.a3"),
                lang
            )
            Troubleshoot(
                Strings.get(lang, "trouble.q4"),
                Strings.get(lang, "trouble.a4"),
                lang
            )
            Troubleshoot(
                Strings.get(lang, "trouble.q5"),
                Strings.get(lang, "trouble.a5"),
                lang
            )
            Troubleshoot(
                Strings.get(lang, "trouble.q6"),
                Strings.get(lang, "trouble.a6"),
                lang
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DiagRow(
    title: String,
    value: String,
    kind: ChipKind,
    remedy: String? = null
) {
    val lang = LocalResolvedLang.current
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            StatusChip(
                when (kind) {
                    ChipKind.OK -> Strings.get(lang, "common.success")
                    ChipKind.WARN -> Strings.get(lang, "common.warn")
                    ChipKind.FAIL -> Strings.get(lang, "common.fail")
                    ChipKind.INFO -> Strings.get(lang, "common.info")
                    ChipKind.IDLE -> Strings.get(lang, "common.idle")
                },
                kind
            )
        }
        Text(
            value,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
        if (remedy != null) {
            Text(
                Strings.get(lang, "diag.remedy_prefix") + ": " + remedy,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun Troubleshoot(problem: String, solution: String, lang: ResolvedLang) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(problem, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(
            solution,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
