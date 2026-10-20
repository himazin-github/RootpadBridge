package com.rootpad.bridge

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.rootpad.bridge.core.AppGraph
import com.rootpad.bridge.core.Lang
import com.rootpad.bridge.core.LangState
import com.rootpad.bridge.core.LocalResolvedLang
import com.rootpad.bridge.core.Strings
import com.rootpad.bridge.ui.DiagnosticsScreen
import com.rootpad.bridge.ui.HomeScreen
import com.rootpad.bridge.ui.KeyMapScreen
import com.rootpad.bridge.ui.MouseSettingsScreen
import com.rootpad.bridge.ui.ProfilesScreen
import com.rootpad.bridge.ui.theme.RootPadTheme

class MainActivity : ComponentActivity() {

    private val requestNotif =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 通知権限 (Android 13+): フォアグラウンドサービス通知の表示に必要
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        setContent {
            RootPadTheme {
                AppRoot()
            }
        }
    }
}

private val tabKeys = listOf(
    "nav.home" to "⌂",
    "nav.keys" to "🎮",
    "nav.mouse" to "🖱",
    "nav.profiles" to "🗂",
    "nav.diagnostics" to "🩺"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // 言語変更で再コンポーズするため、LangState.lang (mutableStateOf) を読む
    val langSetting = LangState.lang
    val lang = LangState.resolve(langSetting)

    CompositionLocalProvider(LocalResolvedLang provides lang) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "RootPad Bridge",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    actions = { LanguageMenu(langSetting) }
                )
            },
            bottomBar = {
                NavigationBar {
                    tabKeys.forEachIndexed { i, (key, icon) ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Text(icon) },
                            label = { Text(Strings.get(lang, key), fontSize = 11.sp) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (tab) {
                    0 -> HomeScreen()
                    1 -> KeyMapScreen()
                    2 -> MouseSettingsScreen()
                    3 -> ProfilesScreen()
                    4 -> DiagnosticsScreen()
                }
            }
        }
    }
}

/** アプリバー右の言語切替メニュー (自動 / 日本語 / English) */
@Composable
private fun LanguageMenu(current: Lang) {
    var expanded by remember { mutableStateOf(false) }
    val lang = LocalResolvedLang.current

    TextButton(onClick = { expanded = true }) {
        Text(
            when (current) {
                Lang.AUTO -> "🌐 " + Strings.get(lang, "common.language.auto")
                Lang.JA -> "🌐 日本語"
                Lang.EN -> "🌐 English"
            },
            fontSize = 12.sp
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        Lang.entries.forEach { l ->
            DropdownMenuItem(
                text = {
                    val label = when (l) {
                        Lang.AUTO -> Strings.get(lang, "common.language.auto")
                        Lang.JA -> Strings.get(lang, "common.language.ja")
                        Lang.EN -> Strings.get(lang, "common.language.en")
                    }
                    Text(
                        (if (current == l) "● " else "○ ") + label,
                        fontSize = 13.sp
                    )
                },
                onClick = {
                    AppGraph.setLanguage(l)
                    expanded = false
                }
            )
        }
    }
}
