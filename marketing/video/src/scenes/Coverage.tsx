import { useCurrentFrame, interpolate } from "remotion";
import { Background, Word, Eyebrow, Footer, clamp } from "../Visuals";
export const Coverage = () => {
  const f = useCurrentFrame();
  return (
    <Background light>
      <div style={{ position: "absolute", left: 112, top: 132 }}>
        <Eyebrow dark>02 — READ YOUR COVERAGE</Eyebrow>
        <div style={{ marginTop: 50 }}>
          <Word style={{ fontSize: 108 }}>Find the weak spots.</Word>
        </div>
        <div style={{ fontSize: 34, marginTop: 30, color: "#52624f" }}>
          Explore your scan in 2D and 3D.
        </div>
      </div>
      <div
        style={{
          position: "absolute",
          left: 130,
          top: 505,
          width: 950,
          height: 370,
          rotate: interpolate(f, [0, 210], ["-6deg", "0deg"], clamp),
        }}
      >
        <svg viewBox="0 0 950 400" width="950" height="400">
          <defs>
            <radialGradient id="coverage">
              <stop stopColor="#b6e85d" />
              <stop offset=".42" stopColor="#cce052" />
              <stop offset=".7" stopColor="#e8b452" />
              <stop offset="1" stopColor="#ed7953" />
            </radialGradient>
          </defs>
          <rect width="940" height="380" rx="12" fill="url(#coverage)" />
          <g stroke="#fff" strokeOpacity=".25">
            {Array.from({ length: 24 }, (_, i) => (
              <line key={`a${i}`} x1={i * 40} y1="0" x2={i * 40} y2="380" />
            ))}
            {Array.from({ length: 10 }, (_, i) => (
              <line key={`b${i}`} x1="0" y1={i * 40} x2="940" y2={i * 40} />
            ))}
          </g>
          <path
            d="M4 4H936V376H4ZM410 4V140M410 230V376M650 4V170H936M4 240H225"
            fill="none"
            stroke="#26382c"
            strokeWidth="10"
          />
          <path
            d="M100 85H330V310H520V245H770V70"
            stroke="#fff"
            strokeWidth="4"
            strokeDasharray="10 12"
            fill="none"
            strokeDashoffset={-f}
          />
          {[
            [100, 85],
            [230, 85],
            [330, 85],
            [330, 180],
            [330, 310],
            [520, 310],
            [520, 245],
            [770, 245],
            [770, 70],
          ].map(([x, y], i) => (
            <circle key={i} cx={x} cy={y} r="6" fill="#fff" />
          ))}
        </svg>
      </div>
      <div style={{ position: "absolute", left: 1260, top: 515, width: 450 }}>
        <div style={{ fontSize: 78, fontWeight: 800, letterSpacing: -4 }}>
          Less guesswork.
        </div>
        <div
          style={{
            height: 9,
            marginTop: 42,
            background: "linear-gradient(90deg,#ed7953,#e8b452,#b6e85d)",
            borderRadius: 8,
          }}
        />
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            fontSize: 24,
            marginTop: 15,
            color: "#52624f",
          }}
        >
          <span>Weaker</span>
          <span>Stronger</span>
        </div>
      </div>
      <Footer chapter="03 / EXPLORE" visual dark />
    </Background>
  );
};
