import "./index.css";
import { Composition, Still } from "remotion";
import { Film } from "./Film";
import { FeatureGraphic } from "./FeatureGraphic";
export const RemotionRoot = () => (
  <>
    <Composition
      id="SignalMap-Promo"
      component={Film}
      durationInFrames={1260}
      fps={30}
      width={1920}
      height={1080}
    />
    <Still
      id="FeatureGraphic"
      component={FeatureGraphic}
      width={1024}
      height={500}
    />
  </>
);
