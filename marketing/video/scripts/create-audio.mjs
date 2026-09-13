import fs from "node:fs";
// Original procedural composition: D-minor ambient pulse, 120 BPM.
// No samples, recordings, or third-party copyrighted music.
const sr = 48000,
  seconds = 42,
  N = sr * seconds,
  buffer = new Float64Array(N);
const add = (start, dur, fn, gain = 1) => {
  for (let k = 0; k < Math.floor(dur * sr); k++) {
    const i = Math.floor(start * sr) + k;
    if (i < N) buffer[i] += fn(k / sr, dur) * gain;
  }
};
const hz = (n) => 440 * 2 ** ((n - 69) / 12);
const chords = [
  [50, 57, 65, 69],
  [46, 53, 60, 65],
  [53, 60, 65, 69],
  [48, 55, 62, 67],
];
for (let bar = 0; bar < 10; bar++) {
  const start = bar * 4;
  chords[bar % 4].forEach((n) =>
    add(
      start,
      5,
      (t, d) =>
        Math.sin(2 * Math.PI * hz(n) * t) *
        Math.min(1, t / 0.8) *
        Math.max(0, 1 - t / d) *
        0.032,
    ),
  );
}
let seed = 9328;
const noise = () => {
  seed = (seed * 1664525 + 1013904223) >>> 0;
  return seed / 2147483648 - 1;
};
for (let beat = 0; beat < 76; beat++) {
  const start = (beat * 60) / 120;
  if (start > 38) break;
  add(
    start,
    0.42,
    (t) =>
      Math.sin(2 * Math.PI * (48 * t + (50 * (1 - Math.exp(-t * 25))) / 25)) *
      Math.exp(-t * 12),
    0.26,
  );
  if (beat % 2 === 1)
    add(start, 0.14, (t) => noise() * Math.exp(-t * 30), 0.075);
  add(start + 0.25, 0.045, (t) => noise() * Math.exp(-t * 90), 0.035);
  if (beat > 9) {
    const notes = [74, 77, 81, 72, 77, 69, 72, 65];
    add(
      start,
      0.65,
      (t) =>
        Math.sin(2 * Math.PI * hz(notes[beat % 8]) * t) *
        Math.exp(-t * 7) *
        (1 - Math.exp(-t * 100)),
      0.075,
    );
  }
}
for (const start of [5.65, 12.65, 19.65, 27.65, 34.65])
  add(start, 0.7, (t, d) => noise() * Math.sin((Math.PI * t) / d) ** 2, 0.04);
const out = Buffer.alloc(44 + N * 4);
out.write("RIFF");
out.writeUInt32LE(36 + N * 4, 4);
out.write("WAVE", 8);
out.write("fmt ", 12);
out.writeUInt32LE(16, 16);
out.writeUInt16LE(1, 20);
out.writeUInt16LE(2, 22);
out.writeUInt32LE(sr, 24);
out.writeUInt32LE(sr * 4, 28);
out.writeUInt16LE(4, 32);
out.writeUInt16LE(16, 34);
out.write("data", 36);
out.writeUInt32LE(N * 4, 40);
let peak = 0;
for (const v of buffer) peak = Math.max(peak, Math.abs(v));
for (let i = 0; i < N; i++) {
  const t = i / sr,
    fade = Math.min(1, t / 1.2, (seconds - t) / 2.3);
  const v = Math.tanh((buffer[i] / peak) * 0.75) * fade;
  out.writeInt16LE(Math.round(v * 32767), 44 + i * 4);
  const delayed = (buffer[Math.max(0, i - 480)] / peak) * 0.08;
  out.writeInt16LE(
    Math.round(Math.tanh(v + delayed * fade) * 32767),
    46 + i * 4,
  );
}
fs.mkdirSync("public/audio", { recursive: true });
fs.writeFileSync("public/audio/signal-original.wav", out);
console.log("Original 42-second stereo score created.");
