import { Img, staticFile, useCurrentFrame, interpolate } from "remotion";
import { Background, Word, Eyebrow, Footer, green, clamp } from "../Visuals";

// The image remains an unaltered, user-supplied native application capture.
// Editorial clipping shows only its 3D canvas, excluding status/navigation/ad UI.
export const Opening = () => {
  const f = useCurrentFrame();
  return (
    <Background>
      <div
        style={{
          position: "absolute",
          left: 112,
          top: 165,
          width: 760,
          zIndex: 2,
        }}
      >
        <Eyebrow>THE INVISIBLE. MADE VISIBLE.</Eyebrow>
        <div style={{ marginTop: 70 }}>
          <Word style={{ fontSize: 143 }}>Your WiFi.</Word>
          <Word
            delay={25}
            style={{ fontSize: 168, color: green, marginTop: 10 }}
          >
            In 3D.
          </Word>
        </div>
        <div
          style={{
            fontSize: 34,
            lineHeight: 1.5,
            color: "#a8b8ad",
            marginTop: 58,
            opacity: interpolate(f, [65, 95], [0, 1], clamp),
          }}
        >
          See the coverage
          <br />
          around you.
        </div>
      </div>
      <div
        style={{
          position: "absolute",
          left: 910,
          top: 155,
          width: 920,
          height: 770,
          overflow: "hidden",
          borderRadius: 32,
          background: "#000",
          border: "1px solid #294035",
          boxShadow: "0 40px 80px #0006",
          opacity: interpolate(f, [10, 40], [0, 1], clamp),
          translate: `0 ${interpolate(f, [0, 50], [45, 0], clamp)}px`,
        }}
      >
        <div
          style={{
            position: "absolute",
            left: 0,
            top: 0,
            width: 920,
            height: 770,
            overflow: "hidden",
            scale: interpolate(f, [0, 180], [1, 1.025], clamp),
          }}
        >
          <Img
            src={staticFile("screens/actual-3d.png")}
            style={{
              position: "absolute",
              left: 0,
              top: -735,
              width: 920,
              height: 2044.444444,
            }}
          />
        </div>
        <div
          style={{
            position: "absolute",
            left: 28,
            top: 25,
            fontSize: 18,
            fontWeight: 700,
            letterSpacing: 3,
            color: "#99bda6",
          }}
        >
          ACTUAL 3D SCAN
        </div>
      </div>
      <Footer chapter="ACTUAL APP · USER CAPTURE / 01" />
    </Background>
  );
};
