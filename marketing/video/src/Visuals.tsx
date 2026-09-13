import {
  AbsoluteFill,
  Easing,
  Interactive,
  interpolate,
  useCurrentFrame,
} from "remotion";
import type { CSSProperties, ReactNode } from "react";
export const green = "#c3ff6b",
  cyan = "#64e8ef",
  muted = "#94a59d";
export const clamp = {
  extrapolateLeft: "clamp" as const,
  extrapolateRight: "clamp" as const,
};
export const ease = Easing.bezier(0.16, 1, 0.3, 1);
export const Background = ({
  children,
  light = false,
}: {
  children?: ReactNode;
  light?: boolean;
}) => (
  <AbsoluteFill
    style={{
      background: light ? "#e8ede1" : "#070b0d",
      color: light ? "#111c18" : "#f3f5ef",
      overflow: "hidden",
    }}
  >
    <AbsoluteFill
      style={{
        backgroundImage: light
          ? "none"
          : "radial-gradient(ellipse at 76% 36%,#17302880 0%,transparent 55%)",
      }}
    />
    {children}
  </AbsoluteFill>
);
export const Word = ({
  children,
  delay = 0,
  style = {},
}: {
  children: ReactNode;
  delay?: number;
  style?: CSSProperties;
}) => {
  const f = useCurrentFrame();
  return (
    <Interactive.Div
      name="Headline"
      style={{
        fontWeight: 800,
        fontSize: 116,
        letterSpacing: -4,
        lineHeight: 1.08,
        ...style,
        opacity: interpolate(f, [delay, delay + 20], [0, 1], clamp),
        translate: `0 ${interpolate(f, [delay, delay + 36], [65, 0], { ...clamp, easing: ease })}px`,
      }}
    >
      {children}
    </Interactive.Div>
  );
};
export const Eyebrow = ({
  children,
  dark = false,
}: {
  children: ReactNode;
  dark?: boolean;
}) => (
  <div
    style={{
      fontSize: 23,
      letterSpacing: 5,
      fontWeight: 700,
      color: dark ? "#536148" : green,
      display: "flex",
      alignItems: "center",
      gap: 18,
    }}
  >
    <span
      style={{
        width: 9,
        height: 9,
        background: dark ? "#536148" : green,
        borderRadius: 10,
      }}
    />
    {children}
  </div>
);
export const Caption = ({
  children,
  style = {},
}: {
  children: ReactNode;
  style?: CSSProperties;
}) => (
  <div style={{ fontSize: 33, lineHeight: 1.5, color: muted, ...style }}>
    {children}
  </div>
);
export const Footer = ({
  chapter,
  visual = false,
  dark = false,
}: {
  chapter: string;
  visual?: boolean;
  dark?: boolean;
}) => (
  <div
    style={{
      position: "absolute",
      left: 112,
      right: 112,
      bottom: 58,
      display: "flex",
      justifyContent: "space-between",
      fontSize: 19,
      color: dark ? "#52624f" : muted,
      letterSpacing: 2,
    }}
  >
    <span>WiFi Heatmap 3D</span>
    <span>
      {visual ? "CONCEPT VISUALIZATION  /  " : ""}
      {chapter}
    </span>
  </div>
);
const project = (x: number, y: number, z = 0) => [
  850 + (x - y) * 51,
  380 + (x + y) * 25 - z * 75,
];
const pts = (v: number[][]) =>
  v
    .map(([x, y, z]) => project(x, y, z))
    .map((p) => p.join(","))
    .join(" ");
export const Room = ({
  scan = false,
  top = false,
  dim = false,
}: {
  scan?: boolean;
  top?: boolean;
  dim?: boolean;
}) => {
  const f = useCurrentFrame();
  const reveal = interpolate(f, [8, 145], [0, 1], clamp);
  const scanpos = interpolate(f, [25, 165], [0, 1], clamp);
  const path = [
    [1, 1],
    [3, 1],
    [5, 1],
    [7, 2],
    [7, 4],
    [5, 4],
    [3, 4],
    [1, 4],
    [1, 6],
    [3, 7],
    [5, 7],
    [7, 7],
  ];
  const index = Math.min(
    path.length - 1,
    Math.floor(scanpos * (path.length - 1)),
  );
  const fraction = scanpos * (path.length - 1) - index;
  const nextPoint = path[Math.min(path.length - 1, index + 1)];
  const cursor = project(
    path[index][0] + (nextPoint[0] - path[index][0]) * fraction,
    path[index][1] + (nextPoint[1] - path[index][1]) * fraction,
    0.15,
  );
  return (
    <svg
      viewBox="0 0 1700 1100"
      width="100%"
      height="100%"
      style={{ filter: "drop-shadow(0 45px 60px #0009)" }}
    >
      <defs>
        <linearGradient id="wall" x1="0" y1="0" x2="0" y2="1">
          <stop stopColor="#aec7b4" stopOpacity=".17" />
          <stop offset="1" stopColor="#29483c" stopOpacity=".35" />
        </linearGradient>
        <radialGradient id="glow">
          <stop stopColor={green} stopOpacity=".5" />
          <stop offset="1" stopColor={green} stopOpacity="0" />
        </radialGradient>
      </defs>
      <ellipse
        cx="850"
        cy="750"
        rx="610"
        ry="160"
        fill="url(#glow)"
        opacity=".2"
      />
      <polygon
        points={pts([
          [0, 0, -0.12],
          [9, 0, -0.12],
          [9, 9, -0.12],
          [0, 9, -0.12],
        ])}
        fill="#0d1c18"
        stroke="#4b705c"
        strokeWidth="2"
      />
      {Array.from({ length: 18 }, (_, a) =>
        Array.from({ length: 18 }, (_, b) => {
          const x = a / 2,
            y = b / 2;
          const d = Math.hypot(x - 1.5, y - 2);
          const hue = 110 - Math.min(1, d / 9) * 108;
          const strength = dim ? 0.12 : 0.8;
          const opacity = (scan ? (a + b) / 36 < reveal : true)
            ? strength
            : 0.07;
          return (
            <polygon
              key={`${a}-${b}`}
              points={pts([
                [x, y, 0],
                [x + 0.475, y, 0],
                [x + 0.475, y + 0.475, 0],
                [x, y + 0.475, 0],
              ])}
              fill={`hsl(${hue},79%,58%)`}
              opacity={opacity}
            />
          );
        }),
      )}
      {!top && (
        <>
          <polygon
            points={pts([
              [0, 0, 0],
              [9, 0, 0],
              [9, 0, 2.8],
              [0, 0, 2.8],
            ])}
            fill="url(#wall)"
            stroke="#829e8b"
            strokeWidth="2"
          />
          <polygon
            points={pts([
              [0, 0, 0],
              [0, 9, 0],
              [0, 9, 2.8],
              [0, 0, 2.8],
            ])}
            fill="url(#wall)"
            stroke="#829e8b"
            strokeWidth="2"
          />
          <polygon
            points={pts([
              [5, 0, 0],
              [5, 3.2, 0],
              [5, 3.2, 2.2],
              [5, 0, 2.2],
            ])}
            fill="url(#wall)"
            stroke="#698470"
            strokeWidth="2"
          />
          <polygon
            points={pts([
              [0, 5, 0],
              [3.4, 5, 0],
              [3.4, 5, 2.2],
              [0, 5, 2.2],
            ])}
            fill="url(#wall)"
            stroke="#698470"
            strokeWidth="2"
          />
        </>
      )}
      <polyline
        points={pts([
          [0, 9, 0],
          [9, 9, 0],
          [9, 0, 0],
        ])}
        fill="none"
        stroke="#a0c38b"
        strokeWidth="3"
      />
      {scan && (
        <>
          <polyline
            points={path
              .slice(0, index + 1)
              .map((p) => project(p[0], p[1], 0.1).join(","))
              .join(" ")}
            fill="none"
            stroke="#fff"
            strokeWidth="4"
            strokeDasharray="9 9"
          />
          {path.slice(0, index + 1).map((p, i) => (
            <circle
              key={i}
              cx={project(p[0], p[1], 0.1)[0]}
              cy={project(p[0], p[1], 0.1)[1]}
              r="6"
              fill="#fff"
            />
          ))}
          <circle cx={cursor[0]} cy={cursor[1]} r="19" fill={cyan} />
          <circle
            cx={cursor[0]}
            cy={cursor[1]}
            r={25 + (f % 35)}
            stroke={cyan}
            fill="none"
            opacity={1 - (f % 35) / 35}
          />
          <line
            x1={cursor[0]}
            y1={cursor[1]}
            x2={cursor[0]}
            y2={cursor[1] - 140}
            stroke={cyan}
            strokeWidth="3"
          />
        </>
      )}
      {!scan &&
        [0, 1, 2, 3].map((i) => {
          const r = (f * 1.4 + i * 80) % 320;
          return (
            <ellipse
              key={i}
              cx={project(1.4, 1.8, 0.1)[0]}
              cy={project(1.4, 1.8, 0.1)[1]}
              rx={r}
              ry={r * 0.49}
              fill="none"
              stroke={green}
              strokeWidth="2"
              opacity={(1 - r / 320) * 0.55}
            />
          );
        })}
      <circle
        cx={project(1.4, 1.8, 0.1)[0]}
        cy={project(1.4, 1.8, 0.1)[1]}
        r="12"
        fill="#fff"
      />
    </svg>
  );
};
export const SignalMark = ({ size = 100 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 100 100">
    <path
      d="M15 34Q50 4 85 34M28 49Q50 29 72 49M40 64Q50 55 60 64"
      fill="none"
      stroke="currentColor"
      strokeWidth="9"
      strokeLinecap="round"
    />
    <circle cx="50" cy="79" r="6" fill="currentColor" />
  </svg>
);
