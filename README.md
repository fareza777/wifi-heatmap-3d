# WiFi Heatmap 3D : Signal Map

Android Wi-Fi survey and network diagnostic utility from F7 Developer. Kotlin + Jetpack Compose, ARCore tracking, SceneView/Filament room visualization, local survey storage, Google Mobile Ads and Google Play Billing.

## Build

Use JDK 21 and an Android SDK with platform 37. Set `sdk.dir` in ignored `local.properties`, then run `gradlew.bat assembleDebug`. Debug uses Google's public test ad identifiers. Requires Android 8.0 or newer. AR scanning additionally requires an ARCore-compatible device.

`gradlew.bat testDebugUnitTest` checks purchase reconciliation and ad-break scheduling. `gradlew.bat lintDebug` checks Android issues.

## Release

App ID: `com.f7developer.wifiheatmap3d`; namespace: `com.sinyal.app`; version: `1.0.0` (1). Set Gradle properties or environment variables `ADMOB_APP_ID`, `ADMOB_BANNER_ID`, `ADMOB_INTERSTITIAL_ID` to the real units. Release builds reject test or missing IDs.

Signing reads the local, ignored `release-signing.properties`: `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. Never commit the file or keystore. Environment alternatives are `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.

Build with `gradlew.bat bundleRelease`. Google Play product ID is `remove_ads`, a non-consumable one-time product with US base price USD 4.99. UI uses the localized price returned by Play. See [release status](RELEASE-STATUS.md) for actual Console completion and outstanding work.

## Marketing and public pages

- `marketing/store-listing-en-US.txt` and `marketing/store-listing-id.txt`: store copy.
- `marketing/icon/`: master and Play icon export.
- `marketing/screenshots/`: actual app screenshots and reproducible debug capture harness documentation.
- `marketing/feature-graphic.png`: Play feature graphic.
- `marketing/video/`: Remotion project and rendered output instructions.
- `docs/`: GitHub Pages support site and privacy policy.

[Privacy policy](https://fareza777.github.io/wifi-heatmap-3d/privacy-policy.html) · [Support](mailto:fajar.mreza@gmail.com)

Screenshot fixtures are restricted to the debug source set. Production builds do not include sample data or the screenshot activity.
