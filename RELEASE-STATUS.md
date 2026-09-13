# Release preparation — 13 September 2026

User target: complete preparation through closed testing; user handles production next. No production rollout is authorized or planned.

## Confirmed external resources

- Repository: https://github.com/fareza777/wifi-heatmap-3d (main)
- Play Console app created: https://play.google.com/console/u/0/developers/7590575640597683388/app/4974989146770480859/app-dashboard
- Package reserved: `com.f7developer.wifiheatmap3d` (original `com.sinyal.app` unavailable)
- Play title: `WiFi Heatmap 3D : Signal Map`; free app, English US, Tools category saved.
- AdMob app created: app numeric ID `1942300843`; app ID `ca-app-pub-6279186647593327~1942300843`
- Banner created: `ca-app-pub-6279186647593327/5053523223`
- Privacy: https://fareza777.github.io/wifi-heatmap-3d/privacy-policy.html (HTTP 200 verified)
- Root app-ads.txt: https://fareza777.github.io/app-ads.txt (HTTP 200, matching publisher pub-6279186647593327 verified)
- Support: fajar.mreza@gmail.com (same published business support contact as Vocatim)

## Pending external configuration (do not infer completion)

- Interstitial creation and ID
- AdMob privacy messages, privacy URL, app-ads.txt/domain verification
- Play privacy URL, ads/access/content/target audience/data safety/government/financial/health declarations
- Store contact details, tags, English and Indonesian listings, icon, feature graphic, 8 screenshots, promotion video URL
- One-time product `remove_ads`, US price USD 4.99, active buy option and country availability
- Signed AAB upload, closed testing countries and testers, release readiness
- YouTube upload of Remotion video (unlisted, embeddable, no monetization, for store preview)

The Chrome control connection currently returns `Debugger unattached`, including a fresh tab and reattachment attempt. Browser reconnection has been requested from the user. Re-read visible state before applying any pending action; none of the pending configuration above is claimed as saved.

## Completed local assets

- English and Indonesian ASO descriptions: title 28 characters; short descriptions 73 / 77; full descriptions 3,181 / 3,066.
- New icon: `marketing/icon/play-icon-512.png`; native adaptive launcher and About page use the new artwork.
- Feature graphic: `marketing/feature-graphic.png` (1024 × 500).
- Remotion promo: `marketing/wifi-heatmap-3d-promo.mp4` (42 seconds, 1920 × 1080, 30 fps, original stereo music). Real user-supplied 3D screenshot appears in the opening. Full video decode passed; checksum in `marketing/video-verification.json`.
- YouTube thumbnail, English/Indonesian captions, and prepared upload metadata included in `marketing/`.
- Eight narrated screenshots are in `marketing/screenshots/play-store/` (1080 × 1920), with the real user-supplied 3D result as the hero. Source provenance and reproduction instructions are in `marketing/screenshots/README.md`.

## Vocatim tester configuration, read-only source

Vocatim Alpha track uses four Google Groups, not email lists. Apply the same groups to WiFi Heatmap. Group addresses are kept in the ignored local operator notes, not in the public source repository.

Feedback: fajar.mreza@gmail.com. Vocatim showed 177 countries; review appropriate availability for new app. Do not modify Vocatim.

## Signing

Local upload key: `wifiheatmap-upload.jks`, alias `wifiheatmap-upload`. Passwords in ignored `release-signing.properties`. These must never be committed or published. Keep both files for future releases. Generated securely in this workspace.

## Validation so far

- Debug APK built, 10 JVM monetization regression tests passed.
- Android lint completed with zero errors after fixing missing Indonesian resources, platform API guards, permission handling, optional camera hardware and configuration-aware resource access. Early internal detector failures were followed by a successful stable-source run.
- Final APK build and signing verification passed, with 10 tests passing and Android lint reporting 0 errors, 86 warnings and 1 hint. Language splits are disabled for the in-app language selector; explicit cloud/device-transfer backup exclusions are included. Full evidence: `docs/development/monetization-validation.md`.
- Live Google Play checkout/restore and regional AdMob consent require store-configured testing; not yet verified.
- Physical AR walking accuracy cannot be validated in an emulator.
