# WiFi Heatmap 3D : Signal Map — promotional film

42-second English product film, 1920×1080, 30 fps. Six editable Remotion scenes. Landscape master is designed for YouTube and embedding in the Google Play listing.

## Reproduce

```powershell
npm ci
node scripts/create-audio.mjs
npx remotion studio --no-open
npx remotion render SignalMap-Promo out/wifi-heatmap-3d-promo.mp4 --codec=h264 --crf=18 --concurrency=2
npx remotion still FeatureGraphic out/feature-graphic.png
```

## Content and authenticity

- The opening hero uses the user's real 3D scan screenshot, framed to show its visualization and exclude phone status/navigation controls and the test-ad banner. The scan geometry is unchanged. It is labeled “ACTUAL 3D SCAN” and “ACTUAL APP · USER CAPTURE”.
- Explanatory room and floor graphics are original SVG animations and are visibly identified as concept visualizations.
- The phone showcase uses genuine application composables captured with sample fixtures, disclosed on screen. It does not claim measured performance.
- Feature copy covers walking scans, 2D/3D coverage, live signal, network details, channels, ping/speed tests, LAN/security checks, history and report exports.
- AR walking scan compatibility is explicitly limited to ARCore-supported devices.
- No speed improvement, security guarantee, endorsement, review, rating, download count, or public-availability claim is made.
- Brand end: “WiFi Heatmap 3D : Signal Map” and “Map your signal. Find your better spot.”

## Asset provenance

- `src/`: original React/SVG motion graphics.
- `scripts/create-audio.mjs`: original deterministic synthesized composition; produces a 42-second stereo WAV. No samples or third-party music are used. No speech or voice clone.
- `public/fonts/`: Plus Jakarta Sans fonts copied from this app's existing font resources.
- `public/icon.png`: the icon generated for this app in the parent marketing task.
- `public/screens/`: native app screenshots produced by the sibling screenshot task; sample data is disclosed.
- `public/screens/actual-3d.png`: user-supplied native scan screenshot (`image-2026-09-12T23-45-15-084Z.png`); the film clips its visible canvas in React without modifying the original asset.

The project pins Remotion 4.0.524 and React 19.2.3. Review Remotion's license for your organization before broader commercial use. The scaffold uses an unlicensed private package; this is not a declaration of ownership over third-party dependencies.

## Timeline

| Time | Scene |
| --- | --- |
| 00:00–00:06 | Your WiFi. In 3D. Real user scan. |
| 00:06–00:13 | Every step. A signal point. |
| 00:13–00:20 | Find the weak spots. |
| 00:20–00:28 | Know your connection. |
| 00:28–00:35 | A fuller picture. |
| 00:35–00:42 | Brand and tagline |

English and Indonesian subtitle sidecars are supplied for optional YouTube accessibility/localization. The master uses music and on-screen narrative copy, without voiceover.

## Verified delivery

`out/wifi-heatmap-3d-promo.mp4` is the completed master (9,409,569 bytes): H.264, 1920×1080, 30 fps, 1,260 video frames, full-range 4:2:0 color. Video duration is 42.000 s; AAC packet padding makes container duration 42.048 s. Audio is AAC stereo at 48 kHz.

All six scenes and both phone views were inspected in rendered frames. The finished MP4 was fully decoded without errors. `out/verification.json` contains metadata and SHA-256 checksum; rerun `node scripts/verify-output.mjs` to verify.

`out/youtube-thumbnail.png` is a 1920×1080 still extracted from the finished real-3D opening; `out/brand-thumbnail.png` is an alternate brand-ending still. `out/feature-graphic.png` is the verified 1024×500 Play banner; its deliverable copy is `../feature-graphic.png`.

The original music WAV has a −3.94 dBFS peak and no clipped samples. No voiceover is present. Source lint and TypeScript checks passed.

The scaffold's nested Git metadata is preserved in ignored `.scaffold-git-backup/` so the parent repository tracks the source normally. No commits or pushes were performed by this media task.

Final tracked copies are ../wifi-heatmap-3d-promo.mp4, ../youtube-thumbnail.png, and ../video-verification.json. Final video SHA-256: 0fdd5d8f04f9b8fc0dd6b6fc9f16855b4dc34d5127d9fde9a1b8f651af5b8bef.

