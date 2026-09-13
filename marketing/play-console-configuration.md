# Google Play configuration reference

This is a preparation reference, not evidence these values have been saved in Play Console. Refer to RELEASE-STATUS.md.

## Identity and organization

Title: WiFi Heatmap 3D : Signal Map. Package: com.f7developer.wifiheatmap3d. App, free, Tools. Default en-US, Indonesian localization. Support email fajar.mreza@gmail.com. Website https://fareza777.github.io/wifi-heatmap-3d/. Privacy https://fareza777.github.io/wifi-heatmap-3d/privacy-policy.html.

Prefer relevant network/Wi-Fi/connectivity tags from the actual Play Console tag picker. Do not add unrelated popularity tags. Title 28 characters; check all short descriptions ≤80 and full descriptions ≤4,000.

## Content declarations

- Contains ads: yes (AdMob banners and interstitials; purchase removes them).
- Sign-in/app access: no account or login required. ARCore device compatibility and camera/location permissions are technical feature prerequisites. Purchases are optional; all measurement features accessible without purchase.
- Intended target ages: 13–15, 16–17, 18+. Not designed for children under 13.
- Government app: no. Financial features: none (selling an ad-removal purchase is not a finance feature). Health features: none.
- Content rating: utility/other-app questionnaire; no violence, sexual content, gambling, controlled substances, public social content or in-app user communication. In-app purchase exists. Review each exact question before answering; standard Android external sharing is distinct from an in-app social feed.

## Data safety basis

Review the actual current questionnaire and SDK documentation before submission. AdMob SDK 25.4.0 documents automatic collection/sharing of approximate location inferred from IP, app interactions, diagnostics, and device/account identifiers, for advertising, analytics and fraud prevention. UMP handles privacy selections. SDK data is not described as ephemeral. Removing ads prevents in-app ad requests; consent and regional behavior may also affect requests.

Google Play handles the optional purchase and the app uses purchase tokens and status locally for entitlement. No developer backend receives purchases. Do not state that card/payment details are received by F7 Developer. Assess the questionnaire's platform-payment exceptions for purchase history, rather than copying another app blindly.

Survey measurements, precise geographic anchor, room geometry, paths, Wi-Fi IDs and landmark photos remain in app-owned storage and are not automatically uploaded. Exclude on-device-only processing from collection declarations where the form instructs. User-initiated export is sent to the chosen destination. No app account creation, microphone recording, contacts collection, or developer analytics backend.

Speed tests contact Cloudflare with IP and request information; ping/DNS contact selected hosts; local discovery contacts local peers. Ad/billing/Cloudflare requests use encrypted transport, while diagnostic probes can be unencrypted. Do not make an unqualified “all data is encrypted” claim without applying the form's exact definitions.

Deletion: surveys can be removed from History; clear storage/uninstall removes all app-owned photos/settings. External exports/provider records are outside app control. No claim that F7 Developer can erase all AdMob records. No independent security certification.

Primary references checked 2026-09-13:

- https://developers.google.com/admob/android/privacy/play-data-disclosure
- https://developers.google.com/admob/android/privacy
- https://support.google.com/googleplay/android-developer/answer/10787469
- https://developers.google.com/ar/develop/privacy-requirements

## One-time product

Product ID `remove_ads`, non-consumable one-time purchase; English title “Remove Ads”, description “Remove all in-app banner and full-screen ads with one purchase.” Indonesian title “Hapus Iklan”, description “Hapus semua banner dan iklan layar penuh dalam aplikasi dengan sekali bayar.” US base price USD 4.99; use Play's localized prices and taxes, shown dynamically at checkout. Purchase restoration uses the same Play account. Do not create a subscription or charge for the free app itself.

## AdMob

App ID `ca-app-pub-6279186647593327~1942300843`, banner ID `ca-app-pub-6279186647593327/5053523223`. Interstitial ID still pending creation. Banner placement home/results; interstitial only after closing results with minimum three completed scans and 180 seconds elapsed, also blocked during immediate rescan. No app-open ads, no ads during camera capture or tests, no delayed surprise ad after navigation, no extra ad purchases required for tools.

Create regional consent/privacy messages in the AdMob app using the public policy URL and available reject/manage options; app uses UMP. Keep live units out of debug runs. Configure a server-side interstitial frequency cap as an additional protection. App store association/review and app-ads.txt verification may await a discoverable Play listing.
