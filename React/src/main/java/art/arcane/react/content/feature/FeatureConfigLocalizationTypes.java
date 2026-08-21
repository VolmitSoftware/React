package art.arcane.react.content.feature;

import java.util.List;

public final class FeatureConfigLocalizationTypes {
  private static final List<Class<?>> TYPES = List.of(FeatureChunkHeatmapBase.class);

  private FeatureConfigLocalizationTypes() {
  }

  public static List<Class<?>> types() {
    return TYPES;
  }
}
