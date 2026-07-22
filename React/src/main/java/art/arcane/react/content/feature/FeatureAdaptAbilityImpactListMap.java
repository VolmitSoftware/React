package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.core.controller.MapController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;

import java.util.List;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for the Adapt Ability Impact List Map feature.")
public class FeatureAdaptAbilityImpactListMap extends ReactFeature implements ReactRenderer {
  public static final String ID = "adapt-ability-impact-list-map";
  private static final TinyColor ACCENT = new TinyColor(112, 184, 108);
  private static final TinyColor FRAME_VALUE = new TinyColor(255, 196, 92);

  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum Adapt abilities shown on a held map.", impact = "Higher values show more abilities with denser rows; lower values improve readability.")
  private int maxEntries = 6;
  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum Adapt abilities shown when the map is placed in an item frame.", impact = "Lower values improve readability from a distance.")
  private int frameMaxEntries = 5;

  public FeatureAdaptAbilityImpactListMap() {
    super(ID);
  }

  @Override
  public void render() {
    boolean frameAnchored = isFrameAnchored();
    AdaptAbilityImpactSeries.Snapshot snapshot = AdaptAbilityImpactSeries.snapshot();
    drawBackdrop();
    dashHeader(ReactLanguage.raw(RendererMessages.TITLE_ADAPT_CALLBACK_COST), topLabel(snapshot), ACCENT, FRAME_VALUE);

    List<AdaptAbilityImpactSeries.Entry> entries = snapshot.entries();
    if (entries.isEmpty()) {
      drawEmptyState(snapshot);
      return;
    }

    int rowHeight = 17;
    int startY = 17;
    int rowsBySpace = Math.max(1, (119 - startY) / rowHeight);
    int configuredLimit = Math.max(1, frameAnchored ? frameMaxEntries : maxEntries);
    int limit = Math.min(Math.min(configuredLimit, entries.size()), rowsBySpace);
    double maxTiming = Math.max(0D, entries.getFirst().executionTimingMs());
    double maxExecutionOps = entries.stream()
        .mapToDouble(AdaptAbilityImpactSeries.Entry::executionOps)
        .max()
        .orElse(0D);
    int y = startY;

    for (int i = 0; i < limit; i++) {
      AdaptAbilityImpactSeries.Entry entry = entries.get(i);
      double normalized = normalizedImpact(entry, maxTiming, maxExecutionOps);
      drawRowCard(y, rowHeight, frameAnchored);
      drawRow(i, y, normalized, entry, frameAnchored);
      y += rowHeight;
    }
  }

  private void drawBackdrop() {
    for (int y = 0; y < height(); y++) {
      double normalized = y / (double) Math.max(1, height() - 1);
      set(0, y, width(), 1, gradient(normalized, new TinyColor(10, 17, 14), new TinyColor(18, 28, 23)));
    }
  }

  private void drawEmptyState(AdaptAbilityImpactSeries.Snapshot snapshot) {
    if (snapshot.aggregateAvailable() && snapshot.aggregateGuardChecks() > 0D) {
      text(4, 22, ReactLanguage.raw(RendererMessages.ADAPT_MEASURED_UNAVAILABLE), TEXT_BRIGHT);
      text(4, 34, ReactLanguage.raw(RendererMessages.ADAPT_GUARD_ONLY), TEXT_DIM);
      return;
    }

    text(4, 22, ReactLanguage.raw(RendererMessages.ADAPT_NO_ACTIVITY), TEXT_BRIGHT);
    text(4, 34, ReactLanguage.raw(RendererMessages.ADAPT_USE_ABILITIES), TEXT_DIM);
  }

  private void drawRowCard(int y, int rowHeight, boolean frameAnchored) {
    TinyColor panel = frameAnchored ? new TinyColor(18, 30, 24) : new TinyColor(15, 26, 21);
    TinyColor border = frameAnchored ? new TinyColor(48, 72, 58) : new TinyColor(34, 54, 43);
    int cardHeight = rowHeight - 1;
    set(2, y - 1, 124, cardHeight, panel);
    set(2, y - 1, 124, 1, border);
    set(2, y + cardHeight - 1, 124, 1, border);
  }

  private void drawRow(
      int rank,
      int y,
      double normalized,
      AdaptAbilityImpactSeries.Entry entry,
      boolean frameAnchored
  ) {
    text(4, y, "#" + (rank + 1), TEXT_DIM);
    int nameWidth = frameAnchored ? 98 : 96;
    text(19, y, trimToWidth(entry.displayName(), nameWidth), TEXT_BRIGHT);

    int barX = 75;
    int barY = y + 8;
    int barWidth = 47;
    int fill = (int) Math.round(barWidth * normalized);
    set(barX, barY, barWidth, 4, new TinyColor(24, 38, 31));
    if (fill > 0) {
      set(barX, barY, fill, 4, rankColor(rank, normalized));
    }

    String value = ReactLanguage.raw(
        RendererMessages.ADAPT_CALLBACK_VALUE,
        MessageArgument.untrusted("timing", compact(entry.executionTimingMs())),
        MessageArgument.untrusted("operations", compact(entry.executionOps()))
    );
    text(4, y + 7, trimToWidth(value, barX - 7), TEXT_DIM);
  }

  private double normalizedImpact(
      AdaptAbilityImpactSeries.Entry entry,
      double maxTiming,
      double maxExecutionOps
  ) {
    if (maxTiming > 0D) {
      return Math.max(0D, Math.min(1D, entry.executionTimingMs() / maxTiming));
    }
    if (maxExecutionOps > 0D) {
      return Math.max(0D, Math.min(1D, entry.executionOps() / maxExecutionOps));
    }
    return 0D;
  }

  private String topLabel(AdaptAbilityImpactSeries.Snapshot snapshot) {
    if (!snapshot.aggregateAvailable() && snapshot.entries().isEmpty()) {
      return ReactLanguage.raw(RendererMessages.STATUS_WAITING);
    }
    double topTiming = snapshot.entries().isEmpty() ? 0D : snapshot.entries().getFirst().executionTimingMs();
    return ReactLanguage.raw(
        RendererMessages.ADAPT_TOP_VALUE,
        MessageArgument.untrusted("timing", compact(topTiming))
    );
  }

  private boolean isFrameAnchored() {
    MapController mapController = React.controller(MapController.class);
    return mapController != null && mapController.hasFrameAnchor(view());
  }

  private TinyColor rankColor(int rank, double normalized) {
    if (rank == 0) {
      return gradient(normalized, new TinyColor(255, 190, 70), new TinyColor(255, 88, 52));
    }
    if (rank <= 2) {
      return gradient(normalized, new TinyColor(106, 204, 118), new TinyColor(236, 154, 62));
    }
    return gradient(normalized, new TinyColor(60, 154, 120), new TinyColor(92, 188, 196));
  }

  private TinyColor gradient(double normalized, TinyColor low, TinyColor high) {
    return new TinyColor(gradientRgb(normalized, low, high));
  }

  private String trimToWidth(String value, int maxWidth) {
    if (value == null || value.isBlank()) {
      return ReactLanguage.raw(RendererMessages.PIE_UNKNOWN);
    }

    String safe = value.trim();
    int limit = Math.max(8, maxWidth);
    if (textWidth(safe) <= limit) {
      return safe;
    }

    int end = safe.length();
    while (end > 1) {
      String candidate = safe.substring(0, end) + ".";
      if (textWidth(candidate) <= limit) {
        return candidate;
      }
      end--;
    }
    return safe.substring(0, 1);
  }

  private String compact(double value) {
    if (!Double.isFinite(value) || value <= 0D) {
      return "0";
    }
    if (value >= 1000D) {
      return Form.f(value / 1000D, 1) + "k";
    }
    if (value >= 100D) {
      return Form.f(value, 0);
    }
    return Form.f(value, 1);
  }
}
