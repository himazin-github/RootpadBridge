# RootPad Bridge

**Turn a physical keyboard and mouse into an Xbox-compatible virtual gamepad on Android (root / Magisk required).**

[![Platform](https://img.shields.io/badge/platform-Android%2012%2B%20(API%2031%2B)-3DDC84?logo=android&logoColor=white)](#requirements)
[![Root](https://img.shields.io/badge/root-Magisk%20required-E74C3C)](#requirements)
[![AArch64](https://img.shields.io/badge/ABI-arm64--v8a-blue)](#requirements)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?logo=kotlin&logoColor=white)](#tech-stack)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202026.06.01-4285F4?logo=jetpackcompose&logoColor=white)](#tech-stack)
[![Tests](https://img.shields.io/badge/tests-58%20passing-success)](#verification-status-honest)
[![License](https://img.shields.io/badge/license-MIT-blue)](#license)

[日本語](README.md) | **English**

</div>

---

> [!WARNING]
> **This app requires root.** It exclusively grabs `/dev/input` (`EVIOCGRAB`), creates a virtual
> device on `/dev/uinput`, and writes key layouts to `/data/system/devices/`, so
> **it does not work without root (including with Shizuku).**
> Shizuku only grants the shell (uid 2000) identity, which is not enough for these operations.
> This involves operations outside the device warranty. Use at your own risk.

---

## Table of contents

- [What is this](#what-is-this)
- [Screenshots](#screenshots)
- [Requirements](#requirements)
- [Features](#features)
- [How it works](#how-it-works)
- [Installation](#installation)
- [Build](#build)
- [Usage](#usage)
- [Localization (Japanese / English)](#localization-japanese-english)
- [Project structure](#project-structure)
- [Tech stack](#tech-stack)
- [Troubleshooting / FAQ](#troubleshooting-faq)
- [Verification status (honest)](#verification-status-honest)
- [Known limitations](#known-limitations)
- [Contributing](#contributing)
- [License](#license)

---

## What is this

RootPad Bridge converts **physical keyboard / mouse input into an Xbox-compatible virtual gamepad**
on Android devices with root (Magisk). Games see it as a normal gamepad, so you can bring
PC-style controls to games that do not support keyboard and mouse.

- Package: `com.rootpad.bridge`
- The conversion logic is **pure Kotlin** and unit-tested
- **No internet permission** (fully offline, no telemetry)

> A power-user tool intended for devices already rooted with Magisk or similar.

---

## Screenshots

<!-- TODO: add screenshots to docs/screenshots/ and uncomment the block below -->

> Screenshots coming soon.
>
> <!--
> | Home | Key map | Mouse |
> |---|---|---|
> | ![Home](docs/screenshots/home.png) | ![Keys](docs/screenshots/keys.png) | ![Mouse](docs/screenshots/mouse.png) |
>
> | Profiles | Diagnostics |
> |---|---|
> | ![Profiles](docs/screenshots/profiles.png) | ![Diagnostics](docs/screenshots/diagnostics.png) |
> -->

---

## Requirements

| Item | Requirement |
|---|---|
| OS | Android 12 (API 31) or newer / verified on Android 15 |
| Architecture | **arm64-v8a only** |
| Root | **Root via Magisk or similar is required** |
| Kernel | `CONFIG_INPUT_UINPUT` enabled and `/dev/uinput` accessible |
| SELinux | Must permit uinput access |
| Input | A physical USB / Bluetooth keyboard and mouse |

**Why root is required**: the app directly does the following.

- Exclusively grabs `/dev/input/event*` via `EVIOCGRAB` (to prevent double input)
- Creates a virtual gamepad on `/dev/uinput`
- Installs Xbox key layout files (`.kl` / `.idc`) into `/data/system/devices/`

All of these require root (uid 0), so **Shizuku cannot substitute for it**
(Shizuku only grants shell / uid 2000).

---

## Features

- **Root / uinput diagnostics**: root availability, presence of `/dev/uinput`, virtual device
  creation test, and how Android (`InputDevice` API) classified it. **Never reports false success.**
- **Virtual Xbox pad**: A/B/X/Y, LB/RB, LT/RT, Menu/View, both sticks + presses, D-pad.
- **Default key map** (all rebindable):

  | Key | Action | Key | Action |
  |---|---|---|---|
  | W/A/S/D | Left stick | Space | A |
  | Q | B | E | X |
  | R | Y | Left Shift / 1 | LB |
  | F / 2 | RB | Tab | View |
  | Esc | Menu | Left Ctrl | L3 |
  | Z | R3 | G | Guide |
  | Arrow keys | D-pad | | |

- **Duplicate key bindings**: one key can be assigned to multiple actions (pressed together).
  e.g. bind Space to both A and LB. Existing bindings are kept and the new one is added.
- **Mouse wheel mapping**: assign wheel up/down/left/right (tilt) to any action
  (default: up = D-pad up, down = D-pad down). Each notch is sent as a short press.
- **Mouse → right stick**: sensitivity (independent X/Y, **0.05x–50x** via exponential curve),
  axis invert, deadzone, curve (Linear / Square / Smooth), acceleration, max deflection,
  auto return-to-center after you stop moving. Live preview. Mouse button bindings are editable
  (default: left = RT, right = LT, side 4 = LB, side 5 = RB).
- **Profiles**: create / rename / duplicate / delete / reset. Changes are saved immediately.
- **Foreground service**: start/stop from the notification. Stopping always resets input.
  Detects daemon crashes and auto-restarts (up to 3 times).
- **Auto-resume on boot** (only when enabled in the profile).

---

## How it works

```
physical keyboard / mouse
        │  /dev/input/event*
        ▼
rootpad_d  (C daemon running as root)
  ├─ creates an Xbox 360 compatible pad on /dev/uinput  (045E:028E)
  ├─ EVIOCGRAB to exclusive-grab physical devices (prevents double input)
  └─ talks to the app over an abstract Unix socket (rootpad.bridge.daemon)
        ▲  EV / DEVADD / DEVDEL     │  PAD / GRAB / PADCREATE …
        │                           ▼
RootPadService (Kotlin foreground service)
  └─ BridgeEngine … interprets raw events and builds the gamepad state
```

- The conversion logic lives in **pure Kotlin (`BridgeEngine` / `MouseStickEngine`)** and is unit-tested.
- The daemon is **not JNI** but a standalone executable. It is bundled as `assets/rootpad_d`,
  extracted to app storage on first run, and launched via `su -c`.
- If the app goes silent for 8 seconds the daemon exits and always resets input to neutral
  (the app sends a keepalive every 2 seconds).

### Wire protocol (one text line each)

| Direction | Example | Meaning |
|---|---|---|
| daemon → app | `EV <code> <value> <type>` | physical input event |
| daemon → app | `DEVADD <name>` / `DEVDEL <name>` | device attach / detach |
| app → daemon | `PAD <buttons> <lx> <ly> <rx> <ry> <lt> <rt> <hatX> <hatY>` | pad state |
| app → daemon | `PING` | keepalive (every 2s) |

> LT / RT / Guide / D-pad are not button-mask bits; they are sent in dedicated fields.

---

## Installation

### Option 1: use a prebuilt APK (Releases)

1. Download the latest `app-debug.apk` (or release APK) from [Releases](../../releases).
2. Install it (`adb install -r app-debug.apk`, or from a file manager).
3. Open the app and confirm everything shows **OK** on the **Diagnostics** tab.
4. Start the service from the **Home** tab.

### Option 2: build it yourself

See [Build](#build).

> [!IMPORTANT]
> After installing, **always check the Diagnostics tab first.**
> It will not work on your device until every item shows OK.

---

## Build

### Requirements

- Android SDK (**compileSdk 35**)
- NDK **`28.2.13676358`**
- CMake **`3.22.1`**
- JDK **17–21** (set via `org.gradle.java.home` in `gradle.properties`; defaults to Android Studio's JBR)
- `local.properties` with `sdk.dir=...`

### Steps

```powershell
# build the debug APK
.\gradlew.bat assembleDebug

# run unit tests
.\gradlew.bat testDebugUnitTest

# do both (recommended)
.\gradlew.bat clean assembleDebug testDebugUnitTest --no-daemon
```

Artifact: `app/build/outputs/apk/debug/app-debug.apk`

> **Windows note (a real issue seen in this environment)**
> Some environments have an unmatched quote in the `Path` variable, which makes the Gradle test JVM
> receive a broken classpath and fail with `ClassNotFoundException`.
> In that case, build with the offending entries removed from PATH:
> ```powershell
> $env:Path = ($env:Path -split ';' | Where-Object { $_ -notlike '*Muse Code*' }) -join ';'
> .\gradlew.bat testDebugUnitTest
> ```
> This is an environment issue, not a bug in the code.

---

## Usage

1. Open the **Diagnostics** tab and check the four items: root, `/dev/uinput`,
   virtual device creation, and Android recognition.
2. On the **Home** tab, tap "Start". A notification appears and the foreground service starts.
3. On the **Key map** tab, assign any key to an action (tap → press a key to capture).
4. On the **Mouse** tab, tune sensitivity, curve, and button bindings. Use the live preview to check.
5. On the **Profiles** tab, save and switch settings per game.

> While running, the physical keyboard/mouse is exclusively grabbed via `EVIOCGRAB`, so it
> **cannot be used for normal typing.** Stopping the service restores it.

---

## Localization (Japanese / English)

- Default is **automatic**: follows the system language (Japanese on Japanese devices, otherwise English).
- The **🌐 button** in the top-right of the app bar lets you pick
  "System default / 日本語 / English" manually.
- The choice is stored as `ui_language` and restored on the next launch.
- Enum labels (button names, curve types, wheel directions) follow the language too.
  Stored values (e.g. `"LS_UP"`, `"LINEAR"`) are language-independent, so
  **switching language never changes your profiles or key bindings**.

---

## Project structure

```
KMtoXGP/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── cpp/                 # root daemon (C)
│       │   │   ├── daemon_main.c    #   entry point / IPC loop
│       │   │   ├── input_dev.c      #   /dev/input enumeration & EVIOCGRAB
│       │   │   ├── uinput_pad.c     #   /dev/uinput virtual pad creation
│       │   │   ├── usock.c          #   abstract Unix socket
│       │   │   └── CMakeLists.txt
│       │   ├── java/com/rootpad/bridge/
│       │   │   ├── core/            # conversion logic (pure Kotlin, tested)
│       │   │   │   ├── BridgeEngine.kt      # raw events -> pad state
│       │   │   │   ├── MouseStickEngine.kt  # mouse -> right stick
│       │   │   │   ├── Model.kt             # PadAction / profile model
│       │   │   │   ├── Protocol.kt          # text IPC protocol
│       │   │   │   ├── ProfileStore.kt      # profile persistence
│       │   │   │   ├── LinuxKeys.kt         # Linux key code table
│       │   │   │   └── Lang.kt              # JA / EN UI strings
│       │   │   ├── service/         # foreground service / daemon connection
│       │   │   ├── ui/              # Compose screens
│       │   │   └── root/            # su execution helper
│       │   └── res/                 # icons / string resources
│       └── test/                    # unit tests (58)
├── gradle/
├── README.md / README.en.md
└── LICENSE
```

---

## Tech stack

| Area | Choice |
|---|---|
| Language | Kotlin 2.3.21 / C11 |
| UI | Jetpack Compose (BOM 2026.06.01, Material 3) |
| Build | Gradle 8.14.5 / AGP 8.13.2 / CMake 3.22.1 / NDK 28.2.13676358 |
| Persistence | kotlinx-serialization-json 1.12.0 (JSON profiles) |
| Testing | JUnit 4 |
| min / target SDK | 31 / 35 |

---

## Troubleshooting / FAQ

<details>
<summary><b>Diagnostics says "cannot access uinput"</b></summary>

The kernel needs `CONFIG_INPUT_UINPUT`, and SELinux (enforcing) may deny uinput access.
Check permissions with `su -c "ls -l /dev/uinput"`.
</details>

<details>
<summary><b>Android does not recognize the virtual pad as a GAMEPAD</b></summary>

Android's InputReader may not classify `045E:028E` as a gamepad. The Diagnostics screen shows
the recognition status. Enabling **install keylayout** in the profile writes the `.kl` / `.idc`
files into `/data/system/devices/`.
</details>

<details>
<summary><b>I can't type with my keyboard while it's running</b></summary>

That is by design. Physical devices are exclusively grabbed via `EVIOCGRAB` to prevent double input.
Stopping the service restores normal typing.
</details>

<details>
<summary><b>Can I use Shizuku instead of root?</b></summary>

No. Shizuku can only grant the shell (uid 2000) identity, while grabbing `/dev/input`,
accessing `/dev/uinput`, and writing to `/data/system/devices/` all require root (uid 0).
</details>

<details>
<summary><b>Building and editing feels awkward in Fortnite</b></summary>

See [Known limitations](#known-limitations).
</details>

---

## Verification status (honest)

| Item | Status |
|---|---|
| Kotlin / C compilation | ✅ Verified (`assembleDebug` succeeds) |
| APK build with bundled `assets/rootpad_d` | ✅ Verified (ELF confirmed) |
| 58 unit tests | ✅ All passing |
| On-device (Magisk root, Android 15) | ✅ Verified |
| Android recognizes the virtual pad (GAMEPAD + JOYSTICK) | ✅ Verified (`/dev/input/event15`) |
| Japanese / English UI, switching and persistence | ✅ Verified |
| Existing profiles preserved after language change | ✅ Verified |

Confirmed on a real device:

- `su` is available and the daemon starts as root
- The virtual pad is created and Android classifies it as **GAMEPAD + JOYSTICK**
- The Xbox key layout is installed into `/data/system/devices/`
- The uinput creation self-test succeeds
- Multiple USB-attached physical input devices are GRABbed
- Duplicate key bindings (one key → multiple actions) are saved and reloaded
- Mouse wheel bindings are saved and reloaded
- Legacy (string) profiles migrate correctly to the new (array) format
- Switching the language does not change existing profiles or key bindings

Device-dependent items that may not work everywhere:

- The kernel provides `CONFIG_INPUT_UINPUT` and `/dev/uinput` is accessible.
- SELinux (enforcing) permits uinput access.
- Android's InputReader classifies 045E:028E as **GAMEPAD / JOYSTICK**
  (some devices need the keylayout/IDC installed; the Diagnostics screen shows the status).
- Compatibility with the input API each game uses (Gamepad API, direct `InputDevice`, etc.).

You can only consider it working on your device once the Diagnostics screen shows all OK.

---

## Known limitations

> **Note on game compatibility**: Because this app intentionally presents itself as an *Xbox pad*,
> games apply their **controller presets** (sensitivity, aim assist, deadzone, button prompts),
> which differ from keyboard & mouse presets. In particular, some games force
> multi-step "hold a mode-switch button + select with the stick" flows for building/editing
> (e.g. Fortnite building). As long as it is recognized as a pad, the app cannot remove that.

- **arm64-v8a only** (no 32-bit or x86 support)
- Some games detect rooted devices or virtual input devices and refuse to launch
- Depending on the anti-cheat implementation, it may be detected unintentionally
  (**use at your own risk**)
- The daemon exits automatically after 8 seconds without communication from the app (by design)
- The language switch affects the UI only; notification language follows the system setting

---

## Contributing

Issues and pull requests are welcome.

1. Fork this repository
2. Create a branch (`git checkout -b feature/your-feature`)
3. Commit your changes (`git commit -m 'Add some feature'`)
4. Push the branch (`git push origin feature/your-feature`)
5. Open a pull request

**Before opening a PR**: run `.\gradlew.bat clean assembleDebug testDebugUnitTest --no-daemon`
and make sure the build and tests pass.

> [!NOTE]
> The conversion logic (`core/`) is pure Kotlin with no Android dependency.
> Please include **unit tests** with any logic change.

---

## License

Released under the **MIT License**. See [LICENSE](LICENSE) for details.

---

## Disclaimer

This app manipulates `/dev/uinput` and `/dev/input` with root privileges.
This includes operations outside the device warranty. The author is not liable for any
damage resulting from the use of this software. Use at your own risk.
