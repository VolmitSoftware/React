package art.arcane.react.api.rendering;

import java.util.List;

/**
 * Renderers the registry may resolve on demand when an item names one that is not registered yet.
 */
public interface MapRendererCatalog {
  MapRendererCatalog NONE = (requested, requestedAlias) -> List.of();

  /**
   * Candidates in priority order: integration dashboards, samplers, then feature maps.
   */
  List<ReactRenderer> dynamicCandidates(String requested, String requestedAlias);
}
