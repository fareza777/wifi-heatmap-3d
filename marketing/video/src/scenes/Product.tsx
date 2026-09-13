import { Img, staticFile, useCurrentFrame, interpolate } from "remotion";
import {
  Background,
  Word,
  Eyebrow,
  Footer,
  Caption,
  green,
  clamp,
  SignalMark,
} from "../Visuals";
import { screens } from "../screens";
export const Product = () => {
  const f = useCurrentFrame();
  const screen = screens[Math.min(screens.length - 1, Math.floor(f / 120))];
  return (
    <Background>
      <div style={{ position: "absolute", left: 112, top: 185, width: 870 }}>
        <Eyebrow>03 — LOOK CLOSER</Eyebrow>
        <div style={{ marginTop: 62 }}>
          <Word>Know your</Word>
          <Word delay={16} style={{ color: green }}>
            connection.
          </Word>
        </div>
        <Caption style={{ marginTop: 46 }}>
          Live signal. Network details.
          <br />
          Clarity in your hand.
        </Caption>
        <div style={{ display: "flex", gap: 15, marginTop: 64 }}>
          {["LIVE dBm", "CHANNELS", "HISTORY"].map((t) => (
            <div
              key={t}
              style={{
                border: "1px solid #385040",
                padding: "14px 23px",
                borderRadius: 30,
                fontSize: 19,
                letterSpacing: 2,
                color: "#b0c2b4",
              }}
            >
              {t}
            </div>
          ))}
        </div>
      </div>
      <div
        style={{
          position: "absolute",
          left: 1165,
          top: 105,
          width: 485,
          height: 847,
          borderRadius: 55,
          padding: 10,
          background:
            "linear-gradient(130deg,#c2cdc3,#303b36 20%,#85998b 60%,#18241b)",
          boxShadow: "0 65px 100px #000a",
          rotate: interpolate(f, [0, 240], ["7deg", "-3deg"], clamp),
          translate: `0 ${interpolate(f, [0, 40], [75, 0], clamp)}px`,
        }}
      >
        <div
          style={{
            width: "100%",
            height: "100%",
            borderRadius: 46,
            overflow: "hidden",
            position: "relative",
            background: "#0b1512",
          }}
        >
          {screen ? (
            <Img
              src={staticFile(screen)}
              style={{ width: "100%", height: "100%", objectFit: "contain" }}
            />
          ) : (
            <div
              style={{
                height: "100%",
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                justifyContent: "center",
                gap: 40,
                color: green,
              }}
            >
              <SignalMark size={140} />
              <div
                style={{ fontWeight: 800, fontSize: 40, textAlign: "center" }}
              >
                WiFi Heatmap
                <br />
                3D
              </div>
            </div>
          )}
        </div>
      </div>
      <div
        style={{
          position: "absolute",
          left: 112,
          top: 815,
          fontSize: 21,
          letterSpacing: 2,
          color: "#94a59d",
        }}
      >
        ACTUAL APP · SAMPLE DATA
      </div>
      <Footer chapter="04 / CONNECT" />
    </Background>
  );
};

