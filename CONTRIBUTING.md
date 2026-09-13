# Contributing to OpenPage

Thank you for considering a contribution to OpenPage. The project is an Android application for direct USB printing and scanning, and contributions should preserve its privacy-first, offline-oriented design.

## Before opening an issue or pull request

Please search existing issues first. For feature requests, describe the user problem, affected Android versions and hardware, and any compatibility or privacy implications. For printer or scanner support, include the device manufacturer and model when safe to share, the transport and interface details, the selected driver, and relevant diagnostics output. Do not include documents, serial numbers, credentials, or private network information.

## Development

Use JDK 17 or newer and Android SDK 35. Run the relevant checks before submitting a pull request:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

Instrumentation tests require a configured Android device or emulator:

```bash
./gradlew connectedDebugAndroidTest
```

Keep user-facing text in Android resources, maintain accessibility semantics, avoid adding network permissions without a documented need, and add focused tests for protocol, parsing, geometry, or state-management changes.

## Pull requests

Explain what changed, why it changed, how it was tested, and whether real hardware was used. Keep pull requests focused. Do not commit signing keys, `keystore.properties`, generated build output, device logs containing sensitive information, or user documents.

By contributing, you agree that your contribution may be distributed under the MIT License included in this repository.
