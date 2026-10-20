---
name: Bug report
about: Report something that does not work as expected
title: "[Bug] "
labels: bug
---

<!--
Before filing: please check the Diagnostics tab and paste its output below.
RootPad Bridge needs root; issues on non-rooted devices (or Shizuku only) are expected.
-->

## What happened

<!-- A clear and concise description of the bug. -->

## Expected behavior

<!-- What you expected to happen instead. -->

## Steps to reproduce

1.
2.
3.

## Diagnostics output

<!--
Open the Diagnostics tab in the app and paste the four results here
(root / /dev/uinput / device creation / Android recognition).
-->

```
paste diagnostics here
```

## Environment

| Item | Value |
|---|---|
| Device model | |
| Android version | |
| Root method (Magisk version, etc.) | |
| App version / commit | |
| Input device (USB/BT keyboard & mouse) | |

## Logs

<!--
If the app reported an error, attach relevant output. Useful commands:

  adb logcat -d | findstr rootpad
  adb shell su -c "ls -l /dev/uinput /dev/input/event*"
-->

## Additional context

<!-- Screenshots, the game you were testing, etc. -->
