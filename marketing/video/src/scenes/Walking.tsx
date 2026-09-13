import { useCurrentFrame, interpolate } from "remotion";
import {
  Background,
  Word,
  Eyebrow,
  Room,
  Footer,
  Caption,
  green,
  clamp,
} from "../Visuals";
export const Walking = () => {
  const f = useCurrentFrame();
  return (
    <Background>
      <div style={{ position: "absolute", left: 112, top: 154, width: 750 }}>
        <Eyebrow>01 — WALK & SCAN</Eyebrow>
        <div style={{ marginTop: 60 }}>
          <Word>Every step.</Word>
          <Word delay={15} style={{ color: green }}>
            A signal point.
          </Word>
        </div>
        <Caption style={{ marginTop: 40, maxWidth: 600 }}>
          Walk through your room.
          <br />
          Build a spatial WiFi scan.
        </Caption>
        <div style={{ marginTop: 72, fontSize: 21, color: "#7e9187" }}>
          AR walking scans require an ARCore-supported device.
        </div>
      </div>
      <div
        style={{
          position: "absolute",
          width: 1340,
          height: 1000,
          right: -220,
          top: 60,
          translate: `${interpolate(f, [0, 35], [100, 0], clamp)}px 0`,
        }}
      >
        <Room scan />
      </div>
      <Footer chapter="02 / SCAN" visual />
    </Background>
  );
};
