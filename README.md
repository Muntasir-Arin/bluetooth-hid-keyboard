# BT Keyboard (Android)

Android app that turns a phone into a Bluetooth HID keyboard for host devices (Windows/macOS/Linux/Android).

## Features (v1)

- Bluetooth Classic HID Device profile keyboard + mouse
- In-app device discovery, pairing, and connection
- Regular Android soft keyboard input forwarding via HID reports
- Compact special key panel: navigation, function keys, modifiers, media keys
- Dedicated on-screen trackpad with tap-to-click, left/right buttons, and two-finger scroll
- Smart idle foreground service lifecycle (lazy start, connected keep-alive, background idle auto-stop)
- Auto reconnect to last connected trusted host
- Trusted device and settings persistence via DataStore
- One-time mandatory re-pair flow when HID descriptor version changes
- Diagnostics log export from Settings

## Tech Stack

- Kotlin + Jetpack Compose
- Android API 29+ (`targetSdk 35`, `compileSdk 35`)
- DataStore Preferences

## Build

Requirements:

- Android SDK installed (`ANDROID_HOME` configured)
- JDK that includes `javac` (full JDK, not JRE-only runtime)

Then run:

```bash
ANDROID_HOME=$HOME/Android/Sdk ./gradlew assembleDebug
```

## Notes

- HID Device support depends on Android OS + OEM implementation.
- The app uses US QWERTY key mapping in v1.
