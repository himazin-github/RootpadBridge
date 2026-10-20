package com.rootpad.bridge.ui

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rootpad.bridge.core.AppGraph
import com.rootpad.bridge.core.AppState
import com.rootpad.bridge.core.CurveType
import com.rootpad.bridge.core.LinuxKeys
import com.rootpad.bridge.core.LocalResolvedLang
import com.rootpad.bridge.core.MouseSettings
import com.rootpad.bridge.core.PadAction
import com.rootpad.bridge.core.Strings
import com.rootpad.bridge.ui.components.HintText
import com.rootpad.bridge.ui.components.SectionCard
import com.rootpad.bridge.ui.components.StickPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ln
import kotlin.math.pow

@Composable
fun MouseSettingsScreen() {
    val lang = LocalResolvedLang.current
    val profiles by AppGraph.profiles.profiles.collectAsStateWithLifecycle()
    val activeId by AppGraph.profiles.activeId.collectAsStateWithLifecycle()
    val profile = profiles.firstOrNull { it.id == activeId } ?: return
    val mouse = profile.mouse
    val preview by AppState.mousePreview.collectAsStateWithLifecycle()
    val currentPad by AppState.currentPad.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    fun update(transform: (MouseSettings) -> MouseSettings) {
        AppGraph.profiles.updateActive { p -> p.copy(mouse = transform(p.mouse)) }
        // 変換中の設定変更をエンジンへ即時反映
        scope.launch {
            delay(50)
            com.rootpad.bridge.service.RootPadService.pushProfile()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionCard(title = Strings.get(lang, "mouse.preview")) {
            StickPreview(
                x = preview.first,
                y = preview.second,
                deadzone = mouse.deadzone
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "RX: ${"%.3f".format(preview.first)}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "RY: ${"%.3f".format(preview.second)}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    Strings.get(lang, "mouse.preview_output") +
                        ": (${currentPad.rx}, ${currentPad.ry})",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HintText(Strings.get(lang, "mouse.preview_hint"))
        }

        SectionCard(title = Strings.get(lang, "mouse.sensitivity")) {
            SensitivitySlider(Strings.get(lang, "mouse.sensitivity_x"), mouse.sensitivityX) { v ->
                update { it.copy(sensitivityX = v) }
            }
            SensitivitySlider(Strings.get(lang, "mouse.sensitivity_y"), mouse.sensitivityY) { v ->
                update { it.copy(sensitivityY = v) }
            }
            SwitchRow(Strings.get(lang, "mouse.invert_x"), mouse.invertX) { v ->
                update { it.copy(invertX = v) }
            }
            SwitchRow(Strings.get(lang, "mouse.invert_y"), mouse.invertY) { v ->
                update { it.copy(invertY = v) }
            }
            HintText(Strings.format(lang, "mouse.sensitivity_hint", "%.0f".format(MouseSettings.SENSITIVITY_MAX)))
        }

        SectionCard(title = Strings.get(lang, "mouse.response")) {
            SliderRow(Strings.get(lang, "mouse.deadzone"), mouse.deadzone, 0f, 0.4f, format = { "%.2f".format(it) }) { v ->
                update { it.copy(deadzone = v) }
            }
            SliderRow(Strings.get(lang, "mouse.acceleration"), mouse.acceleration, 0f, 1f, format = { "%.2f".format(it) }) { v ->
                update { it.copy(acceleration = v) }
            }
            SliderRow(Strings.get(lang, "mouse.maxdeflection"), mouse.maxDeflection, 0.1f, 1f, format = { "%.2f".format(it) }) { v ->
                update { it.copy(maxDeflection = v) }
            }
            SliderRow(
                Strings.get(lang, "mouse.idle_return"), mouse.idleReturnMs.toFloat(), 0f, 200f, format = { "%.0f".format(it) }
            ) { v -> update { it.copy(idleReturnMs = v.toLong()) } }
            SliderRow(
                Strings.get(lang, "mouse.decay"), mouse.decayMs.toFloat(), 10f, 500f, format = { "%.0f".format(it) }
            ) { v -> update { it.copy(decayMs = v.toLong()) } }

            Spacer(Modifier.height(4.dp))
            Text(Strings.get(lang, "mouse.curve"), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CurveType.entries.forEach { c ->
                    FilterChip(
                        selected = mouse.curve == c,
                        onClick = { update { it.copy(curve = c) } },
                        label = { Text(c.label(lang), fontSize = 12.sp) }
                    )
                }
            }
        }

        SectionCard(title = Strings.get(lang, "mouse.buttons")) {
            val buttons = listOf(
                LinuxKeys.BTN_LEFT to Strings.get(lang, "mouse.click.left"),
                LinuxKeys.BTN_RIGHT to Strings.get(lang, "mouse.click.right"),
                LinuxKeys.BTN_MIDDLE to Strings.get(lang, "mouse.click.middle"),
                LinuxKeys.BTN_SIDE to Strings.get(lang, "mouse.click.side"),
                LinuxKeys.BTN_EXTRA to Strings.get(lang, "mouse.click.extra")
            )
            buttons.forEach { (code, name) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(name, fontSize = 14.sp)
                    MouseActionDropdown(
                        selected = mouse.buttonBindings[code] ?: PadAction.NONE,
                        onSelect = { action ->
                            update { s ->
                                val m = s.buttonBindings.toMutableMap()
                                if (action == PadAction.NONE) m.remove(code) else m[code] = action
                                s.copy(buttonBindings = m)
                            }
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    format: (Float) -> String = { "%.2f".format(it) }
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 14.sp)
            Text(format(value), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// 引数順不同のヘルパー (label, value, min, max) → SliderRow へ
@Composable
private fun SliderRow(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    format: (Float) -> String = { "%.2f".format(it) },
    onChange: (Float) -> Unit
) = SliderRow(label, value, min..max, onChange, format)

/**
 * 感度用スライダー。線形ではなく指数カーブで SENSITIVITY_MIN〜SENSITIVITY_MAX を
 * マッピングするため、低感度側の細かい調整と高感度側の広い範囲を両立する。
 */
@Composable
private fun SensitivitySlider(
    label: String,
    value: Float,
    onChange: (Float) -> Unit
) {
    val min = MouseSettings.SENSITIVITY_MIN
    val max = MouseSettings.SENSITIVITY_MAX
    // value → スライダー位置 [0,1]
    val pos = (ln(value.coerceIn(min, max) / min) / ln(max / min)).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 14.sp)
            Text("${"%.2f".format(value)}x", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = pos,
            onValueChange = { p ->
                // スライダー位置 → 指数マッピングで実値へ
                val v = min * (max / min).pow(p.coerceIn(0f, 1f))
                onChange(v)
            },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun MouseActionDropdown(selected: PadAction, onSelect: (PadAction) -> Unit) {
    val lang = LocalResolvedLang.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selected.label(lang), fontSize = 13.sp)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
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
