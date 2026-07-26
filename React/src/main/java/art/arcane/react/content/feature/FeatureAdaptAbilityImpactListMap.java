package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.api.rendering.MapTheme;
import art.arcane.react.api.rendering.MegamapGrid;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.api.rendering.Region;
import art.arcane.react.api.rendering.RendererLayout;
import art.arcane.react.core.controller.MapController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.MapMessages;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.util.localization.MessageArgument;

import java.util.List;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for the Adapt Ability Impact List Map feature.")
public class FeatureAdaptAbilityImpactListMap extends ReactFeature implements ReactRenderer {
  public static final String ID = "adapt-ability-impact-list-map";
  private static final TinyColor ACCENT = MapTheme.OK;

  @art.arcane.react.util.project.config.ConfigDoc(value = "Maximum rows drawn per map tile, or 0 to fill the available height.", impact = "Zero lets a taller map wall list every ability; a fixed value keeps the row count constant no matter how large the wall is.")
  private int maxRowsPerTile = 0;

  public FeatureAdaptAbilityImpactListMap() {
    super(ID);
  }

  @Override
  public MegamapGrid.MegamapCapability megamapCapability() {
    return MegamapGrid.MegamapCapability.adaptive(4, 4);
  }

  @Override
  public void render() {
    boolean frameAnchored = isFrameAnchored();
    AdaptAbilityImpactSeries.Snapshot snapshot = AdaptAbilityImpactSeries.snapshot();
    RendererLayout.backdrop(this, MapTheme.SURFACE_0, MapTheme.SURFACE_1);
    dashHeader(
        ReactLanguage.raw(RendererMessages.TITLE_ADAPT_CALLBACK_COST),
        frameAnchored ? ReactLanguage.raw(RendererMessages.STATUS_FRAME) : topLabel(snapshot),
        ACCENT,
        MapTheme.ACCENT_YELLOW
    );

    List<AdaptAbilityImpactSeries.Entry> entries = snapshot.entries();
    Region body = RendererLayout.body(this);
    if (entries.isEmpty()) {
      drawEmptyState(body, snapshot);
      RendererLayout.footer(this, null, null, ACCENT);
      return;
    }

    int columns = megamapColumns();
    int[] weights = columnWeights(columns);
    int gutter = MapTheme.gutter(uiScale());
    Region list = body;
    if (megamapDetail().atLeast(MegamapGrid.MegamapDetail.EXPANDED)) {
      Region strip = body.topBand(MapTheme.lineHeight(uiScale()));
      RendererLayout.columnLabels(this, strip, strip.splitColumns(gutter, weights), columnLabels(columns));
      list = body.withoutTop(strip.height() + gutter);
    }

    int rowHeight = RendererLayout.listRowHeight(this);
    int capacity = Math.max(1, list.rowCapacity(rowHeight));
    if (maxRowsPerTile > 0) {
      capacity = Math.min(capacity, maxRowsPerTile);
    }

    int limit = Math.min(entries.size(), capacity);
    double maxTiming = Math.max(0D, entries.getFirst().executionTimingMs());
    double maxExecutionOps = 0D;
    double totalTiming = 0D;
    for (AdaptAbilityImpactSeries.Entry entry : entries) {
      maxExecutionOps = Math.max(maxExecutionOps, entry.executionOps());
      totalTiming += entry.executionTimingMs();
    }

    for (int i = 0; i < limit; i++) {
      Region row = list.rowAt(i, rowHeight);
      if (!visible(row)) {
        continue;
      }

      AdaptAbilityImpactSeries.Entry entry = entries.get(i);
      double normalized = normalizedImpact(entry, maxTiming, maxExecutionOps);
      double share = totalTiming <= 0D ? 0D : entry.executionTimingMs() / totalTiming;
      drawRow(i, row.withoutBottom(gutter), weights, gutter, normalized, columnValues(columns, i, share, entry));
    }

    RendererLayout.footer(
        this,
        ReactLanguage.raw(
            MapMessages.ROWS_SHOWN,
            MessageArgument.untrusted("shown", Integer.toString(limit)),
            MessageArgument.untrusted("total", Integer.toString(entries.size()))
        ),
        ReactLanguage.raw(
            MapMessages.TOTAL_COST,
            MessageArgument.untrusted("value", RendererLayout.compact(totalTiming))
        ),
        ACCENT
    );
  }

  private void drawEmptyState(Region body, AdaptAbilityImpactSeries.Snapshot snapshot) {
    if (snapshot.aggregateAvailable() && snapshot.aggregateGuardChecks() > 0D) {
      RendererLayout.emptyState(
          this,
          body,
          ReactLanguage.raw(RendererMessages.ADAPT_MEASURED_UNAVAILABLE),
          ReactLanguage.raw(RendererMessages.ADAPT_GUARD_ONLY)
      );
      return;
    }

    RendererLayout.emptyState(
        this,
        body,
        ReactLanguage.raw(RendererMessages.ADAPT_NO_ACTIVITY),
        ReactLanguage.raw(RendererMessages.ADAPT_USE_ABILITIES)
    );
  }

  private void drawRow(
      int rank,
      Region card,
      int[] weights,
      int gutter,
      double normalized,
      String[] values
  ) {
    if (card.isEmpty()) {
      return;
    }

    int scale = uiScale();
    RendererLayout.card(this, card, rank < 3);

    Region inner = card.inset(MapTheme.pad(scale), MapTheme.borderThickness(scale) + scale, MapTheme.pad(scale), MapTheme.borderThickness(scale) + scale);
    if (inner.isEmpty()) {
      return;
    }

    Region textLine = inner.topBand(MapTheme.lineHeight(scale));
    Region[] columns = textLine.splitColumns(gutter, weights);
    for (int i = 0; i < columns.length && i < values.length; i++) {
      TinyColor color = i == 0 ? MapTheme.TEXT : MapTheme.TEXT_SOFT;
      if (i == 0) {
        textIn(columns[i], 0, 0, values[i], color);
      } else {
        textRightIn(columns[i], 0, 0, values[i], color);
      }
    }

    Region barRow = inner.withoutTop(textLine.height() + scale);
    if (barRow.isEmpty()) {
      return;
    }

    RendererLayout.bar(
        this,
        barRow.topBand(Math.min(barRow.height(), RendererLayout.barHeight(this))),
        normalized,
        MapTheme.categorical(rank)
    );
  }

  private int[] columnWeights(int columns) {
    return switch (columns) {
      case 1 -> new int[]{64, 36};
      case 2 -> new int[]{48, 26, 26};
      default -> new int[]{40, 20, 20, 20};
    };
  }

  private String[] columnLabels(int columns) {
    String name = ReactLanguage.raw(MapMessages.COLUMN_NAME);
    String cost = ReactLanguage.raw(MapMessages.COLUMN_COST);
    String ops = ReactLanguage.raw(MapMessages.COLUMN_OPS);
    return switch (columns) {
      case 1 -> new String[]{name, cost};
      case 2 -> new String[]{name, ops, cost};
      default -> new String[]{name, ops, cost, ReactLanguage.raw(MapMessages.COLUMN_SHARE)};
    };
  }

  private String[] columnValues(int columns, int rank, double share, AdaptAbilityImpactSeries.Entry entry) {
    String name = "#" + (rank + 1) + " " + abilityName(entry);
    String cost = RendererLayout.compact(entry.executionTimingMs());
    String ops = RendererLayout.compact(entry.executionOps());
    return switch (columns) {
      case 1 -> new String[]{name, cost};
      case 2 -> new String[]{name, ops, cost};
      default -> new String[]{name, ops, cost, RendererLayout.percent(share)};
    };
  }

  private String abilityName(AdaptAbilityImpactSeries.Entry entry) {
    String name = entry.displayName();
    return name == null || name.isBlank() ? ReactLanguage.raw(RendererMessages.PIE_UNKNOWN) : name.trim();
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
        MessageArgument.untrusted("timing", RendererLayout.compact(topTiming))
    );
  }

  private boolean isFrameAnchored() {
    MapController mapController = React.controller(MapController.class);
    return mapController != null && mapController.hasFrameAnchor(view());
  }
}
