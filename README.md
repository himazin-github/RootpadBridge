# RootPad Bridge

**物理キーボード・マウスを Xbox 互換の仮想ゲームパッドに変換する Android アプリ (root / Magisk 必須)**

[![Platform](https://img.shields.io/badge/platform-Android%2012%2B%20(API%2031%2B)-3DDC84?logo=android&logoColor=white)](#動作環境)
[![Root](https://img.shields.io/badge/root-Magisk%20required-E74C3C)](#動作環境)
[![AArch64](https://img.shields.io/badge/ABI-arm64--v8a-blue)](#動作環境)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?logo=kotlin&logoColor=white)](#技術スタック)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202026.06.01-4285F4?logo=jetpackcompose&logoColor=white)](#技術スタック)
[![Tests](https://img.shields.io/badge/tests-58%20passing-success)](#検証状況-正直な記載)
[![License](https://img.shields.io/badge/license-MIT-blue)](#ライセンス)

**日本語** | [English](README.en.md)

</div>

---

> [!WARNING]
> **このアプリは root 権限を必要とします。** `/dev/input` の独占取得 (`EVIOCGRAB`)、`/dev/uinput`
> への仮想デバイス生成、`/data/system/devices/` へのキーレイアウト導入を行うため、
> **root なし (Shizuku 含む) では動作しません。**
> Shizuku は shell (uid 2000) 権限しか付与できないため、これらの操作には不十分です。
> 端末保証の対象外となる操作を含みます。自己責任で使用してください。

---

## 目次

- [これは何か](#これは何か)
- [スクリーンショット](#スクリーンショット)
- [動作環境](#動作環境)
- [主な機能](#主な機能)
- [アーキテクチャ](#アーキテクチャ)
- [インストール](#インストール)
- [ビルド](#ビルド)
- [使い方](#使い方)
- [多言語対応 (日本語 / English)](#多言語対応-日本語-english)
- [プロジェクト構成](#プロジェクト構成)
- [技術スタック](#技術スタック)
- [トラブルシューティング / FAQ](#トラブルシューティング-faq)
- [検証状況 (正直な記載)](#検証状況-正直な記載)
- [既知の制限](#既知の制限)
- [コントリビュート](#コントリビュート)
- [ライセンス](#ライセンス)

---

## これは何か

RootPad Bridge は、root 権限 (Magisk) を持つ Android 端末で
**物理キーボード / マウスの入力を Xbox 互換の仮想ゲームパッドへ変換**するアプリです。
ゲーム側は通常のゲームパッドとして認識するため、
キーボード&マウスに対応していないゲームでも、PC 感覚の操作を持ち込めます。

- パッケージ: `com.rootpad.bridge`
- 変換ロジックは **純 Kotlin** に分離し、単体テスト済み
- **インターネット権限なし** (完全オフライン・テレメトリなし)

> 本アプリは Magisk 等で root 化済みの端末を対象とした、上級者向けツールです。

---

## スクリーンショット

<!-- TODO: スクリーンショットを docs/screenshots/ に追加して、下記のコメントを外してください -->

> スクリーンショットは準備中です。
>
> <!--
> | ホーム | キー設定 | マウス設定 |
> |---|---|---|
> | ![Home](docs/screenshots/home.png) | ![Keys](docs/screenshots/keys.png) | ![Mouse](docs/screenshots/mouse.png) |
>
> | プロファイル | 診断 |
> |---|---|
> | ![Profiles](docs/screenshots/profiles.png) | ![Diagnostics](docs/screenshots/diagnostics.png) |
> -->

---

## 動作環境

| 項目 | 要件 |
|---|---|
| OS | Android 12 (API 31) 以上 / 実機で確認したのは Android 15 |
| アーキテクチャ | **arm64-v8a** のみ |
| Root | **Magisk 等による root が必須** |
| カーネル | `CONFIG_INPUT_UINPUT` が有効で `/dev/uinput` にアクセスできること |
| SELinux | uinput へのアクセスが許可されていること |
| 入力 | USB / Bluetooth の物理キーボード・マウス |

**root が必須な理由**: 本アプリは以下を直接行います。

- `/dev/input/event*` を `EVIOCGRAB` で独占取得 (二重入力を防ぐため)
- `/dev/uinput` に仮想ゲームパッドを生成
- `/data/system/devices/` に Xbox 用のキーレイアウト (`.kl` / `.idc`) を導入

これらはすべて root (uid 0) 権限を要求するため、**Shizuku では代替できません**
(Shizuku が付与するのは shell / uid 2000 権限のみ)。

---

## 主な機能

- **Root / uinput 診断**: root 取得状況、`/dev/uinput` の有無、仮想デバイス生成テスト、
  Android 側 (`InputDevice` API) がどう認識したかを表示。**偽の成功は表示しません。**
- **仮想 Xbox パッド**: A/B/X/Y、LB/RB、LT/RT、Menu/View、左右スティック + 押し込み、十字キー。
- **既定キー割り当て** (すべて変更可):

  | キー | 割り当て | キー | 割り当て |
  |---|---|---|---|
  | W/A/S/D | 左スティック | Space | A |
  | Q | B | E | X |
  | R | Y | 左Shift / 1 | LB |
  | F / 2 | RB | Tab | View |
  | Esc | Menu | 左Ctrl | L3 |
  | Z | R3 | G | Guide |
  | 矢印キー | 十字キー | | |

- **キーの重複割り当て**: 1 つのキーを複数のアクションへ割り当て可能 (同時押し)。
  例: Space を A と LB の両方に割り当てる。既存の割り当ては保持されたまま追加されます。
- **マウスホイール割り当て**: ホイール上下・左右 (チルト) を任意のアクションへ割り当て可能
  (既定: 上=十字キー上、下=十字キー下)。1 ノッチを短時間の押下としてゲームへ送信します。
- **マウス → 右スティック**: 感度 (X/Y 独立、**0.05〜50 倍**を指数カーブで調整)、軸反転、
  デッドゾーン、カーブ (リニア/スクエア/スムーズ)、加速度、最大傾き制限、移動停止後の自動復帰。
  ライブプレビュー付き。マウスボタンの割り当ても変更可 (既定: 左=RT, 右=LT, 側面4=LB, 側面5=RB)。
- **プロファイル**: 作成 / 名前変更 / 複製 / 削除 / 既定に戻す。変更は即時保存。
- **フォアグラウンドサービス**: 通知から開始/停止。サービス停止時は必ず入力リセット。
  デーモン異常終了を検知して自動再起動 (最大 3 回)。
- **再起動後の自動再開** (プロファイルで有効化した場合のみ)。

---

## アーキテクチャ

```
物理キーボード/マウス
        │  /dev/input/event*
        ▼
rootpad_d  (C, root で動作する常駐デーモン)
  ├─ /dev/uinput へ Xbox 360 互換パッドを生成  (045E:028E)
  ├─ EVIOCGRAB で物理デバイスを独占 (二重入力を防止)
  └─ abstract Unix ソケット (rootpad.bridge.daemon) でアプリと通信
        ▲  EV / DEVADD / DEVDEL     │  PAD / GRAB / PADCREATE …
        │                           ▼
RootPadService (Kotlin, フォアグラウンドサービス)
  └─ BridgeEngine … 生イベントを解釈しゲームパッド状態へ変換
```

- 変換ロジックは **純 Kotlin (`BridgeEngine` / `MouseStickEngine`)** に分離し、単体テスト対象。
- デーモンは JNI ではなく **独立した実行ファイル**。APK の `assets/rootpad_d` に同梱され、
  初回起動時にアプリ領域へ展開 → `su -c` で起動します。
- デーモンは 8 秒間アプリからの通信が無いと終了し、必ず入力状態をニュートラルへ戻します
  (アプリは 2 秒ごとに keepalive を送信)。

### 通信プロトコル (テキスト 1 行)

| 方向 | 例 | 説明 |
|---|---|---|
| daemon → app | `EV <code> <value> <type>` | 物理入力イベント |
| daemon → app | `DEVADD <name>` / `DEVDEL <name>` | デバイスの接続/切断 |
| app → daemon | `PAD <buttons> <lx> <ly> <rx> <ry> <lt> <rt> <hatX> <hatY>` | パッド状態 |
| app → daemon | `PING` | keepalive (2 秒ごと) |

> LT / RT / Guide / 十字キーはボタンマスクではなく、専用フィールドで送信されます。

---

## インストール

### 方法 1: ビルド済み APK を使う (Releases)

1. [Releases](../../releases) から最新の `app-debug.apk` (または release APK) をダウンロード。
2. 端末にインストール (`adb install -r app-debug.apk` もしくはファイルマネージャから)。
3. アプリを開き、**診断** タブですべて **OK** になることを確認。
4. **ホーム** タブから変換サービスを開始。

### 方法 2: 自分でビルドする

[ビルド](#ビルド) を参照してください。

> [!IMPORTANT]
> インストール後、**初回は必ず「診断」タブを確認してください。**
> すべて OK にならない限り、その端末では動作しません。

---

## ビルド

### 必要なもの

- Android SDK (**compileSdk 35**)
- NDK **`28.2.13676358`**
- CMake **`3.22.1`**
- JDK **17〜21**
  (`gradle.properties` の `org.gradle.java.home` で指定済み。既定は Android Studio 同梱の JBR)
- `local.properties` に `sdk.dir=...`

### 手順

```powershell
# デバッグ APK を生成
.\gradlew.bat assembleDebug

# 単体テストを実行
.\gradlew.bat testDebugUnitTest

# まとめて実行 (推奨)
.\gradlew.bat clean assembleDebug testDebugUnitTest --no-daemon
```

成果物: `app/build/outputs/apk/debug/app-debug.apk`

> **Windows での注意 (この環境で実際に発生した問題)**
> 一部の環境で `Path` 環境変数に引用符の対応が取れていない要素が含まれており、
> Gradle のテスト用 JVM が壊れたクラスパスを受け取り
> `ClassNotFoundException` で落ちることがあります。
> その場合は該当要素を除いた PATH でビルドしてください:
> ```powershell
> $env:Path = ($env:Path -split ';' | Where-Object { $_ -notlike '*Muse Code*' }) -join ';'
> .\gradlew.bat testDebugUnitTest
> ```
> これはコード側の不具合ではなく、環境変数の問題です。

---

## 使い方

1. **診断タブ** を開き、root / `/dev/uinput` / 仮想デバイス生成 / Android 認識の 4 項目を確認。
2. **ホームタブ** で「変換を開始」。通知が表示され、フォアグラウンドサービスが起動します。
3. **キー設定タブ** で任意のキーをアクションへ割り当て (タップ → キー入力でキャプチャ)。
4. **マウス設定タブ** で感度・カーブ・ボタン割り当てを調整。ライブプレビューで確認できます。
5. **プロファイルタブ** でゲームごとに設定を保存・切替。

> 変換中は物理キーボード/マウスが `EVIOCGRAB` で独占されるため、
> **通常のテキスト入力には使えません。** 停止すれば元に戻ります。

---

## 多言語対応 (日本語 / English)

UI は **日本語** と **英語** に対応しています。

- 既定は **端末の言語に自動追従** (日本語端末なら日本語、それ以外は英語)。
- アプリバー右上の **🌐 ボタン** から「端末に合わせる / 日本語 / English」を手動で選択できます。
- 選択は `ui_language` として保存され、次回起動時にも維持されます。
- enum (ボタン名・カーブ種別・ホイール方向) の表示名も言語に追従します。
  内部の保存値 (例: `"LS_UP"`, `"LINEAR"`) は言語に依存しないため、
  **言語を切り替えてもプロファイルやキー割り当ては一切変化しません**。
- 通知・チャンネル名は Android の string リソース (`values-ja` / `values`) で提供します。

---

## プロジェクト構成

```
KMtoXGP/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── cpp/                 # root デーモン (C)
│       │   │   ├── daemon_main.c    #   エントリポイント / IPC ループ
│       │   │   ├── input_dev.c      #   /dev/input の列挙・EVIOCGRAB
│       │   │   ├── uinput_pad.c     #   /dev/uinput 仮想パッド生成
│       │   │   ├── usock.c          #   abstract Unix ソケット
│       │   │   └── CMakeLists.txt
│       │   ├── java/com/rootpad/bridge/
│       │   │   ├── core/            # 変換ロジック (純 Kotlin・テスト対象)
│       │   │   │   ├── BridgeEngine.kt      # 生イベント → パッド状態
│       │   │   │   ├── MouseStickEngine.kt  # マウス → 右スティック
│       │   │   │   ├── Model.kt             # PadAction / プロファイル定義
│       │   │   │   ├── Protocol.kt          # テキスト IPC プロトコル
│       │   │   │   ├── ProfileStore.kt      # プロファイル永続化
│       │   │   │   ├── LinuxKeys.kt         # Linux キーコード表
│       │   │   │   └── Lang.kt              # 日本語 / 英語 UI 文字列
│       │   │   ├── service/         # フォアグラウンドサービス / デーモン接続
│       │   │   ├── ui/              # Compose 画面
│       │   │   └── root/            # su 実行ヘルパ
│       │   └── res/                 # アイコン / 文字列リソース
│       └── test/                    # 単体テスト (58 件)
├── gradle/
├── README.md / README.en.md
└── LICENSE
```

---

## 技術スタック

| 領域 | 採用 |
|---|---|
| 言語 | Kotlin 2.3.21 / C11 |
| UI | Jetpack Compose (BOM 2026.06.01, Material 3) |
| ビルド | Gradle 8.14.5 / AGP 8.13.2 / CMake 3.22.1 / NDK 28.2.13676358 |
| 永続化 | kotlinx-serialization-json 1.12.0 (JSON プロファイル) |
| テスト | JUnit 4 |
| 最小 / ターゲット SDK | 31 / 35 |

---

## トラブルシューティング / FAQ

<details>
<summary><b>診断で「uinput にアクセスできません」と出る</b></summary>

カーネルに `CONFIG_INPUT_UINPUT` が必要です。また SELinux (enforcing) が
uinput アクセスを拒否している場合があります。
`su -c "ls -l /dev/uinput"` で権限を確認してください。
</details>

<details>
<summary><b>仮想パッドが Android に GAMEPAD として認識されない</b></summary>

Android の InputReader が `045E:028E` をゲームパッドとして分類していない可能性があります。
診断画面に認識状況が表示されます。プロファイルで **キーレイアウト導入** を有効にすると
`/data/system/devices/` に `.kl` / `.idc` が導入されます。
</details>

<details>
<summary><b>変換中にキーボードで文字入力ができない</b></summary>

仕様です。二重入力を防ぐため `EVIOCGRAB` で物理デバイスを独占しています。
変換サービスを停止すれば元に戻ります。
</details>

<details>
<summary><b>Shizuku で動かせないの?</b></summary>

動きません。Shizuku が付与できるのは shell (uid 2000) 権限のみで、
`/dev/input` の独占取得・`/dev/uinput` へのアクセス・`/data/system/devices/` への書き込みは
いずれも root (uid 0) を要求します。
</details>

<details>
<summary><b>Fortnite などで建築・編集がやりにくい</b></summary>

[既知の制限](#既知の制限) を参照してください。
</details>

---

## 検証状況 (正直な記載)

| 項目 | 状態 |
|---|---|
| Kotlin / C のコンパイル | ✅ 検証済み (`assembleDebug` 成功) |
| APK 生成・`assets/rootpad_d` 同梱 | ✅ 検証済み (ELF を確認) |
| 単体テスト 58 件 | ✅ 全件成功 |
| 実機 (Magisk root 端末, Android 15) での動作 | ✅ 検証済み |
| 仮想パッドの Android 認識 (GAMEPAD + JOYSTICK) | ✅ 検証済み (`/dev/input/event15`) |
| 日本語 / 英語 UI・言語切替・保存 | ✅ 検証済み |
| 既存プロファイルの保持 (言語変更後も変化なし) | ✅ 検証済み |

実機で確認済みの内容:

- `su` が利用でき、デーモンが root で起動すること
- 仮想パッドが生成され、Android が **GAMEPAD + JOYSTICK** として認識すること
- Xbox 用キーレイアウトが `/data/system/devices/` に導入されること
- uinput 生成セルフテストが成功すること
- USB 接続された複数の物理入力デバイスが GRAB されること
- キーの重複割り当て (1 キー → 複数アクション) が保存・再読込されること
- マウスホイール割り当てが保存・再読込されること
- 旧形式 (文字列) プロファイルが新形式 (配列) として正しく移行されること
- 言語を切り替えても既存プロファイル・キー割り当てが変化しないこと

端末依存で、環境によっては動作しない可能性があるもの:

- カーネルに `CONFIG_INPUT_UINPUT` があること、`/dev/uinput` にアクセスできること
- SELinux (enforcing) が uinput アクセスを許可すること
- Android の InputReader が 045E:028E を **GAMEPAD / JOYSTICK として分類**すること
  (端末によってはキーレイアウト/IDC の導入が必要。診断画面に認識状況を表示します)
- 各ゲームが利用する入力 API (Gamepad API / `InputDevice` 直接参照など) との相性

診断画面の表示がすべて「OK」になって初めて、その端末で動作可能と判断できます。

---

## 既知の制限

> **ゲームとの相性について**: 本アプリは意図的に「Xbox パッド」として認識させるため、
> ゲーム側は**パッド用の設定 (感度・エイムアシスト・デッドゾーン・ボタンプロンプト)** を適用します。
> キーボード&マウス用のプリセットとは異なるため、
> 特に建築・編集で「モード切替を挟む」多段操作が必須になるゲームがあります
> (例: フォートナイトの建築)。これはパッドとして認識させている以上、アプリ側では解消できません。

- **arm64-v8a のみ** (32bit / x86 端末は非対応)
- 一部のゲームは root 化端末や仮想入力デバイスを検出して起動を拒否する場合があります
- アンチチート実装によっては、意図せず検出される可能性があります (**自己責任で使用してください**)
- デーモンはアプリから 8 秒間通信が無いと自動終了します (設計上の動作)
- 言語切替は UI のみが対象で、通知の言語は端末設定に従います

---

## コントリビュート

Issue / Pull Request を歓迎します。

1. このリポジトリを Fork する
2. ブランチを作成する (`git checkout -b feature/your-feature`)
3. 変更をコミットする (`git commit -m 'Add some feature'`)
4. ブランチを Push する (`git push origin feature/your-feature`)
5. Pull Request を作成する

**PR を出す前に**: `.\gradlew.bat clean assembleDebug testDebugUnitTest --no-daemon`
を実行し、ビルドとテストが通ることを確認してください。

> [!NOTE]
> 変換ロジック (`core/`) は Android に依存しない純 Kotlin です。
> ロジックの変更には**単体テストの追加**を添えてください。

---

## ライセンス

このプロジェクトは **MIT License** の下で公開されています。詳細は [LICENSE](LICENSE) を参照してください。

---

## 免責

root 権限で `/dev/uinput` と `/dev/input` を操作します。端末の保証対象外の操作を含みます。
本ソフトウェアの使用によって生じたいかなる損害についても、作者は責任を負いません。
自己責任で使用してください。
