# Wi-Fi Heatmap 3D — Play Store screenshots

Eight English portrait PNGs, 1080 × 1920, in `play-store/`. Source app captures are preserved in `originals/`. The real-phone hero source is 720 × 1600; the other captured sources are 1080 × 1920.

The final store images are direct Android captures of an original native editorial layout. Image 01 incorporates a real-phone screenshot supplied by the user (`image-2026-09-12T23-45-15-084Z.png`). Its 3D geometry is preserved; the frame clips the phone's status/navigation bars, the test advertisement and excess black space while retaining the real **Show details** control. Its disclosure reads **Actual app · User capture**.

Images 02–07 render the production Compose screens with deterministic illustrative fixtures and disclose **Sample survey · Illustrative data**. The 2D image uses the app's actual `MapRenderer` export. Image 08 shows actual settings. No generated product UI, replacement 3D geometry, fictional controls or performance guarantees are used.

## Order

1. 3D coverage — explore signal coverage in a room view.
2. Map export — share a colour-coded 2D coverage map.
3. Signal trends — compare strength over time.
4. Channel analysis — inspect nearby channel overlap.
5. Security check — review grades and warnings.
6. Ping test — inspect latency, jitter and packet loss.
7. Survey history — revisit saved scans by date.
8. Appearance — choose theme, accent and language.

## Reproduce

Build and install the debug APK on a test emulator, then run `capture.ps1`. The debug Activity, hero asset and sample data do not ship in release builds. Illustrative network identifiers use locally administered fictional MAC addresses. Image 01 is a user-provided real scan; its visible crop contains room geometry and an app control, with no network identifier, location, account or phone notification data.

For the final framing, run `capture.ps1 -Serial emulator-5556 -FramedOnly`, followed by `python capture_detail_views.py` to scroll the channel, security and ping screens to their detailed results. The detail helper currently targets emulator-5556; adjust its ADB serial for another test device. The security view received a final normal touch scroll to align its first grade card. Finally, run `python verify_and_package.py` to validate all eight dimensions, losslessly encode opaque RGB PNGs, record SHA-256 checksums, and regenerate the contact sheet and upload ZIP.

## Verification

All eight final images were individually inspected and the complete contact sheet was approved. `manifest.json` records each image's dimensions, provenance, file size and checksum. `play-store-screenshots.zip` contains only the eight store PNGs and their manifest. `contact-sheet.png` is a review aid, not an upload screenshot. Temporary emulator logs and diagnostic captures live in the ignored `.cache/` directory. The capture emulator has been shut down.

The screenshots describe existing capabilities and make no ratings, install-count, guaranteed-speed, or unsupported-device claims. 3D scanning depends on device AR support; the supplied 3D result was captured in the actual app on the user's phone.
