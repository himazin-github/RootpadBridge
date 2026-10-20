package com.rootpad.bridge.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.isForwardPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rootpad.bridge.core.AppGraph
import com.rootpad.bridge.core.AppState
import com.rootpad.bridge.core.LinuxKeys
import com.rootpad.bridge.core.LocalResolvedLang
import com.rootpad.bridge.core.PadAction
import com.rootpad.bridge.core.PadButton
import com.rootpad.bridge.core.ResolvedLang
import com.rootpad.bridge.core.ScrollDirection
import com.rootpad.bridge.core.Strings
import com.rootpad.bridge.ui.components.ChipKind
import com.rootpad.bridge.ui.components.HintText
import com.rootpad.bridge.ui.components.SectionCard
import com.rootpad.bridge.ui.components.StatusChip

/** マウスボタン (Compose PointerButtons) → Linux BTN_* */
private fun pressedMouseButton(
    buttons: androidx.compose.ui.input.pointer.PointerButtons
): Int? = when {
    buttons.isPrimaryPressed -> LinuxKeys.BTN_LEFT
    buttons.isSecondaryPressed -> LinuxKeys.BTN_RIGHT
    buttons.isTertiaryPressed -> LinuxKeys.BTN_MIDDLE
    buttons.isBackPressed -> LinuxKeys.BTN_SIDE
    buttons.isForwardPressed -> LinuxKeys.BTN_EXTRA
    else -> null
}

/** マウスボタン表示名 (言語別) を返す */
private fun mouseButtonLabel(code: Int, lang: ResolvedLang): String = when (code) {
    LinuxKeys.BTN_LEFT -> Strings.get(lang, "keys.mouse.left")
    LinuxKeys.BTN_RIGHT -> Strings.get(lang, "keys.mouse.right")
    LinuxKeys.BTN_MIDDLE -> Strings.get(lang, "keys.mouse.middle")
    LinuxKeys.BTN_SIDE -> Strings.get(lang, "keys.mouse.side")
    LinuxKeys.BTN_EXTRA -> Strings.get(lang, "keys.mouse.extra")
    else -> LinuxKeys.label(code, lang)
}

private val mouseButtonCodes = listOf(
    LinuxKeys.BTN_LEFT,
    LinuxKeys.BTN_RIGHT,
    LinuxKeys.BTN_MIDDLE,
    LinuxKeys.BTN_SIDE,
    LinuxKeys.BTN_EXTRA
)

/** 指定アクションに割り当てられているキー (重複割り当てに対応) */
private fun keysFor(bindings: Map<Int, Set<PadAction>>, action: PadAction): List<Int> =
    bindings.filterValues { action in it }.keys.sorted()

@Composable
fun KeyMapScreen() {
    val lang = LocalResolvedLang.current
    val profiles by AppGraph.profiles.profiles.collectAsStateWithLifecycle()
    val activeId by AppGraph.profiles.activeId.collectAsStateWithLifecycle()
    val profile = profiles.firstOrNull { it.id == activeId } ?: return
    val conversionActive by AppState.conversionActive.collectAsStateWithLifecycle()

    var captureAction by remember { mutableStateOf<PadAction?>(null) }
    var menuForAction by remember { mutableStateOf<PadAction?>(null) }

    /**
     * キー/ホイールをアクションへ割り当てる。
     * 重複を許可するため、同じキーに複数のアクションを割り当て可能。
     */
    fun assignKey(linuxCode: Int, action: PadAction) {
        AppGraph.profiles.updateActive { p ->
            val existing = p.keyBindings[linuxCode].orEmpty()
            p.copy(keyBindings = p.keyBindings + (linuxCode to (existing + action)))
        }
        com.rootpad.bridge.service.RootPadService.pushProfile()
    }

    /** アクションの割り当てをすべて解除 */
    fun clearAction(action: PadAction) {
        AppGraph.profiles.updateActive { p ->
            p.copy(
                keyBindings = p.keyBindings
                    .mapValues { (_, v) -> v - action }
                    .filterValues { it.isNotEmpty() }
            )
        }
        com.rootpad.bridge.service.RootPadService.pushProfile()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (conversionActive) {
            SectionCard(title = Strings.get(lang, "keys.converting")) {
                HintText(Strings.get(lang, "keys.dialog_hint"))
            }
        }

        SectionCard(title = Strings.get(lang, "keys.mouse_buttons")) {
            mouseButtonCodes.forEach { code ->
                val current = profile.mouse.buttonBindings[code] ?: PadAction.NONE
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(mouseButtonLabel(code, lang), fontSize = 14.sp)
                    ActionDropdown(
                        selected = current,
                        onSelect = { action ->
                            AppGraph.profiles.updateActive { p ->
                                val m = p.mouse.buttonBindings.toMutableMap()
                                if (action == PadAction.NONE) m.remove(code) else m[code] = action
                                p.copy(mouse = p.mouse.copy(buttonBindings = m))
                            }
                        }
                    )
                }
            }
            HintText(Strings.get(lang, "keys.mouse_hint"))
        }

        SectionCard(title = Strings.get(lang, "keys.wheel")) {
            ScrollDirection.entries.forEach { dir ->
                val current = profile.mouse.scrollBindings[dir] ?: PadAction.NONE
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(dir.label(lang), fontSize = 14.sp)
                    ActionDropdown(
                        selected = current,
                        onSelect = { action ->
                            AppGraph.profiles.updateActive { p ->
                                val m = p.mouse.scrollBindings.toMutableMap()
                                m[dir] = action
                                p.copy(mouse = p.mouse.copy(scrollBindings = m))
                            }
                            com.rootpad.bridge.service.RootPadService.pushProfile()
                        }
                    )
                }
            }
            HintText(Strings.get(lang, "keys.wheel_hint"))
        }

        SectionCard(
            title = Strings.get(lang, "keys.list"),
            trailing = {
                OutlinedButton(onClick = {
                    AppGraph.profiles.resetToDefault(profile.id)
                }) { Text(Strings.get(lang, "common.reset"), fontSize = 12.sp) }
            }
        ) {
            PadAction.entries.filter { it != PadAction.NONE }.forEach { action ->
                val keys = keysFor(profile.keyBindings, action)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { menuForAction = action }
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(action.label(lang), fontSize = 14.sp, modifier = Modifier.weight(1f))
                    if (keys.isEmpty()) {
                        Text(
                            Strings.get(lang, "common.unassigned"),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            keys.forEach { k ->
                                StatusChip(LinuxKeys.label(k, lang), ChipKind.INFO)
                            }
                        }
                    }
                }
            }
            HintText(Strings.get(lang, "keys.list_hint"))
        }

        Spacer(Modifier.height(24.dp))
    }

    // ---- キー/マウスボタンの割り当てダイアログ (一覧行から: 重複許可) ----
    captureAction?.let { action ->
        CaptureDialog(
            action = action,
            currentKeys = keysFor(profile.keyBindings, action),
            onCaptured = { linuxCode ->
                assignKey(linuxCode, action)
                captureAction = null
            },
            onClear = {
                clearAction(action)
                captureAction = null
            },
            onDismiss = { captureAction = null }
        )
    }

    menuForAction?.let { action ->
        CaptureDialog(
            action = action,
            currentKeys = keysFor(profile.keyBindings, action),
            onCaptured = { linuxCode ->
                assignKey(linuxCode, action)
                menuForAction = null
            },
            onClear = {
                clearAction(action)
                menuForAction = null
            },
            onDismiss = { menuForAction = null }
        )
    }
}

@Composable
private fun ActionDropdown(selected: PadAction, onSelect: (PadAction) -> Unit) {
    val lang = LocalResolvedLang.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selected.label(lang), fontSize = 13.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PadAction.entries.forEach { a ->
                DropdownMenuItem(
                    text = { Text(a.label(lang), fontSize = 13.sp) },
                    onClick = {
                        onSelect(a)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * キー押下待ちダイアログ。物理キー 1 回またはマウスボタン 1 回で割り当てる。
 * 重複割り当てを許可するため、既存の割り当ては保持したまま追加する。
 */
@Composable
private fun CaptureDialog(
    action: PadAction,
    currentKeys: List<Int>,
    onCaptured: (Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val lang = LocalResolvedLang.current
    val focusRequester = remember { FocusRequester() }
    val lastCaptured by AppState.lastCapturedKey.collectAsStateWithLifecycle()
    val captureSeq by AppState.captureSeq.collectAsStateWithLifecycle()

    // このダイアログを開いた時点の世代番号。これより古い入力は無視する。
    // (変換中に別のキーを押していても誤って割り当てられないようにする)
    val startSeq = remember { AppState.currentCaptureSeq() }
    var assigned by remember { mutableStateOf(false) }

    fun accept(code: Int) {
        if (assigned) return
        assigned = true
        onCaptured(code)
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // 変換中は物理キーが daemon に GRAB されるため Android には届かない。
    // daemon が転送してくる EV 行から取得する。表示後に届いた入力のみ採用する。
    androidx.compose.runtime.LaunchedEffect(captureSeq) {
        if (captureSeq <= startSeq) return@LaunchedEffect
        val pair = lastCaptured ?: return@LaunchedEffect
        accept(pair.second)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.format(lang, "keys.assign_to", action.label(lang))) },
        text = {
            Column {
                Text(
                    Strings.get(lang, "keys.current") + ": " + if (currentKeys.isEmpty())
                        Strings.get(lang, "common.unassigned")
                    else currentKeys.joinToString(", ") { LinuxKeys.label(it, lang) },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.shapes.medium
                        )
                        // focusable() が無いと requestFocus() が効かず、
                        // onPreviewKeyEvent にキーイベントが届かない。
                        .focusRequester(focusRequester)
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                val linux = LinuxKeys.fromAndroidKeycode(event.key.keyCode.toInt())
                                if (linux != null) {
                                    accept(linux)
                                    true
                                } else {
                                    false
                                }
                            } else {
                                false
                            }
                        }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                                    // 変化イベントのみ対象 (押下の瞬間だけ拾う)
                                    if (ev.changes.any { it.pressed && it.previousPressed.not() }) {
                                        val btn = pressedMouseButton(ev.buttons)
                                        if (btn != null) accept(btn)
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        Strings.get(lang, "keys.press_prompt"),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(6.dp))
                HintText(Strings.get(lang, "keys.dialog_hint"))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(Strings.get(lang, "common.close")) }
        },
        dismissButton = {
            if (currentKeys.isNotEmpty()) {
                TextButton(onClick = onClear) { Text(Strings.get(lang, "keys.clear_all")) }
            }
        }
    )
}
