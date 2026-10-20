# Security Policy

## Scope

RootPad Bridge runs a native daemon as **root (uid 0)** and manipulates
`/dev/input`, `/dev/uinput`, and `/data/system/devices/`. Because of this, security
issues are taken seriously.

In scope:

- Command injection or path traversal in root execution paths
  (e.g. how the app writes and launches `rootpad_d`, or forwards arguments to `su`).
- The daemon or its IPC channel (abstract Unix socket `rootpad.bridge.daemon`) accepting
  commands from other apps on the device.
- Privilege escalation beyond what the user granted.
- Any way untrusted input (device names, event data) could lead to code execution or
  overwriting files outside the intended locations.

## Reporting a vulnerability

**Please do not open a public issue for security problems.**

Instead, report privately via GitHub's [private vulnerability reporting](../../security/advisories/new)
(Security → Report a vulnerability) or contact the maintainer directly.

Please include:

- A description of the issue and its impact
- Steps to reproduce (device, Android version, root method)
- Any proof-of-concept or logs

You can expect an initial response within a few days. Please allow reasonable time for a fix
before public disclosure.

## Important notes for users

- This app **requires root** and performs operations outside the device warranty.
- Only install builds from this repository's Releases, or build it yourself from source.
- The app requests **no internet permission** and has no telemetry. Any build that asks for
  network access is not this project.
- Use at your own risk. See the disclaimer in the [README](README.md).
