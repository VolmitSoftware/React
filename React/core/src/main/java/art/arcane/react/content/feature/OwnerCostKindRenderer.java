package art.arcane.react.content.feature;

import art.arcane.react.api.rendering.MapTheme;
import art.arcane.react.api.rendering.MegamapGrid;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.api.rendering.Region;
import art.arcane.react.api.rendering.RendererLayout;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.MapMessages;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.platform.EventMeasurement;
import art.arcane.react.platform.cost.OwnerCostModel;
import art.arcane.react.platform.cost.OwnerNoun;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.util.localization.MessageArgument;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Owner cost split by kind of work: a bar across all owners, then the most expensive owners with the same split.
 */
public class OwnerCostKindRenderer implements ReactRenderer {
  public static final String ID = "plugin-cost-kind-list-map";
  public static final List<String> KIND_ORDER = List.of("event", "block_entity", "entity", "worldgen");
  private static final TinyColor ACCENT = MapTheme.ACCENT_TEAL;
  private final Supplier<EventMeasurement> events;

  public OwnerCostKindRenderer(Supplier<EventMeasurement> events) {
    this.events = events;
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public MegamapGrid.MegamapCapability megamapCapability() {
    return MegamapGrid.MegamapCapability.adaptiveWall();
  }

  @Override
  public void render() {
    RendererLayout.backdrop(this, MapTheme.SURFACE_0, MapTheme.SURFACE_1);
    EventMeasurement measurement = events.get();
    if (measurement != null) {
      measurement.markSamplerActivity();
    }
    OwnerCostModel model = measurement == null ? null : measurement.ownerCostModel();
    Breakdown breakdown = model == null
        ? Breakdown.EMPTY
        : breakdown(model.snapshotOwnerKindMs(), model.displayNames(), model.windowSeconds());

    dashHeader(
        ReactLanguage.raw(OwnerNoun.pick(RendererMessages.TITLE_PLUGIN_COST_KINDS, RendererMessages.TITLE_MOD_COST_KINDS)),
        breakdown.owners().isEmpty() ? null : RendererLayout.compact(breakdown.totalMsPerSecond()) + " ms/s",
        ACCENT,
        MapTheme.TEXT_SOFT
    );

    Region body = RendererLayout.body(this);
    if (breakdown.owners().isEmpty()) {
      RendererLayout.emptyState(
          this,
          body,
          ReactLanguage.raw(OwnerNoun.pick(RendererMessages.PLUGIN_NO_IMPACT, RendererMessages.MOD_NO_IMPACT)),
          ReactLanguage.raw(RendererMessages.PLUGIN_WAIT_FOR_DATA)
      );
      RendererLayout.footer(this, null, null, ACCENT);
      return;
    }

    int scale = uiScale();
    int gutter = MapTheme.gutter(scale);
    int pad = MapTheme.pad(scale);
    int border = MapTheme.borderThickness(scale) + scale;
    int legendRow = RendererLayout.compactRowHeight(this);
    int stackHeight = 2 * RendererLayout.barHeight(this);
    int summaryHeight = (2 * border) + stackHeight + gutter + (breakdown.kinds().size() * legendRow);
    Region summary = body.topBand(Math.min(body.height(), summaryHeight));
    drawSummary(summary, breakdown, pad, border, stackHeight, gutter, legendRow);

    Region list = body.withoutTop(summary.height() + gutter);
    int rowHeight = RendererLayout.listRowHeight(this);
    int capacity = list.isEmpty() ? 0 : list.rowCapacity(rowHeight);
    int shown = Math.min(capacity, breakdown.owners().size());
    double maxOwner = Math.max(1.0E-6D, breakdown.owners().get(0).totalMsPerSecond());
    for (int i = 0; i < shown; i++) {
      Region row = list.rowAt(i, rowHeight).withoutBottom(gutter);
      if (visible(row)) {
        drawOwner(row, i, breakdown.owners().get(i), maxOwner, breakdown.kinds(), pad, border);
      }
    }

    RendererLayout.footer(
        this,
        ReactLanguage.raw(
            MapMessages.ROWS_SHOWN,
            MessageArgument.untrusted("shown", Integer.toString(shown)),
            MessageArgument.untrusted("total", Integer.toString(breakdown.owners().size()))
        ),
        ReactLanguage.raw(MapMessages.TOTAL_COST, MessageArgument.untrusted("value", RendererLayout.compact(breakdown.totalMsPerSecond()))),
        ACCENT
    );
  }

  private void drawSummary(Region summary, Breakdown breakdown, int pad, int border, int stackHeight, int gutter, int legendRow) {
    if (!visible(summary)) {
      return;
    }
    RendererLayout.card(this, summary, true);
    Region inner = summary.inset(pad, border, pad, border);
    if (inner.isEmpty()) {
      return;
    }
    Region stack = inner.topBand(Math.min(inner.height(), stackHeight));
    stackedBar(stack, breakdown.kinds(), breakdown.kindMsPerSecond(), breakdown.totalMsPerSecond());

    Region legend = inner.withoutTop(stack.height() + gutter);
    int chip = Math.max(3, MapTheme.lineHeight(uiScale()) - (3 * uiScale()));
    double total = Math.max(1.0E-6D, breakdown.totalMsPerSecond());
    for (int i = 0; i < breakdown.kinds().size(); i++) {
      Region row = legend.rowAt(i, legendRow);
      if (!visible(row)) {
        continue;
      }
      String kind = breakdown.kinds().get(i);
      double value = breakdown.kindMsPerSecond().getOrDefault(kind, 0D);
      fill(new Region(row.x(), row.y() + ((row.height() - chip) / 2), chip, chip), kindColor(kind, i));
      Region text = row.withoutLeft(chip + gutter);
      int baseline = RendererLayout.baseline(this, row);
      String label = kindLabel(kind);
      String share = RendererLayout.percent(value / total);
      String right = RendererLayout.compact(value) + " ms/s  " + share;
      if (textWidth(label) + (2 * gutter) + textWidth(right) > text.width()) {
        right = share;
      }
      textRightIn(text, 0, baseline, right, MapTheme.TEXT_SOFT);
      textIn(text.withoutRight(textWidth(right) + gutter), 0, baseline, label, MapTheme.TEXT);
    }
  }

  private void drawOwner(Region row, int rank, Owner owner, double maxOwner, List<String> kinds, int pad, int border) {
    RendererLayout.card(this, row, rank < 3);
    Region inner = row.inset(pad, border, pad, border);
    if (inner.isEmpty()) {
      return;
    }
    int scale = uiScale();
    Region textLine = inner.topBand(MapTheme.lineHeight(scale));
    String value = RendererLayout.compact(owner.totalMsPerSecond()) + " ms/s";
    textRightIn(textLine, 0, 0, value, MapTheme.TEXT_SOFT);
    textIn(textLine.withoutRight(textWidth(value) + MapTheme.gutter(scale)), 0, 0, "#" + (rank + 1) + " " + owner.name(), MapTheme.TEXT);

    Region barRow = inner.withoutTop(textLine.height() + scale);
    if (barRow.isEmpty()) {
      return;
    }
    Region track = barRow.topBand(Math.min(barRow.height(), RendererLayout.barHeight(this)));
    fill(track, MapTheme.TRACK);
    int width = (int) Math.round(track.width() * Math.min(1D, owner.totalMsPerSecond() / maxOwner));
    stackedBar(new Region(track.x(), track.y(), Math.max(0, width), track.height()), kinds, owner.kindMsPerSecond(), owner.totalMsPerSecond());
  }

  private void stackedBar(Region bar, List<String> kinds, Map<String, Double> values, double total) {
    if (bar.isEmpty() || total <= 0D) {
      return;
    }
    int x = bar.x();
    double running = 0D;
    for (int i = 0; i < kinds.size(); i++) {
      double value = values.getOrDefault(kinds.get(i), 0D);
      if (value <= 0D) {
        continue;
      }
      running += value;
      int end = i == kinds.size() - 1 ? bar.right() : bar.x() + (int) Math.round(bar.width() * Math.min(1D, running / total));
      if (end > x) {
        fill(new Region(x, bar.y(), end - x, bar.height()), kindColor(kinds.get(i), i));
        x = end;
      }
    }
  }

  public static String kindLabel(String kind) {
    return switch (kind) {
      case "event" -> ReactLanguage.raw(RendererMessages.COST_KIND_EVENT);
      case "block_entity" -> ReactLanguage.raw(RendererMessages.COST_KIND_BLOCK_ENTITY);
      case "entity" -> ReactLanguage.raw(RendererMessages.COST_KIND_ENTITY);
      case "worldgen" -> ReactLanguage.raw(RendererMessages.COST_KIND_WORLDGEN);
      default -> SharePieRenderer.displayName(kind);
    };
  }

  private static TinyColor kindColor(String kind, int index) {
    return switch (kind) {
      case "event" -> MapTheme.INFO;
      case "block_entity" -> MapTheme.WARN;
      case "entity" -> MapTheme.OK;
      case "worldgen" -> MapTheme.ACCENT_VIOLET;
      default -> MapTheme.categorical(index + 4);
    };
  }

  /**
   * Converts one published window into ms/s per kind and per owner, owners from most to least expensive, kinds in
   * {@link #KIND_ORDER} first and any other kind after them by cost.
   */
  static Breakdown breakdown(Map<String, Map<String, Double>> ownerKindMs, Map<String, String> names, double windowSeconds) {
    if (ownerKindMs == null || ownerKindMs.isEmpty() || windowSeconds <= 0D) {
      return Breakdown.EMPTY;
    }
    Map<String, Double> kindTotals = new HashMap<>();
    List<Owner> owners = new ArrayList<>();
    double total = 0D;
    for (Map.Entry<String, Map<String, Double>> entry : ownerKindMs.entrySet()) {
      Map<String, Double> kinds = new LinkedHashMap<>();
      double ownerTotal = 0D;
      for (Map.Entry<String, Double> kind : entry.getValue().entrySet()) {
        double perSecond = Math.max(0D, kind.getValue() == null ? 0D : kind.getValue()) / windowSeconds;
        if (perSecond <= 0D) {
          continue;
        }
        kinds.put(kind.getKey(), perSecond);
        kindTotals.merge(kind.getKey(), perSecond, Double::sum);
        ownerTotal += perSecond;
      }
      if (ownerTotal <= 0D) {
        continue;
      }
      String name = names == null ? null : names.get(entry.getKey());
      owners.add(new Owner(entry.getKey(), name == null || name.isBlank() ? entry.getKey() : name.trim(), ownerTotal, kinds));
      total += ownerTotal;
    }
    owners.sort(Comparator.comparingDouble(Owner::totalMsPerSecond).reversed().thenComparing(Owner::name));

    List<String> kinds = new ArrayList<>();
    for (String known : KIND_ORDER) {
      if (kindTotals.containsKey(known)) {
        kinds.add(known);
      }
    }
    List<String> extra = new ArrayList<>(kindTotals.keySet());
    extra.removeAll(KIND_ORDER);
    extra.sort(Comparator.comparingDouble((String kind) -> kindTotals.get(kind)).reversed().thenComparing(Comparator.naturalOrder()));
    kinds.addAll(extra);
    return new Breakdown(List.copyOf(kinds), Map.copyOf(kindTotals), List.copyOf(owners), total);
  }

  record Owner(String id, String name, double totalMsPerSecond, Map<String, Double> kindMsPerSecond) {
  }

  record Breakdown(List<String> kinds, Map<String, Double> kindMsPerSecond, List<Owner> owners, double totalMsPerSecond) {
    static final Breakdown EMPTY = new Breakdown(List.of(), Map.of(), List.of(), 0D);
  }
}
