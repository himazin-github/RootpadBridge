package com.rootpad.bridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rootpad.bridge.core.AppGraph
import com.rootpad.bridge.core.AppState
import com.rootpad.bridge.core.LocalResolvedLang
import com.rootpad.bridge.core.Strings
import com.rootpad.bridge.service.RootPadService
import com.rootpad.bridge.ui.components.ChipKind
import com.rootpad.bridge.ui.components.HintText
import com.rootpad.bridge.ui.components.SectionCard
import com.rootpad.bridge.ui.components.StatusChip

@Composable
fun ProfilesScreen() {
    val lang = LocalResolvedLang.current
    val profiles by AppGraph.profiles.profiles.collectAsStateWithLifecycle()
    val activeId by AppGraph.profiles.activeId.collectAsStateWithLifecycle()
    val conversionActive by AppState.conversionActive.collectAsStateWithLifecycle()

    var newNameDialog by remember { mutableStateOf<String?>(null) }      // 変更対象 id
    var createDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionCard(
            title = Strings.get(lang, "profiles.title"),
            trailing = {
                OutlinedButton(onClick = { createDialog = true }) {
                    Text(Strings.get(lang, "common.add"), fontSize = 12.sp)
                }
            }
        ) {
            if (conversionActive) {
                StatusChip(Strings.get(lang, "profiles.switch_hint"), ChipKind.WARN)
                Spacer(Modifier.height(6.dp))
            }
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(profiles, key = { it.id }) { p ->
                    val isActive = p.id == activeId
                    SectionCard(
                        title = p.name,
                        trailing = {
                            if (isActive) StatusChip(Strings.get(lang, "profiles.in_use"), ChipKind.OK)
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isActive,
                                onClick = { AppGraph.profiles.setActive(p.id) }
                            )
                            OutlinedButton(
                                onClick = { newNameDialog = p.id },
                                enabled = !conversionActive
                            ) {
                                Text(Strings.get(lang, "common.rename"), fontSize = 11.sp)
                            }
                            OutlinedButton(onClick = { AppGraph.profiles.duplicate(p.id) }) {
                                Text(Strings.get(lang, "common.duplicate"), fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { deleteDialog = p.id },
                                enabled = profiles.size > 1 && !conversionActive
                            ) {
                                Text(Strings.get(lang, "common.delete"), fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    AppGraph.profiles.resetToDefault(p.id)
                                    RootPadService.pushProfile()
                                },
                                enabled = !conversionActive
                            ) {
                                Text(Strings.get(lang, "common.reset"), fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                Strings.get(lang, "profiles.keys_count") + ": ${p.keyBindings.size}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                Strings.get(lang, "profiles.grab") + ": " +
                                    if (p.grabDevices) Strings.get(lang, "common.enabled")
                                    else Strings.get(lang, "common.disabled"),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                Strings.get(lang, "profiles.autostart") + ": " +
                                    if (p.autoStartOnBoot) Strings.get(lang, "common.enabled")
                                    else Strings.get(lang, "common.disabled"),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (p.id == activeId) {
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(Strings.get(lang, "profiles.grab_switch"), fontSize = 13.sp)
                                Switch(
                                    checked = p.grabDevices,
                                    onCheckedChange = { v ->
                                        AppGraph.profiles.updateActive { it.copy(grabDevices = v) }
                                    }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(Strings.get(lang, "profiles.keylayout_switch"), fontSize = 13.sp)
                                Switch(
                                    checked = p.installKeylayout,
                                    onCheckedChange = { v ->
                                        AppGraph.profiles.updateActive { it.copy(installKeylayout = v) }
                                    }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(Strings.get(lang, "profiles.autostart_switch"), fontSize = 13.sp)
                                Switch(
                                    checked = p.autoStartOnBoot,
                                    onCheckedChange = { v ->
                                        AppGraph.profiles.updateActive { it.copy(autoStartOnBoot = v) }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            HintText(Strings.get(lang, "profiles.hint"))
        }
        Spacer(Modifier.height(24.dp))
    }

    // ---- 名前変更ダイアログ ----
    newNameDialog?.let { id ->
        var text by remember(id) { mutableStateOf(profiles.firstOrNull { it.id == id }?.name ?: "") }
        AlertDialog(
            onDismissRequest = { newNameDialog = null },
            title = { Text(Strings.get(lang, "profiles.rename_title")) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(40) },
                    label = { Text(Strings.get(lang, "profiles.name_label")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) {
                        AppGraph.profiles.rename(id, text.trim())
                    }
                    newNameDialog = null
                }) { Text(Strings.get(lang, "common.ok")) }
            },
            dismissButton = {
                TextButton(onClick = { newNameDialog = null }) {
                    Text(Strings.get(lang, "common.cancel"))
                }
            }
        )
    }

    // ---- 作成ダイアログ ----
    if (createDialog) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { createDialog = false },
            title = { Text(Strings.get(lang, "profiles.add_title")) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(40) },
                    label = { Text(Strings.get(lang, "profiles.name_label")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = text.trim().ifEmpty { Strings.get(lang, "profiles.new_name") }
                    val p = AppGraph.profiles.create(name)
                    AppGraph.profiles.setActive(p.id)
                    createDialog = false
                }) { Text(Strings.get(lang, "common.create")) }
            },
            dismissButton = {
                TextButton(onClick = { createDialog = false }) {
                    Text(Strings.get(lang, "common.cancel"))
                }
            }
        )
    }

    // ---- 削除確認 ----
    deleteDialog?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteDialog = null },
            title = { Text(Strings.get(lang, "profiles.delete_title")) },
            text = {
                Text(
                    Strings.format(
                        lang, "profiles.delete_message",
                        profiles.firstOrNull { it.id == id }?.name ?: ""
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    AppGraph.profiles.delete(id)
                    deleteDialog = null
                }) { Text(Strings.get(lang, "common.delete")) }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialog = null }) {
                    Text(Strings.get(lang, "common.cancel"))
                }
            }
        )
    }
}
