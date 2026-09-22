# Release status

## Update — 22 September 2026

Latest main source `c125861` built as **1.0.2 (version code 3)** because code 2 was already published on Alpha. Signed AAB accepted and submitted for 100% rollout on the existing closed Alpha track. Publishing overview confirmed **Changes in review**; preliminary checks and Google approval remain pending. Managed publishing is off. All 10 JVM tests passed, release lint vital and signing passed. See `docs/development/release-1.0.2.md` for checksum and validation evidence. Production was not changed.

## Initial release preparation — 13 September 2026

User target: complete preparation through closed testing; user handles production next. No production rollout is authorized or planned.

## Confirmed external resources

- Repository: https://github.com/fareza777/wifi-heatmap-3d (main)
- Play Console app created: https://play.google.com/console/u/0/developers/7590575640597683388/app/4974989146770480859/app-dashboard
- Package reserved: `com.f7developer.wifiheatmap3d` (original `com.sinyal.app` unavailable)
- Play title: `WiFi Heatmap 3D : Signal Map`; free app, English US, Tools category saved. Contact email, HTTPS website and Measurement, Network connectivity, Tools, Wi-fi tags saved.
- English and Indonesian listings saved and submitted for review: full ASO copy, icon, feature graphic, eight screenshots in the intended order, and promotion video URL. Indonesian uses the default visual assets. Icon, feature graphic and video have AI-asset labels; app screenshots retain their actual capture provenance.
- Privacy URL, ads, sign-in details, Advertising ID, target age 13+, government, financial, health and Data safety declarations saved. Dashboard setup completed. Data safety import source: `marketing/play-data-safety.csv`.
- IARC questionnaire submitted: utility app with in-app purchases; ESRB Everyone, PEGI 3, Brazil 14+, other ratings as shown by IARC. No developer-selected rating override.
- Closed Alpha track `4700499920695228359`: countries selected, four Google Groups matching Vocatim saved, feedback email saved.
- Signed version 1 (1.0.0), release `1.0.0 - Closed test`, accepted by Play and submitted with 16 changes. Publishing overview confirmed **Changes in review**; preliminary automated checks were still running. No production release was created. The sole bundle warning is missing native debug symbols; no blocking bundle errors.
- Tester opt-in URL: https://play.google.com/apps/testing/com.f7developer.wifiheatmap3d (availability awaits Google approval/publication of the closed track).
- One-time product `remove_ads`, purchase option `buy-remove-ads`, Buy/backwards compatible, verified **Active** in 173 countries/regions. US price USD 4.99 with Google-generated local prices. No subscription.
- YouTube promotion published unlisted: https://www.youtube.com/watch?v=kZE-gv5swDo . English timed subtitles uploaded; custom thumbnail and description saved. Copyright and community checks reported no issues. Embedding enabled.
- AdMob app created: app numeric ID `1942300843`; app ID `ca-app-pub-6279186647593327~1942300843`
- Banner created: `ca-app-pub-6279186647593327/5053523223`
- Interstitial created: `ca-app-pub-6279186647593327/3223592929`; server cap 1 impression per user per 3 minutes.
- AdMob European consent and US privacy-choice messages published for this app only. European message includes Consent, Do not consent in all regions, and Manage options. Public privacy URL saved in the app's messaging configuration. App integrates UMP and a privacy-options entry point.
- Privacy: https://fareza777.github.io/wifi-heatmap-3d/privacy-policy.html (HTTP 200 verified)
- Root app-ads.txt: https://fareza777.github.io/app-ads.txt (HTTP 200, matching publisher pub-6279186647593327 verified)
- Support: fajar.mreza@gmail.com (same published business support contact as Vocatim)

## Pending external configuration (do not infer completion)

- Google preliminary checks/review and closed-track publication; actual tester participation and test duration have not been completed by this preparation.
- AdMob store association, app readiness review and app-ads.txt verification await a discoverable Play listing. The public app-ads.txt file itself is verified.
- Payments profile shows a request to provide Singapore tax information to determine payout withholding. Product activation succeeded, but the account owner must supply accurate tax details; no tax declaration was invented or submitted.
- Optional Indonesian video captions remain local. English captions were uploaded. YouTube did not expose a video monetization control in the upload flow; no claim is made that an unavailable setting was changed.

The original Chrome tab lost its control connection. A fresh Play Console tab restored access; the saved setup and tester configuration were verified there. Re-read visible state before resuming pending actions.

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
- Signed release AAB built successfully with production ad IDs; release lint/R8 completed and JAR signature verified. Size 27,499,842 bytes; SHA-256 `B3CEC09F3388D764759D5225A7DD0E6035EB08A3F33686251BF246AEBB24B3BE`.
- Physical AR walking accuracy cannot be validated in an emulator.
