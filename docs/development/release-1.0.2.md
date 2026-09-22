# Closed testing release 1.0.2

Prepared 22 September 2026 from main commit `c125861`, with the release version advanced to code 3 / name 1.0.2 because code 2 was already published on Alpha.

## Changes

- Compact single-screen home with direct access to Wi-Fi tools.
- Reorganized survey results, history and signal analysis.
- Removed unreliable room geometry and router-placement estimates.
- Clearer network details and interface copy.

## Validation

- `bundleRelease testDebugUnitTest --max-workers=2 --console=plain`: successful.
- 10 JVM tests, zero failures and zero errors.
- Release lint vital, R8 optimization, production ad identifier checks and signing completed.
- `jarsigner -verify`: jar verified; self-signed upload certificate and ZIP stream-order warnings remain, as with the previous accepted AAB.
- AAB: `app/build/outputs/bundle/release/app-release.aab`, 27,561,710 bytes.
- SHA-256: `A157A66B1F97DC8ACA589078B3BD8DD753691E3E987E146459171D23DE3931EE`.
- No physical-device scan or live purchase test was performed for this update.

## Distribution

Target: existing closed Alpha track `4700499920695228359`, release 3. Existing testers and countries remain unchanged. English and Indonesian release notes were saved. Google Play accepted code 3 (1.0.2), with no blocking errors and one warning about missing native debug symbols. Supported device counts are unchanged; Play reports an 11.8 MB delivered download.

Submitted for 100% Alpha rollout as `1.0.2 - Clearer home and survey results`. Publishing overview confirmed **Changes in review**, with preliminary checks running. Managed publishing is off, so publication follows Google's approval automatically. Availability to testers is not yet confirmed.

At the start of this update, Play Console showed 12 testers opted in continuously for 9 days. Production remains outside this request.
