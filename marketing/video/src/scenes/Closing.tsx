import { Img, staticFile, useCurrentFrame, interpolate } from "remotion";
import { Background, Word, green, clamp } from "../Visuals";
export const Closing = () => {
  const f = useCurrentFrame();
  return (
    <Background>
      <div
        style={{
          position: "absolute",
          width: 1000,
          height: 1000,
          left: 460,
          top: -100,
          borderRadius: "50%",
          background: "radial-gradient(circle,#c3ff6b0d,transparent 65%)",
        }}
      />
      {[0, 1, 2, 3].map((i) => (
        <div
          key={i}
          style={{
            position: "absolute",
            left: 960,
            top: 455,
            width: 400 + i * 250 + f,
            height: 400 + i * 250 + f,
            border: "1px solid #c3ff6b13",
            borderRadius: "50%",
            translate: "-50% -50%",
          }}
        />
      ))}
      <div
        style={{
          position: "absolute",
          top: 155,
          left: 0,
          width: "100%",
          display: "flex",
          justifyContent: "center",
          color: green,
          opacity: interpolate(f, [0, 24], [0, 1], clamp),
        }}
      >
        <Img
          src={staticFile("icon.png")}
          style={{ width: 150, height: 150, borderRadius: 30 }}
        />
      </div>
      <div
        style={{
          position: "absolute",
          left: 100,
          right: 100,
          top: 343,
          textAlign: "center",
        }}
      >
        <Word style={{ fontSize: 114 }}>WiFi Heatmap 3D</Word>
        <Word
          delay={12}
          style={{
            fontSize: 74,
            fontWeight: 400,
            letterSpacing: -2,
            color: green,
            marginTop: 10,
          }}
        >
          : Signal Map
        </Word>
        <div
          style={{
            fontSize: 43,
            lineHeight: 1.55,
            marginTop: 64,
            opacity: interpolate(f, [36, 62], [0, 1], clamp),
          }}
        >
          Map your signal. Find your better spot.
        </div>
        <div
          style={{
            marginTop: 55,
            fontSize: 23,
            letterSpacing: 4,
            color: "#809485",
            opacity: interpolate(f, [65, 85], [0, 1], clamp),
          }}
        >
          DESIGNED FOR ANDROID
        </div>
      </div>
    </Background>
  );
};
