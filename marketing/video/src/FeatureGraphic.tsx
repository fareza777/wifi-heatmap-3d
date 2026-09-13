import { AbsoluteFill, Img, staticFile } from "remotion";
export const FeatureGraphic = () => (
  <AbsoluteFill
    style={{
      background: "#05102b",
      color: "#f5f9ed",
      fontFamily: "Jakarta",
      overflow: "hidden",
    }}
  >
    <AbsoluteFill
      style={{
        background:
          "radial-gradient(ellipse at 68% 70%,#0e75634d,transparent 60%)",
      }}
    />
    <div
      style={{
        position: "absolute",
        left: 57,
        top: 60,
        fontSize: 18,
        letterSpacing: 3,
        color: "#b4d8c5",
        fontWeight: 700,
      }}
    >
      WiFi Heatmap 3D
    </div>
    <div
      style={{
        position: "absolute",
        left: 54,
        top: 155,
        fontSize: 67,
        fontWeight: 800,
        lineHeight: 1.09,
        letterSpacing: -2,
        zIndex: 1,
      }}
    >
      Make your
      <br />
      Wi-Fi <span style={{ color: "#c3ff6b", marginLeft: 10 }}>visible.</span>
    </div>
    <div
      style={{
        position: "absolute",
        left: 59,
        top: 387,
        fontSize: 19,
        color: "#a4c5b8",
      }}
    >
      Walk. Map. Understand.
    </div>
    <Img
      src={staticFile("icon.png")}
      style={{
        position: "absolute",
        width: 480,
        height: 480,
        right: -5,
        top: 10,
        objectFit: "contain",
        maskImage: "radial-gradient(ellipse,black 48%,transparent 73%)",
      }}
    />
  </AbsoluteFill>
);
