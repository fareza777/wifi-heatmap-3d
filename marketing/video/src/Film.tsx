import {
  AbsoluteFill,
  staticFile,
  useCurrentFrame,
  interpolate,
} from "remotion";
import { Audio } from "@remotion/media";
import { TransitionSeries } from "@remotion/transitions";
import { Opening } from "./scenes/ActualOpening";
import { Walking } from "./scenes/Walking";
import { Coverage } from "./scenes/Coverage";
import { Product } from "./scenes/Product";
import { Tools } from "./scenes/Tools";
import { Closing } from "./scenes/Closing";
export const Film = () => {
  const frame = useCurrentFrame();
  return (
    <AbsoluteFill style={{ background: "#070b0d", color: "#f3f5ef" }}>
      <TransitionSeries>
        <TransitionSeries.Sequence
          durationInFrames={180}
          name="01 - Reveal the invisible"
        >
          <Opening />
        </TransitionSeries.Sequence>
        <TransitionSeries.Sequence
          durationInFrames={210}
          name="02 - Walk your space"
        >
          <Walking />
        </TransitionSeries.Sequence>
        <TransitionSeries.Sequence
          durationInFrames={210}
          name="03 - Find the weak spots"
        >
          <Coverage />
        </TransitionSeries.Sequence>
        <TransitionSeries.Sequence durationInFrames={240} name="04 - Real app">
          <Product />
        </TransitionSeries.Sequence>
        <TransitionSeries.Sequence
          durationInFrames={210}
          name="05 - Understand your network"
        >
          <Tools />
        </TransitionSeries.Sequence>
        <TransitionSeries.Sequence durationInFrames={210} name="06 - Brand">
          <Closing />
        </TransitionSeries.Sequence>
      </TransitionSeries>
      <Audio src={staticFile("audio/signal-original.wav")} volume={0.8} />
      <AbsoluteFill
        style={{
          pointerEvents: "none",
          background: "#070b0d",
          opacity: interpolate(frame, [0, 14, 1238, 1259], [1, 0, 0, 1], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          }),
        }}
      />
    </AbsoluteFill>
  );
};
