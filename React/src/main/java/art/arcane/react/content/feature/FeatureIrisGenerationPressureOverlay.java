package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.web.heatmap.HeatmapWorldRef;
import art.arcane.react.api.feature.CapabilityGatedFeature;
import art.arcane.react.core.controller.IntegrationController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.util.data.TinyColor;

import java.util.Set;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Iris Generation Pressure Overlay feature. This feature continuously monitors server behavior and applies guardrails during runtime.")
public class FeatureIrisGenerationPressureOverlay extends FeatureChunkHeatmapBase implements CapabilityGatedFeature {
  public static final String ID = "iris-generation-pressure-overlay";

  public FeatureIrisGenerationPressureOverlay() {
    super(ID);
  }

  @Override
  protected String mapLabel() {
    return ReactLanguage.raw(RendererMessages.TITLE_IRIS_PRESSURE);
  }

  @Override
  protected TinyColor headerColor() {
    return new TinyColor(62, 136, 96);
  }

  @Override
  protected TinyColor backgroundColor() {
    return new TinyColor(6, 14, 12);
  }

  @Override
  protected double chunkScore(HeatmapWorldRef world, int chunkX, int chunkZ) {
    if (world == null || !isManagedWorld(world)) {
      return 0D;
    }
    return chunkTotalScore(world, chunkX, chunkZ);
  }

  @Override
  protected TinyColor colorFor(double normalized, double rawScore) {
    return gradient(normalized, new TinyColor(40, 110, 80), new TinyColor(255, 180, 60));
  }

  @Override
  public Set<String> requiredCapabilities() {
    return Set.of("iris");
  }

  private boolean isManagedWorld(HeatmapWorldRef world) {
    IntegrationController controller = React.controller(IntegrationController.class);
    if (controller == null || controller.getRemoteSamplerBridge() == null) {
      return false;
    }
    return controller.getRemoteSamplerBridge().getGroup(
        "iris",
        "world",
        world.worldKey()
    ) != null;
  }
}
