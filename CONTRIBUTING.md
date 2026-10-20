# Contributing

Thanks for your interest in RootPad Bridge!

## Ways to help

- **Bug reports** — open an issue with your device, Android version, and the Diagnostics screen output.
- **Feature requests** — describe the use case first, then the proposed behavior.
- **Pull requests** — fixes, new bindings, UI polish, translations, and tests are all welcome.
- **Documentation** — improving the READMEs or adding troubleshooting notes is valuable too.

## Development setup

1. Install Android Studio (or the command-line SDK) with:
   - Android SDK **compileSdk 35**
   - NDK **28.2.13676358**
   - CMake **3.22.1**
   - JDK **17–21**
2. Create `local.properties` with your SDK path:
   ```properties
   sdk.dir=C\:\\path\\to\\Android\\Sdk
   ```
3. If your default JDK is outside 17–21, point Gradle at a supported one.
   Do **not** commit this — put it in your user-level `~/.gradle/gradle.properties`:
   ```properties
   org.gradle.java.home=C\:\\Program Files\\Android\\Android Studio\\jbr
   ```

## Before opening a pull request

Run the full build and test suite:

```powershell
.\gradlew.bat clean assembleDebug testDebugUnitTest --no-daemon
```

Both must succeed. Please also keep the existing 58 tests passing.

## Code guidelines

- **Keep `core/` pure Kotlin.** The conversion logic (`BridgeEngine`, `MouseStickEngine`,
  `Model`, `Protocol`, `ProfileStore`, `LinuxKeys`) must not depend on Android APIs so it stays
  unit-testable. Add tests for any logic change.
- **Never break saved data.** Profiles are stored as JSON at
  `files/profiles/<id>.json` and preferences in `SharedPreferences`. When you change a stored
  format, keep reading the old form (see `KeyBindingsSerializer` for the legacy→array migration
  pattern) and never delete or overwrite existing user data.
- **Keep UI text localized.** All user-facing strings belong in `core/Lang.kt` under both the
  Japanese and English maps. Enum display names use `label(lang)` functions.
  Stored values (e.g. `"LS_UP"`, `"LINEAR"`) must stay language-independent.
- **Be honest in diagnostics.** Never report success for something that was not actually verified.
- Match the style of the surrounding code (naming, formatting, comment language conventions).

## Commit messages

Use a short imperative subject line, for example:

```
Fix duplicate-key capture when a key is already assigned
Add mouse wheel tilt bindings to profiles
```

## Reporting security issues

Please do not open a public issue for security-sensitive problems — see [SECURITY.md](SECURITY.md).

## License

By contributing, you agree that your contributions are licensed under the
[MIT License](LICENSE).
