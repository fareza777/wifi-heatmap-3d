# Monetization validation

Date: 2026-09-13. Package: `com.f7developer.wifiheatmap3d`. Version: `1.0.0` (code 1).

## Scope and environment

This report covers local Android compilation, resources, monetization regression tests,
and Android lint. It does not certify a live Google Play transaction or live AdMob delivery.

- Java 21; Android SDK installed at `C:/Android/Sdk`.
- Compile/target SDK 37; minimum SDK 26.
- Play Billing 9.1.0; Google Mobile Ads 25.4.0; UMP 4.0.0.
- Build concurrency limited to two workers. Only this verification run's Java wrapper
  and descendant build processes were assigned BelowNormal priority initially. Their
  priority was raised to Normal after the competing video render completed, as authorized.

Command:

```powershell
./gradlew.bat testDebugUnitTest assembleDebug lintDebug --max-workers=2 --console=plain
```

## Results

Final local verification passed. The full test/build/lint run completed successfully
in 1 minute 38 seconds; the final packaging/lint check after language and backup-rule
changes completed successfully in 1 minute 30 seconds with the source frozen.

| Check | Result | Evidence |
| --- | --- | --- |
| JVM regression tests | PASS | 10 tests, zero failures, zero errors |
| Android sources, resources, assets, and APK packaging | PASS | `assembleDebug` completed successfully |
| Android lint | PASS with warnings | Zero errors, 86 warnings, one hint |
| APK signature | PASS | `apksigner verify --verbose`: valid APK Signature Scheme v2; one debug signer |
| APK identity | PASS | Package/version match the report header; minimum API 26, target API 37 |
| Missing release ad identifiers | PASS, negative test | Validation rejects explicitly blank identifiers |
| Signed release bundle | PASS | `bundleRelease` completed in 7m 27s; production ad IDs validated, release lint and R8 completed; `jarsigner -verify` reports jar verified |
| Live store/ad tests | NOT RUN | Requires configured closed testing and real-device service verification |

Release AAB: `app/build/outputs/bundle/release/app-release.aab`, 27,499,842 bytes.
SHA-256: `B3CEC09F3388D764759D5225A7DD0E6035EB08A3F33686251BF246AEBB24B3BE`.
Built with the production banner and interstitial identifiers on 2026-09-13.

Final APK: `app/build/outputs/apk/debug/app-debug.apk`, 61,961,761 bytes,
modified 2026-09-13 07:09:51 Asia/Jakarta. This is a **debug/test-ads APK**, not a
production upload artifact. It includes the debug-only screenshot capture harness.

SHA-256:

```text
B84C8D8431B02889A346E65EB3585757AD29BCC4CDD8CEC6CF8B21CFCD21CA19
```

Local reports:

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/lint-results-debug.html`
- `app/build/reports/lint-results-debug.xml`
- `app/build/reports/lint-results-debug.sarif`

The remaining warnings include 30 unused resources, 25 pluralization suggestions,
10 Kotlin extension suggestions, six inlined-API constants, dependency/version
notices, and smaller locale/style recommendations. They are retained in the lint
report rather than hidden by a baseline. A passing lint task does not mean zero warnings.

The release identifier guard was executed with explicitly blank values for all three
AdMob settings. It failed as intended with the instruction to configure a real
`ADMOB_APP_ID`; no release artifact was produced by that validation-only task.

```powershell
./gradlew.bat :app:validateReleaseAds -PADMOB_APP_ID= -PADMOB_BANNER_ID= -PADMOB_INTERSTITIAL_ID= --max-workers=2 --console=plain
```

Two earlier lint attempts failed inside `ExperimentalDetector` with Kotlin's
`Unexpected owner function: null`, first while analyzing `BillingManager.kt` and later
`StoreScreenshotActivity.kt`. Both attempts overlapped source edits. A stable-source
rerun completed analysis without that crash, supporting concurrent source mutation
as a suspected contributor; this does not establish an upstream root cause.

That completed lint analysis identified 96 errors: 80 missing Indonesian resources,
nine SDK-guard findings, one permission finding, four configuration-aware-resource
findings, an SDK-path escaping issue, and a missing optional camera declaration.
Corrections add the Indonesian entries and camera declaration, recognize the existing
runtime-gated capability helper through `ChecksSdkIntAtLeast`, fix the actual API
thresholds for 60 GHz (31) and scan throttling (30), catch permission revocation at
scan-result access, use Compose `LocalResources`, and escape the local SDK path.
No broad lint suppression or baseline was introduced.

The final packaging check also verifies that English and Indonesian are shipped
together, because Settings changes locale locally without downloading language
splits. Explicit legacy and Android 12+ backup rules exclude app data from cloud
backup and device-to-device transfer, with `allowBackup` remaining false.

## Automated regression coverage

`EntitlementPolicyTest` has four tests:

- A failed Play query preserves an existing paid entitlement.
- A failed Play query cannot grant an unverified entitlement.
- A successful Play query can revoke a refunded purchase.
- A successful Play query restores a completed purchase.

`AdBreakPolicyTest` has six tests:

- Initial launch and viewing history cannot trigger an interstitial.
- Two completed scans are insufficient even after the cooldown.
- Three completed scans still require the three-minute cooldown.
- An ad requires eligibility, a loaded creative, and an allowed natural break.
- Showing an ad resets scan count/cooldown and consumes that result's opportunity.
- A missing ad is skipped and cannot appear later without another completed scan.

The tests were first run against the previous reconciliation/frequency behavior;
five assertions failed. After the policy corrections, all ten tests passed. The
tests exercise local policy decisions, not the Google SDKs or actual store transactions.

## Implemented behavior

UMP is consulted at launch and supplies the ad-request eligibility decision. Ads SDK
initialization and ad requests require both eligible consent and a nonpaid entitlement.
Debug builds use Google's test application and ad unit identifiers. Privacy choices
appear in Settings when UMP requires an entry point. Opening those choices removes
the existing banner and clears cached interstitials while the choice can change.

Banners remain on Home and the result screen, adapt to their available layout width,
and pause/resume/destroy with their lifecycle. A completed scan records progress but
does not show an interstitial. Only leaving its results can present one, after at
least three scans and 180 seconds. Rescan consumes the break without showing an ad;
missing/expired creatives never delay navigation or schedule a later interruption.

The permanent `remove_ads` product is queried from Google Play. Its displayed price
comes from the eligible Play offer; no locally invented fallback price is displayed.
Modern offer tokens are passed to checkout, with compatibility for legacy products
that have no token. Pending payments receive explanatory status and do not grant
ownership or initiate another purchase. Cancellation, unavailability, restore, and
checkout failures receive visible feedback. Failed purchase queries preserve the
paid cache. Purchase acknowledgment retries after 10, 30, and 120 seconds; subsequent
foreground queries retry ownership that still requires acknowledgment.

## Release configuration

Release builds require real `ADMOB_APP_ID`, `ADMOB_BANNER_ID`, and
`ADMOB_INTERSTITIAL_ID` Gradle properties or same-name environment variables.
`validateReleaseAds` rejects missing/malformed identifiers and Google's public test
publisher. The release preparation task depends on this validation.

Signing reads ignored `release-signing.properties` keys `storeFile`, `storePassword`,
`keyAlias`, and `keyPassword`, or the corresponding `RELEASE_STORE_FILE`,
`RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD` environment
variables. No signing values belong in this report or source control.

The intended US base price is USD 4.99, configured in Play Console; localized prices
and taxes are determined by Google Play. This local check does not confirm product
activation or its configured regional prices.

## Outstanding live checks

Before production publishing, use a signed build installed from the Google Play test
track to verify completed purchase, cancellation, pending payment completion, restore
on reinstall, offline paid startup, refund reconciliation, and acknowledgment behavior.
Verify that a completed purchase removes both banner and full-screen ads immediately.

Validate regional UMP forms and the privacy-options entry point with designated test
devices, including unavailable network and changed consent. Verify banners and the
return-from-results interstitial timing on a real supported Android device. Confirm
that capture and the initial result are never blocked by advertising.

A release bundle and live AdMob delivery still await the real interstitial identifier.
No live Play checkout, live charge, or production ad delivery is claimed by this report.

## Integration references

- [Google UMP Android integration](https://developers.google.com/admob/android/privacy)
- [Google Play one-time purchase options and offer tokens](https://developer.android.com/google/play/billing/one-time-product-multi-purchase-options-offers)
