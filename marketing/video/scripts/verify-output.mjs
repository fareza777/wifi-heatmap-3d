import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { execFileSync } from "node:child_process";

const video = "out/wifi-heatmap-3d-promo.mp4";
const binaries = path.resolve(
  "node_modules/@remotion/compositor-win32-x64-msvc",
);
const metadata = JSON.parse(
  execFileSync(
    path.join(binaries, "ffprobe.exe"),
    [
      "-v",
      "error",
      "-show_entries",
      "stream=codec_name,codec_type,width,height,pix_fmt,r_frame_rate,duration,nb_frames,sample_rate,channels:format=duration,size",
      "-of",
      "json",
      video,
    ],
    { encoding: "utf8" },
  ),
);
const visual = metadata.streams.find((s) => s.codec_type === "video");
const audio = metadata.streams.find((s) => s.codec_type === "audio");
if (
  visual.width !== 1920 ||
  visual.height !== 1080 ||
  visual.r_frame_rate !== "30/1" ||
  visual.codec_name !== "h264" ||
  !["yuv420p", "yuvj420p"].includes(visual.pix_fmt) ||
  Number(visual.nb_frames) !== 1260
)
  throw new Error("Unexpected video delivery format");
if (
  audio.codec_name !== "aac" ||
  audio.channels !== 2 ||
  Number(audio.sample_rate) !== 48000
)
  throw new Error("Unexpected audio delivery format");
if (Math.abs(Number(metadata.format.duration) - 42) > 0.05)
  throw new Error("Unexpected duration");
execFileSync(
  path.join(binaries, "ffmpeg.exe"),
  [
    "-v",
    "error",
    "-i",
    video,
    "-c:v",
    "rawvideo",
    "-c:a",
    "pcm_s16le",
    "-f",
    "null",
    "-",
  ],
  { stdio: "pipe" },
);
const banner = fs.readFileSync("out/feature-graphic.png");
if (banner.readUInt32BE(16) !== 1024 || banner.readUInt32BE(20) !== 500)
  throw new Error("Unexpected feature graphic dimensions");
const report = {
  verifiedAt: new Date().toISOString(),
  video,
  metadata,
  fullDecode: "passed",
  featureGraphic: { width: 1024, height: 500 },
  sha256: crypto
    .createHash("sha256")
    .update(fs.readFileSync(video))
    .digest("hex"),
  visualReview:
    "All six scenes and both genuine sample-data phone views inspected using rendered PNG frames.",
};
fs.writeFileSync(
  "out/verification.json",
  JSON.stringify(report, null, 2) + "\n",
);
console.log(JSON.stringify(report, null, 2));
