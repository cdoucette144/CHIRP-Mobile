# ChirpField development baseline (2026-11-03)

## Product contract
- Preserve the existing Jetpack Compose three-tab interface.
- Channel Banks: portable, shareable analog definitions independent of radio.
- Codeplug: working copy assembled from banks or downloaded/opened; edits never mutate banks.
- Radio Settings: device-specific features and menus.
- Only analog FM/NFM/AM imports; exclude digital voice modes.
- Compatibility requires frequency + modulation + model/firmware validation.
- Native TX inhibit is preferred. Dummy TX fallback is explicitly TX-capable, requires per-model validation and operator confirmation. Never silently enable TX.
- Support properly authorized transmit configurations.

## First implementation
`AnalogCompatibility.kt` provides an independent, testable channel/capability model and decision engine. It is **not yet wired into the UI or programming drivers**. Radio capability definitions must be verified against real hardware and firmware before use.

## Build
GitHub Actions workflow runs unit tests and builds a debug APK using Gradle 9.3.1. The repository requires a suitable Android SDK and dependency resolution. Build has **not been verified** in this environment. The export lacks Gradle wrapper JAR; workflow uses a provisioned Gradle installation.

## Security and distribution
Do not publish personal credentials, .env, private signing keys, or private radio configurations. Review Firebase project config and any bundled identifiers before pushing to a public repository. No radio write operation should be enabled without a verified read/backup and checksums.
