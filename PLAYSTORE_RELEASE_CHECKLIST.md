# Play Store Release Checklist

## Policy declarations
- Declare `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` under Nearby devices with feature justification.
- Declare foreground service usage for type `connectedDevice`.
- Confirm app privacy policy reflects:
  - trusted device metadata storage,
  - diagnostics export behavior,
  - no third-party analytics SDK in this release.

## Compatibility matrix
Run full smoke tests for pairing, connection stability, keyboard input, media keys, mouse, gestures, and disconnect:

- Android devices:
  - Pixel (latest stable Android),
  - Samsung Galaxy (latest stable Android),
  - one additional OEM known for Bluetooth stack variance.
- Host systems:
  - Windows 11,
  - macOS 14/15,
  - Ubuntu LTS.

## Rollout plan
1. Stage 1: 5% rollout for 24 hours.
2. Stage 2: 20% rollout for 48 hours.
3. Stage 3: 100% rollout when Stage 2 has no blocker triggers.

## Rollback triggers
- Connect success rate drops by more than 10% from baseline.
- New crash cluster appears in controller/service paths.
- Pairing failure rate increases materially after update.

## Pre-release quality gates
- `./gradlew testDebugUnitTest` must pass.
- `./gradlew lintDebug` must have zero errors.
- `./gradlew assembleRelease` must pass with minification enabled.
- `./gradlew connectedDebugAndroidTest` must pass on at least:
  - one physical device,
  - one emulator profile.
