package com.rootpad.bridge.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * UI の表示言語。
 *
 * - [AUTO]: 端末の言語設定に追従する (日本語端末なら日本語、それ以外は英語)
 * - [JA] / [EN]: アプリ内で手動固定
 */
enum class Lang(val code: String) {
    AUTO("auto"),
    JA("ja"),
    EN("en");

    companion object {
        fun fromCode(code: String?): Lang = entries.firstOrNull { it.code == code } ?: AUTO
    }
}

/** 実際に表示に使う言語 (AUTO を解決した結果) */
enum class ResolvedLang { JA, EN }

/** 言語設定の保存・読み出し (SharedPreferences) */
class LangStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): Lang = Lang.fromCode(prefs.getString(KEY_LANG, null))

    fun save(lang: Lang) {
        prefs.edit().putString(KEY_LANG, lang.code).apply()
    }

    companion object {
        private const val PREFS_NAME = "rootpad_prefs"
        private const val KEY_LANG = "ui_language"
    }
}

/** AppState に置くランタイム言語状態の共有 (UI から監視する) */
object LangState {
    private val _lang = mutableStateOf(Lang.AUTO)
    var lang: Lang
        get() = _lang.value
        set(v) { _lang.value = v }

    /** 端末言語から AUTO を解決する */
    fun resolve(lang: Lang): ResolvedLang = when (lang) {
        Lang.JA -> ResolvedLang.JA
        Lang.EN -> ResolvedLang.EN
        Lang.AUTO -> {
            val loc = Locale.getDefault()
            if (loc.language == "ja") ResolvedLang.JA else ResolvedLang.EN
        }
    }

    /** 現在の解決済み言語 */
    fun current(): ResolvedLang = resolve(_lang.value)
}

/** Compose から参照する解決済み言語。 */
val LocalResolvedLang = staticCompositionLocalOf { ResolvedLang.EN }

/**
 * 全 UI 文字列の集中管理。
 *
 * Android の string リソースではなく Kotlin オブジェクトで持つ理由:
 * enum のラベルやデータクラス内の表示名など、`when` 式や
 * 非 Composable な場所からも参照する必要があるため。
 */
object Strings {

    private fun table(lang: ResolvedLang): Map<String, String> =
        if (lang == ResolvedLang.JA) JA else EN

    private val JA: Map<String, String> = buildMap {
        // --- ナビゲーション ---
        put("nav.home", "ホーム")
        put("nav.keys", "キー設定")
        put("nav.mouse", "マウス設定")
        put("nav.profiles", "プロファイル")
        put("nav.diagnostics", "診断")

        // --- 共通語 ---
        put("common.enabled", "有効")
        put("common.disabled", "無効")
        put("common.unassigned", "未割当")
        put("common.none", "なし")
        put("common.close", "閉じる")
        put("common.cancel", "キャンセル")
        put("common.ok", "決定")
        put("common.create", "作成")
        put("common.delete", "削除")
        put("common.reset", "既定に戻す")
        put("common.rename", "名前変更")
        put("common.duplicate", "複製")
        put("common.add", "追加")
        put("common.run", "実行")
        put("common.recheck", "再チェック")
        put("common.remeasure", "再計測")
        put("common.success", "成功")
        put("common.warn", "注意")
        put("common.fail", "失敗")
        put("common.info", "情報")
        put("common.idle", "未実行")
        put("common.unknown", "不明")
        put("common.language", "言語")
        put("common.language.auto", "端末に合わせる")
        put("common.language.ja", "日本語")
        put("common.language.en", "English")

        // --- ホーム ---
        put("home.conversion", "入力変換")
        put("home.profile", "プロファイル")
        put("home.start", "変換を開始")
        put("home.stop", "変換を停止")
        put("home.grab_hint", "変換中は物理キーボード/マウスをゲームが直接受け取れません (GRAB: %s)。停止はこの画面または通知から行えます。")
        put("home.root_status", "Root 状態")
        put("home.root_ok", "Root 利用可")
        put("home.root_ng", "Root 不可")
        put("home.unchecked", "未確認")
        put("home.exec_result", "実行結果")
        put("home.reason", "原因")
        put("home.root_hint", "Magisk 等の Root 環境が無い場合、このアプリの全機能は利用できません。uinput と /dev/input のアクセスは root 権限が必須です。")
        put("home.virtual_pad", "仮想コントローラー")
        put("home.stopped", "停止中")
        put("home.starting", "起動中...")
        put("home.running", "稼働中")
        put("home.error", "エラー")
        put("home.pad_devicename", "デバイス名")
        put("home.pad_node", "ノード")
        put("home.pad_state", "状態")
        put("home.pad_not_created", "仮想パッド未生成")
        put("home.android_recognized", "Android が認識済み")
        put("home.android_not_recognized", "Android 未認識")
        put("home.recognized_name", "認識名")
        put("home.vid_pid", "識別子")
        put("home.classification", "分類")
        put("home.unclassified", "ゲームパッド未分類")
        put("home.axes", "軸")
        put("home.android_hint", "uinput デバイスは生成されたものの、Android の InputReader が 045E:028E をまだ拾っていないか、ゲームパッドとして分類できていません。診断画面で確認してください。")
        put("home.devices", "接続デバイス (daemon 観測)")
        put("home.no_devices", "キーボード/マウスが検出されていません")
        put("home.grabbed", "独占中")
        put("home.normal", "通常")
        put("home.key_events", "キー入力")
        put("home.mouse_events", "マウス")
        put("home.last_error", "最後のエラー")
        put("home.logs", "ログ (最新10件)")
        put("home.no_logs", "まだログがありません")

        // --- キー設定 ---
        put("keys.mouse_buttons", "マウスボタンの割り当て")
        put("keys.mouse_hint", "左クリック=RT、右クリック=LT が既定です。FPS では射照準の割り当てを好みに合わせて変更してください。")
        put("keys.wheel", "マウスホイールの割り当て")
        put("keys.wheel_hint", "ホイール回転を任意のアクションへ割り当てられます (既定: 上下=十字キー上下)。1 ノッチごとに短時間の押下としてゲームへ送信されます。")
        put("keys.list", "キー割り当て一覧")
        put("keys.list_hint", "行をタップしてキーを設定できます。同じキーを複数のアクションに割り当て可能 (同時押し) です。")
        put("keys.converting", "変換中: 物理キーはそのままキャプチャできます")
        put("keys.assign_to", "%s に割り当てる")
        put("keys.current", "現在")
        put("keys.press_prompt", "キーまたはマウスボタンを押してください")
        put("keys.dialog_hint", "既存の割り当ては保持され、追加されます (同じキーを複数のアクションに割り当て可能)。Esc や Tab なども割り当て可能です。自動リピートは無視されます。")
        put("keys.clear_all", "すべて解除")
        put("keys.stick_target", "割り当てる方向を選んでください")
        put("keys.stick_left", "左スティック")
        put("keys.stick_right", "右スティック")
        put("keys.mouse.left", "マウス左")
        put("keys.mouse.right", "マウス右")
        put("keys.mouse.middle", "マウス中")
        put("keys.mouse.side", "マウス側面4")
        put("keys.mouse.extra", "マウス側面5")

        // --- マウス設定 ---
        put("mouse.preview", "スティックプレビュー")
        put("mouse.preview_output", "出力値")
        put("mouse.preview_hint", "物理マウスを動かすと右スティックの値が更新されます。停止後は自動で中央に戻ります。")
        put("mouse.sensitivity", "感度")
        put("mouse.sensitivity_x", "水平感度 (X)")
        put("mouse.sensitivity_y", "垂直感度 (Y)")
        put("mouse.invert_x", "X 軸を反転")
        put("mouse.invert_y", "Y 軸を反転")
        put("mouse.sensitivity_hint", "感度は最大 %s 倍まで設定できます (指数カーブで調整)。FPS では「上に動かす = 視点が上」が一般的なため Y 反転が既定で有効です。")
        put("mouse.response", "応答特性")
        put("mouse.deadzone", "デッドゾーン")
        put("mouse.acceleration", "加速度")
        put("mouse.maxdeflection", "最大振幅")
        put("mouse.idle_return", "停止→復帰待ち (ms)")
        put("mouse.decay", "減衰時間 (ms)")
        put("mouse.curve", "感度カーブ")
        put("mouse.buttons", "マウスボタンの割り当て")
        put("mouse.click.left", "左クリック")
        put("mouse.click.right", "右クリック")
        put("mouse.click.middle", "中クリック")
        put("mouse.click.side", "側面ボタン4")
        put("mouse.click.extra", "側面ボタン5")

        // --- プロファイル ---
        put("profiles.title", "プロファイル")
        put("profiles.in_use", "使用中")
        put("profiles.switch_hint", "変換中のプロファイル切替は次回開始時に反映されます")
        put("profiles.keys_count", "キー")
        put("profiles.grab", "GRAB")
        put("profiles.autostart", "起動時復旧")
        put("profiles.grab_switch", "物理デバイスを独占 (GRAB)")
        put("profiles.keylayout_switch", "Xbox 用キーレイアウトを導入")
        put("profiles.autostart_switch", "再起動後に自動再開")
        put("profiles.hint", "切替・複製・削除は即座に保存されます。既定に戻すとキーマップとマウス設定が初期値へ戻ります。")
        put("profiles.rename_title", "名前を変更")
        put("profiles.add_title", "プロファイルを追加")
        put("profiles.name_label", "プロファイル名")
        put("profiles.new_name", "新規プロファイル")
        put("profiles.delete_title", "プロファイルを削除")
        put("profiles.delete_message", "「%s」を削除します。この操作は取り消せません。")
        put("profiles.copy_suffix", " のコピー")
        put("profile.default_name", "既定")

        // --- 診断 ---
        put("diag.system", "システム診断")
        put("diag.root", "Root 権限")
        put("diag.root_fail", "取得失敗")
        put("diag.root_remedy", "Magisk の Superuser 許可で「RootPad Bridge」を許可してください。su が無い端末では本機能は利用できません。")
        put("diag.uinput", "/dev/uinput (カーネル機能)")
        put("diag.uinput_ok", "アクセス可")
        put("diag.uinput_fail", "root でのオープン失敗")
        put("diag.uinput_misc_exists", "/sys/class/misc/uinput は存在 (未接続時に確認)")
        put("diag.uinput_misc_missing", "カーネルに uinput が存在しない可能性")
        put("diag.uinput_unknown", "変換を開始すると判明します")
        put("diag.uinput_remedy", "カーネルの CONFIG_INPUT_UINPUT が無効、または SELinux が拒否しています。対応カーネルの ROM を使うか、Magisk モジュールでポリシーを緩和する必要があります。")
        put("diag.selftest", "uinput 生成テスト")
        put("diag.selftest_not_run", "未実行")
        put("diag.selftest_after_start", "変換開始後に実行できます")
        put("diag.selftest_ok", "生成・書き込み・破棄に成功")
        put("diag.selftest_running", "実行中...")
        put("diag.selftest_no_service", "サービス未起動のため実行できません")
        put("diag.selftest_comm_error", "通信エラー: %s")
        put("diag.virtual_pad", "仮想ゲームパッド (稼働時)")
        put("diag.not_created", "未生成")
        put("diag.android_recognition", "Android のゲームパッド認識")
        put("diag.recognition_after_start", "変換開始後に確認")
        put("diag.recognition_not_found", "InputDevice に未検出")
        put("diag.recognition_both", "GAMEPAD + JOYSTICK として認識 (%s)")
        put("diag.recognition_gamepad_only", "GAMEPAD として認識 (%s) ※JOYSTICK未分類")
        put("diag.recognition_joystick_only", "JOYSTICK のみ認識 (%s) ※GAMEPAD未分類")
        put("diag.recognition_unclassified", "デバイスはあるがゲームパッド未分類 (name=%s)")
        put("diag.recognition_remedy", "uinput デバイスの登録に時間がかかっているか、Android 側が認識を拒否しています。数十秒待つか、ゲームを再起動してください。")
        put("diag.recognition_keylayout_hint", "対策: プロファイルで「Xbox 用キーレイアウトを導入」を有効にして再起動してください。Vendor_045E_Product_028E.kl/.idc が InputReader の分類を助けます。")
        put("diag.keylayout", "Xbox キーレイアウト導入")
        put("diag.keylayout_ok", "/data/system/devices/keylayout に導入済み")
        put("diag.keylayout_fail", "導入失敗 (Root 書き込み不可)")
        put("diag.keylayout_skipped", "未実施 (設定で無効、または未開始)")
        put("diag.device_list", "入力デバイス一覧")
        put("diag.device_list_empty", "daemon が観測したキーボード/マウスは未検出")
        put("diag.grab", "GRAB")
        put("diag.released", "開放")
        put("diag.android_devices", "Android InputDevice (参考)")
        put("diag.event_counter", "入力イベント")
        put("diag.event_counter_value", "キー: %s 件 / マウス移動: %s 件")
        put("diag.current_output", "現在の出力")
        put("diag.kernel", "カーネル")
        put("diag.selinux", "SELinux")
        put("diag.not_obtained", "未取得")
        put("diag.ctx_unknown", "ctx不明")
        put("diag.selinux_remedy", "enforcing では root でも uinput が拒否される場合があります。動作しない場合は Magisk でデーモンのドメインを permissive 化してください。")
        put("diag.errno_13", "権限拒否。SELinux が uinput を拒否しています。")
        put("diag.errno_2", "ノードが存在しません。カーネルに uinput がありません。")
        put("diag.errno_22", "このカーネルの uinput API に非対応です。")
        put("diag.errno_other", "詳細はログ参照")
        put("diag.remedy_prefix", "対処")

        // --- トラブルシューティング ---
        put("trouble.title", "エラー原因と対処方法")
        put("trouble.q1", "変換を開始しても物理キーが反応しない")
        put("trouble.a1", "GRAB が有効な場合、物理入力はデーモンだけが受け取ります。ゲーム側がゲームパッド入力を処理しているか確認してください。")
        put("trouble.q2", "uinput の生成に失敗 (errno=2)")
        put("trouble.a2", "カーネルに CONFIG_INPUT_UINPUT がありません。カーネル/ROM の変更が必要です。")
        put("trouble.q3", "uinput の生成に失敗 (errno=13)")
        put("trouble.a3", "SELinux のポリシー拒否です。Magisk の su コンテキストが permissive か確認してください。")
        put("trouble.q4", "ゲームがパッドを認識しない")
        put("trouble.a4", "「Xbox 用キーレイアウトを導入」を有効にして変換を再開始し、ゲームを再起動してください。一部のゲームは独自の入力スタックを持つため完全互換は保証できません。")
        put("trouble.q5", "キーが押されたままになる")
        put("trouble.a5", "デーモンは 8 秒無通信で自動的に全入力を解除します (watchdog)。アプリの「変換を停止」も必ず全入力をリセットします。")
        put("trouble.q6", "マウスの視点が戻ってこない")
        put("trouble.a6", "マウス設定で「停止→復帰待ち」「減衰時間」を短くしてください。")

        // --- 通知 ---
        put("notif.channel_name", "入力変換サービス")
        put("notif.channel_desc", "入力変換の実行状態を表示します")
        put("notif.title", "入力変換を実行中")
        put("notif.text", "キーボード・マウス → ゲームパッド")
        put("notif.stop", "停止")
        put("notif.starting", "起動中...")
        put("notif.converting", "変換中: キーボード/マウス → ゲームパッド")
        put("notif.stopped", "停止: %s")

        // --- ボタン/アクション表示 ---
        put("btn.A", "A")
        put("btn.B", "B")
        put("btn.X", "X")
        put("btn.Y", "Y")
        put("btn.LB", "LB")
        put("btn.RB", "RB")
        put("btn.LT", "LT (左トリガー)")
        put("btn.RT", "RT (右トリガー)")
        put("btn.VIEW", "View")
        put("btn.MENU", "Menu")
        put("btn.GUIDE", "Guide")
        put("btn.LS_PRESS", "左スティック押し込み")
        put("btn.RS_PRESS", "右スティック押し込み")
        put("btn.DPAD_UP", "十字 上")
        put("btn.DPAD_DOWN", "十字 下")
        put("btn.DPAD_LEFT", "十字 左")
        put("btn.DPAD_RIGHT", "十字 右")
        put("btn.LS_UP", "左スティック 上")
        put("btn.LS_DOWN", "左スティック 下")
        put("btn.LS_LEFT", "左スティック 左")
        put("btn.LS_RIGHT", "左スティック 右")
        put("btn.RS_UP", "右スティック 上")
        put("btn.RS_DOWN", "右スティック 下")
        put("btn.RS_LEFT", "右スティック 左")
        put("btn.RS_RIGHT", "右スティック 右")

        // --- カーブ ---
        put("curve.LINEAR", "リニア")
        put("curve.SQUARE", "スクエア (精密照準)")
        put("curve.SMOOTH", "スムーズ (立ち上がり緩やか)")

        // --- ホイール ---
        put("scroll.UP", "ホイール 上")
        put("scroll.DOWN", "ホイール 下")
        put("scroll.LEFT", "ホイール 左 (チルト)")
        put("scroll.RIGHT", "ホイール 右 (チルト)")

        // --- キー名の上書き (修飾キー/マウス) ---
        put("key.LEFTCTRL", "左Ctrl")
        put("key.RIGHTCTRL", "右Ctrl")
        put("key.LEFTSHIFT", "左Shift")
        put("key.RIGHTSHIFT", "右Shift")
        put("key.LEFTALT", "左Alt")
        put("key.RIGHTALT", "右Alt")
        put("key.LEFTMETA", "左Meta")
        put("key.RIGHTMETA", "右Meta")
        put("key.mouse.left", "マウス左")
        put("key.mouse.right", "マウス右")
        put("key.mouse.middle", "マウス中")
        put("key.mouse.side", "マウス側面4")
        put("key.mouse.extra", "マウス側面5")

        // --- ログ ---
        put("log.device_connected", "入力デバイス接続")
        put("log.device_removed", "入力デバイス切断")
    }

    private val EN: Map<String, String> = buildMap {
        // --- Navigation ---
        put("nav.home", "Home")
        put("nav.keys", "Keys")
        put("nav.mouse", "Mouse")
        put("nav.profiles", "Profiles")
        put("nav.diagnostics", "Diagnostics")

        // --- Common ---
        put("common.enabled", "On")
        put("common.disabled", "Off")
        put("common.unassigned", "Unassigned")
        put("common.none", "None")
        put("common.close", "Close")
        put("common.cancel", "Cancel")
        put("common.ok", "OK")
        put("common.create", "Create")
        put("common.delete", "Delete")
        put("common.reset", "Reset to default")
        put("common.rename", "Rename")
        put("common.duplicate", "Duplicate")
        put("common.add", "Add")
        put("common.run", "Run")
        put("common.recheck", "Re-check")
        put("common.remeasure", "Refresh")
        put("common.success", "OK")
        put("common.warn", "Warn")
        put("common.fail", "Fail")
        put("common.info", "Info")
        put("common.idle", "Idle")
        put("common.unknown", "Unknown")
        put("common.language", "Language")
        put("common.language.auto", "System default")
        put("common.language.ja", "日本語")
        put("common.language.en", "English")

        // --- Home ---
        put("home.conversion", "Input conversion")
        put("home.profile", "Profile")
        put("home.start", "Start conversion")
        put("home.stop", "Stop conversion")
        put("home.grab_hint", "While converting, games cannot receive the physical keyboard/mouse directly (GRAB: %s). Stop from this screen or the notification.")
        put("home.root_status", "Root status")
        put("home.root_ok", "Root available")
        put("home.root_ng", "Root unavailable")
        put("home.unchecked", "Not checked")
        put("home.exec_result", "Result")
        put("home.reason", "Reason")
        put("home.root_hint", "Without a root environment such as Magisk, none of this app's features work. Access to uinput and /dev/input requires root.")
        put("home.virtual_pad", "Virtual controller")
        put("home.stopped", "Stopped")
        put("home.starting", "Starting...")
        put("home.running", "Running")
        put("home.error", "Error")
        put("home.pad_devicename", "Device name")
        put("home.pad_node", "Node")
        put("home.pad_state", "State")
        put("home.pad_not_created", "Virtual pad not created")
        put("home.android_recognized", "Recognized by Android")
        put("home.android_not_recognized", "Not recognized by Android")
        put("home.recognized_name", "Name")
        put("home.vid_pid", "VID:PID")
        put("home.classification", "Class")
        put("home.unclassified", "Not classified as gamepad")
        put("home.axes", "Axes")
        put("home.android_hint", "The uinput device exists but Android's InputReader has not picked up 045E:028E yet, or did not classify it as a gamepad. Check the Diagnostics screen.")
        put("home.devices", "Connected devices (daemon)")
        put("home.no_devices", "No keyboard/mouse detected")
        put("home.grabbed", "Grabbed")
        put("home.normal", "Normal")
        put("home.key_events", "Keys")
        put("home.mouse_events", "Mouse")
        put("home.last_error", "Last error")
        put("home.logs", "Log (last 10)")
        put("home.no_logs", "No logs yet")

        // --- Keys ---
        put("keys.mouse_buttons", "Mouse button mapping")
        put("keys.mouse_hint", "Defaults: left click = RT, right click = LT. For FPS games adjust the aim binding as you prefer.")
        put("keys.wheel", "Mouse wheel mapping")
        put("keys.wheel_hint", "Assign wheel rotation to any action (default: up/down = D-pad up/down). Each notch is sent to the game as a short press.")
        put("keys.list", "Key bindings")
        put("keys.list_hint", "Tap a row to assign a key. The same key can be assigned to multiple actions (pressed simultaneously).")
        put("keys.converting", "Converting: physical keys can be captured directly")
        put("keys.assign_to", "Assign to %s")
        put("keys.current", "Current")
        put("keys.press_prompt", "Press a key or mouse button")
        put("keys.dialog_hint", "Existing bindings are kept and the new one is added (a key can map to multiple actions). Esc, Tab, etc. can also be assigned. Auto-repeat is ignored.")
        put("keys.clear_all", "Clear all")
        put("keys.stick_target", "Choose a direction to assign")
        put("keys.stick_left", "Left stick")
        put("keys.stick_right", "Right stick")
        put("keys.mouse.left", "Mouse left")
        put("keys.mouse.right", "Mouse right")
        put("keys.mouse.middle", "Mouse middle")
        put("keys.mouse.side", "Mouse side 4")
        put("keys.mouse.extra", "Mouse side 5")

        // --- Mouse ---
        put("mouse.preview", "Stick preview")
        put("mouse.preview_output", "Output")
        put("mouse.preview_hint", "Moving the physical mouse updates the right stick. It returns to center automatically after you stop.")
        put("mouse.sensitivity", "Sensitivity")
        put("mouse.sensitivity_x", "Horizontal sensitivity (X)")
        put("mouse.sensitivity_y", "Vertical sensitivity (Y)")
        put("mouse.invert_x", "Invert X axis")
        put("mouse.invert_y", "Invert Y axis")
        put("mouse.sensitivity_hint", "Sensitivity can go up to %sx (exponential curve). For FPS games, moving the mouse up usually aims up, so Y-invert is on by default.")
        put("mouse.response", "Response")
        put("mouse.deadzone", "Deadzone")
        put("mouse.acceleration", "Acceleration")
        put("mouse.maxdeflection", "Max deflection")
        put("mouse.idle_return", "Idle before return (ms)")
        put("mouse.decay", "Decay time (ms)")
        put("mouse.curve", "Sensitivity curve")
        put("mouse.buttons", "Mouse button mapping")
        put("mouse.click.left", "Left click")
        put("mouse.click.right", "Right click")
        put("mouse.click.middle", "Middle click")
        put("mouse.click.side", "Side button 4")
        put("mouse.click.extra", "Side button 5")

        // --- Profiles ---
        put("profiles.title", "Profiles")
        put("profiles.in_use", "In use")
        put("profiles.switch_hint", "Switching profiles while converting takes effect on the next start")
        put("profiles.keys_count", "Keys")
        put("profiles.grab", "GRAB")
        put("profiles.autostart", "Restore on boot")
        put("profiles.grab_switch", "Exclusive grab of physical devices")
        put("profiles.keylayout_switch", "Install Xbox keylayout")
        put("profiles.autostart_switch", "Auto-resume after reboot")
        put("profiles.hint", "Switch, duplicate and delete are saved immediately. Reset restores the default key map and mouse settings.")
        put("profiles.rename_title", "Rename profile")
        put("profiles.add_title", "Add profile")
        put("profiles.name_label", "Profile name")
        put("profiles.new_name", "New profile")
        put("profiles.delete_title", "Delete profile")
        put("profiles.delete_message", "Delete \"%s\"? This cannot be undone.")
        put("profiles.copy_suffix", " copy")
        put("profile.default_name", "Default")

        // --- Diagnostics ---
        put("diag.system", "System diagnostics")
        put("diag.root", "Root permission")
        put("diag.root_fail", "Failed")
        put("diag.root_remedy", "Allow \"RootPad Bridge\" in Magisk Superuser. On devices without su this app cannot work.")
        put("diag.uinput", "/dev/uinput (kernel)")
        put("diag.uinput_ok", "Accessible")
        put("diag.uinput_fail", "Open as root failed")
        put("diag.uinput_misc_exists", "/sys/class/misc/uinput exists (check while disconnected)")
        put("diag.uinput_misc_missing", "Kernel may not provide uinput")
        put("diag.uinput_unknown", "Determined once conversion starts")
        put("diag.uinput_remedy", "CONFIG_INPUT_UINPUT is disabled or SELinux denies it. Use a ROM with a supporting kernel, or relax the policy with a Magisk module.")
        put("diag.selftest", "uinput creation test")
        put("diag.selftest_not_run", "Not run")
        put("diag.selftest_after_start", "Available after conversion starts")
        put("diag.selftest_ok", "Create / write / destroy succeeded")
        put("diag.selftest_running", "Running...")
        put("diag.selftest_no_service", "Cannot run: service not started")
        put("diag.selftest_comm_error", "Communication error: %s")
        put("diag.virtual_pad", "Virtual gamepad (while running)")
        put("diag.not_created", "Not created")
        put("diag.android_recognition", "Android gamepad recognition")
        put("diag.recognition_after_start", "Checked after conversion starts")
        put("diag.recognition_not_found", "Not found in InputDevice")
        put("diag.recognition_both", "Recognized as GAMEPAD + JOYSTICK (%s)")
        put("diag.recognition_gamepad_only", "Recognized as GAMEPAD (%s), JOYSTICK unclassified")
        put("diag.recognition_joystick_only", "Recognized as JOYSTICK only (%s), GAMEPAD unclassified")
        put("diag.recognition_unclassified", "Device present but not classified as gamepad (name=%s)")
        put("diag.recognition_remedy", "Device registration may still be in progress, or Android rejected it. Wait a few tens of seconds or restart the game.")
        put("diag.recognition_keylayout_hint", "Fix: enable \"Install Xbox keylayout\" in the profile, then restart. Vendor_045E_Product_028E.kl/.idc helps InputReader classify it.")
        put("diag.keylayout", "Xbox keylayout installed")
        put("diag.keylayout_ok", "Installed under /data/system/devices/keylayout")
        put("diag.keylayout_fail", "Install failed (root write denied)")
        put("diag.keylayout_skipped", "Not attempted (disabled or not started)")
        put("diag.device_list", "Input devices")
        put("diag.device_list_empty", "No keyboard/mouse observed by the daemon")
        put("diag.grab", "GRAB")
        put("diag.released", "Open")
        put("diag.android_devices", "Android InputDevice (reference)")
        put("diag.event_counter", "Input events")
        put("diag.event_counter_value", "Keys: %s / mouse moves: %s")
        put("diag.current_output", "Current output")
        put("diag.kernel", "Kernel")
        put("diag.selinux", "SELinux")
        put("diag.not_obtained", "Not obtained")
        put("diag.ctx_unknown", "ctx unknown")
        put("diag.selinux_remedy", "Under enforcing, uinput may be denied even for root. If it does not work, set the daemon domain to permissive with Magisk.")
        put("diag.errno_13", "Permission denied. SELinux is blocking uinput.")
        put("diag.errno_2", "Node does not exist. The kernel has no uinput.")
        put("diag.errno_22", "This kernel's uinput API is unsupported.")
        put("diag.errno_other", "See logs for details")
        put("diag.remedy_prefix", "Fix")

        // --- Troubleshooting ---
        put("trouble.title", "Causes and fixes")
        put("trouble.q1", "Physical keys do nothing after starting")
        put("trouble.a1", "With GRAB enabled, only the daemon receives physical input. Make sure the game is processing gamepad input.")
        put("trouble.q2", "uinput creation failed (errno=2)")
        put("trouble.a2", "The kernel lacks CONFIG_INPUT_UINPUT. A different kernel/ROM is required.")
        put("trouble.q3", "uinput creation failed (errno=13)")
        put("trouble.a3", "SELinux policy denial. Check that Magisk's su context is permissive.")
        put("trouble.q4", "The game does not recognize the pad")
        put("trouble.a4", "Enable \"Install Xbox keylayout\", restart conversion and restart the game. Some games use their own input stack, so full compatibility is not guaranteed.")
        put("trouble.q5", "A key stays pressed")
        put("trouble.a5", "The daemon releases all input after 8 seconds without communication (watchdog). \"Stop conversion\" also always resets all input.")
        put("trouble.q6", "The mouse view does not return to center")
        put("trouble.a6", "Shorten \"Idle before return\" and \"Decay time\" in the mouse settings.")

        // --- Notification ---
        put("notif.channel_name", "Input conversion service")
        put("notif.channel_desc", "Shows the running state of input conversion")
        put("notif.title", "Input conversion running")
        put("notif.text", "Keyboard / mouse → gamepad")
        put("notif.stop", "Stop")
        put("notif.starting", "Starting...")
        put("notif.converting", "Converting: keyboard / mouse → gamepad")
        put("notif.stopped", "Stopped: %s")

        // --- Button / action labels ---
        put("btn.A", "A")
        put("btn.B", "B")
        put("btn.X", "X")
        put("btn.Y", "Y")
        put("btn.LB", "LB")
        put("btn.RB", "RB")
        put("btn.LT", "LT (left trigger)")
        put("btn.RT", "RT (right trigger)")
        put("btn.VIEW", "View")
        put("btn.MENU", "Menu")
        put("btn.GUIDE", "Guide")
        put("btn.LS_PRESS", "Left stick press")
        put("btn.RS_PRESS", "Right stick press")
        put("btn.DPAD_UP", "D-pad up")
        put("btn.DPAD_DOWN", "D-pad down")
        put("btn.DPAD_LEFT", "D-pad left")
        put("btn.DPAD_RIGHT", "D-pad right")
        put("btn.LS_UP", "Left stick up")
        put("btn.LS_DOWN", "Left stick down")
        put("btn.LS_LEFT", "Left stick left")
        put("btn.LS_RIGHT", "Left stick right")
        put("btn.RS_UP", "Right stick up")
        put("btn.RS_DOWN", "Right stick down")
        put("btn.RS_LEFT", "Right stick left")
        put("btn.RS_RIGHT", "Right stick right")

        // --- Curves ---
        put("curve.LINEAR", "Linear")
        put("curve.SQUARE", "Square (fine aim)")
        put("curve.SMOOTH", "Smooth (soft onset)")

        // --- Wheel ---
        put("scroll.UP", "Wheel up")
        put("scroll.DOWN", "Wheel down")
        put("scroll.LEFT", "Wheel left (tilt)")
        put("scroll.RIGHT", "Wheel right (tilt)")

        // --- Key name overrides (modifiers/mouse) ---
        put("key.LEFTCTRL", "Left Ctrl")
        put("key.RIGHTCTRL", "Right Ctrl")
        put("key.LEFTSHIFT", "Left Shift")
        put("key.RIGHTSHIFT", "Right Shift")
        put("key.LEFTALT", "Left Alt")
        put("key.RIGHTALT", "Right Alt")
        put("key.LEFTMETA", "Left Meta")
        put("key.RIGHTMETA", "Right Meta")
        put("key.mouse.left", "Mouse left")
        put("key.mouse.right", "Mouse right")
        put("key.mouse.middle", "Mouse middle")
        put("key.mouse.side", "Mouse side 4")
        put("key.mouse.extra", "Mouse side 5")

        // --- Logs ---
        put("log.device_connected", "Input device connected")
        put("log.device_removed", "Input device removed")
    }

    /**
     * 文字列を取得する。未定義キーはキー名をそのまま返す (開発時に気づけるように)。
     */
    fun get(lang: ResolvedLang, key: String): String =
        table(lang)[key] ?: key

    /** プレースホルダ (%s) を順に置換する */
    fun format(lang: ResolvedLang, key: String, vararg args: Any?): String {
        var s = get(lang, key)
        for (a in args) {
            val idx = s.indexOf("%s")
            if (idx < 0) break
            s = s.substring(0, idx) + a.toString() + s.substring(idx + 2)
        }
        return s
    }
}
