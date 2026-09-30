# Production release 1.0.3

Prepared 30 September 2026 from repository revision `20da4e5`; version advanced to code 4 / name 1.0.3 for the first Production release.

## Bundle

- File: `app/build/outputs/bundle/release/app-release.aab`
- Size: 27,561,697 bytes
- SHA-256: `235010ABDD38EC05FC519E8D99CCDB341DEFE99BBC933E95DB33811E1BA73529`
- `bundleRelease`: successful; release lint vital, R8, production AdMob identifier validation and upload-key signing completed.
- `jarsigner -verify`: jar verified. The upload-key certificate is self-signed and the usual JAR stream-order warnings remain.
- Play Console parsed version code 4 / version name 1.0.3, minimum API 26, target API 37. Delivered install size estimate: 11.8 MB.
- No tests were run for this version-only production rebuild. Previous application code is the 1.0.2 build already serving on closed Alpha.

## Production distribution

- Track: Production (`4697365245780258009`)
- Release: `1.0.3 - Production launch`, 100% rollout.
- Country targeting: all 177 available countries/regions plus Rest of World, 178 total.
- English and Indonesian release notes saved.
- Play reported one non-blocking warning: the bundle contains native code without uploaded debug symbols.
- Submitted to Play Console automated checks/review on 30 September 2026. It is not publicly available until Google approves it. Managed publishing is off, so it will publish automatically after approval.

## AdMob

- App ID: `ca-app-pub-6279186647593327~1942300843`
- Adaptive banner: `ca-app-pub-6279186647593327/5053523223`
- Interstitial: `ca-app-pub-6279186647593327/3223592929`, capped at 1 impression per user every 3 minutes.
- App-serving account is approved and enabled. The WiFi Heatmap app itself still requires AdMob review. AdMob could not find the app in Google Play before Production went live, so store association and the app-level readiness review remain pending until the public listing is available.
- Consent/privacy messages, public privacy URL, UMP privacy controls and publisher `app-ads.txt` were already configured and verified.
