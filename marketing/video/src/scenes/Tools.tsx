import { useCurrentFrame, interpolate } from "remotion";
import { Background, Word, Eyebrow, Footer, green, clamp } from "../Visuals";
export const Tools = () => {
  const f = useCurrentFrame();
  return (
    <Background>
      <div style={{ position: "absolute", left: 112, top: 130 }}>
        <Eyebrow>04 — GO BEYOND THE BARS</Eyebrow>
        <div style={{ marginTop: 48 }}>
          <Word style={{ fontSize: 106 }}>A fuller picture.</Word>
        </div>
      </div>
      <div
        style={{
          position: "absolute",
          left: 112,
          right: 112,
          top: 430,
          display: "flex",
          gap: 54,
        }}
      >
        {[
          {
            n: "01",
            title: "Measure",
            body: "Ping & speed tests",
            icon: "wave",
          },
          {
            n: "02",
            title: "Inspect",
            body: "LAN & security checks",
            icon: "network",
          },
          {
            n: "03",
            title: "Keep",
            body: "History & report exports",
            icon: "file",
          },
        ].map((item, i) => (
          <div
            key={item.n}
            style={{
              flex: 1,
              opacity: interpolate(
                f,
                [16 + i * 24, 38 + i * 24],
                [0, 1],
                clamp,
              ),
              translate: `0 ${interpolate(f, [16 + i * 24, 50 + i * 24], [50, 0], clamp)}px`,
            }}
          >
            <div
              style={{
                height: 200,
                borderTop: "1px solid #385041",
                position: "relative",
              }}
            >
              <div style={{ fontSize: 21, color: "#748d7b", paddingTop: 24 }}>
                {item.n}
              </div>
              <svg
                style={{ position: "absolute", left: 160, top: 45 }}
                width="260"
                height="140"
                viewBox="0 0 260 140"
              >
                {item.icon === "wave" ? (
                  <path
                    d="M0 76H42L58 35L84 118L108 9L134 83H159L179 49L197 76H260"
                    stroke={green}
                    strokeWidth="5"
                    fill="none"
                    strokeDasharray="600"
                    strokeDashoffset={interpolate(f, [25, 95], [600, 0], clamp)}
                  />
                ) : item.icon === "network" ? (
                  <g stroke={green} strokeWidth="3" fill="#132318">
                    <path d="M130 60L40 110M130 60L220 110M130 60V10" />
                    <circle cx="130" cy="60" r="27" />
                    <circle cx="40" cy="110" r="13" />
                    <circle cx="220" cy="110" r="13" />
                    <circle cx="130" cy="10" r="9" />
                  </g>
                ) : (
                  <g fill="none" stroke={green} strokeWidth="4">
                    <rect x="81" y="4" width="105" height="126" rx="9" />
                    <path d="M106 37H162M106 60H162M106 83H145M106 106H150" />
                  </g>
                )}
              </svg>
            </div>
            <div
              style={{
                fontSize: 59,
                fontWeight: 800,
                letterSpacing: -2,
                marginTop: 24,
              }}
            >
              {item.title}
            </div>
            <div style={{ fontSize: 28, color: "#a3b6a7", marginTop: 22 }}>
              {item.body}
            </div>
          </div>
        ))}
      </div>
      <Footer chapter="05 / UNDERSTAND" />
    </Background>
  );
};
