package art.arcane.react.content.feature;

import art.arcane.react.api.rendering.MapTheme;
import art.arcane.react.api.rendering.MegamapGrid;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.api.rendering.Region;
import art.arcane.react.api.rendering.RendererLayout;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.MapMessages;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.platform.MapRenderTarget;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * A donut chart of named shares with a legend, for the viewer holding or framing the map.
 */
public abstract class SharePieRenderer implements ReactRenderer {
  private static final int MIN_VISIBLE_SLICES = 3;
  private static final int MAX_VISIBLE_SLICES = 32;
  private static final long BUCKET_CACHE_TTL_MS = 45L;
  private static final ThreadLocal<BucketCache> BUCKET_CACHE = ThreadLocal.withInitial(BucketCache::new);
  private static final TinyColor HEADER = MapTheme.INFO;
  private static final TinyColor EMPTY = MapTheme.LINE_STRONG;
  private final String id;

  protected SharePieRenderer(String id) {
    this.id = id;
  }

  public static Map<String, Long> newCounterMap() {
    return new LinkedHashMap<>();
  }

  /**
   * Title case for a raw id such as {@code dark_forest}.
   */
  public static String displayName(String value) {
    if (value == null || value.isBlank()) {
      return ReactLanguage.raw(RendererMessages.PIE_UNKNOWN);
    }

    String spaced = value.replace('_', ' ')
        .replace('-', ' ')
        .trim()
        .toLowerCase(Locale.ROOT);
    if (spaced.isBlank()) {
      return ReactLanguage.raw(RendererMessages.PIE_UNKNOWN);
    }

    StringBuilder out = new StringBuilder(spaced.length());
    boolean upper = true;
    for (int i = 0; i < spaced.length(); i++) {
      char c = spaced.charAt(i);
      if (Character.isWhitespace(c)) {
        upper = true;
        out.append(c);
        continue;
      }

      if (upper && Character.isLetter(c)) {
        out.append(Character.toUpperCase(c));
        upper = false;
      } else {
        out.append(c);
        upper = false;
      }
    }

    return out.toString();
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public MegamapGrid.MegamapCapability megamapCapability() {
    return MegamapGrid.MegamapCapability.adaptiveWall();
  }

  @Override
  public final void render() {
    RendererLayout.backdrop(this, MapTheme.SURFACE_0, MapTheme.SURFACE_1);
    dashHeader(ReactLanguage.raw(title()), null, headerColor());

    Region body = RendererLayout.body(this);
    MapRenderTarget target = target();
    if (!viewerAvailable(target)) {
      RendererLayout.emptyState(this, body, ReactLanguage.raw(RendererMessages.PIE_VIEWER_UNAVAILABLE), null);
      RendererLayout.footer(this, null, null, headerColor());
      return;
    }

    int gutter = MapTheme.gutter(uiScale());
    boolean wide = body.width() >= body.height();
    Region pieArea;
    Region legendArea;
    if (wide) {
      Region[] split = body.splitColumns(gutter, 42, 58);
      pieArea = split[0];
      legendArea = split[1];
    } else {
      Region[] split = body.splitRows(gutter, 52, 48);
      pieArea = split[0];
      legendArea = split[1];
    }

    int legendRowHeight = RendererLayout.compactRowHeight(this);
    int legendCapacity = sliceBudget(legendContent(legendArea).rowCapacity(legendRowHeight));

    Map<String, Long> raw = cachedBuckets(target);
    List<Slice> slices = normalize(raw, legendCapacity);
    if (slices.isEmpty()) {
      RendererLayout.emptyState(this, body, ReactLanguage.raw(RendererMessages.PIE_NO_DATA), null);
      RendererLayout.footer(this, null, null, headerColor());
      return;
    }

    long total = total(slices);
    drawPie(pieArea, slices, total);
    drawLegend(legendArea, legendRowHeight, slices);

    RendererLayout.footer(
        this,
        ReactLanguage.raw(
            RendererMessages.PIE_TOTAL,
            MessageArgument.untrusted("total", compact(total)),
            MessageArgument.untrusted("unit", totalUnitLabel())
        ),
        ReactLanguage.raw(
            MapMessages.ROWS_SHOWN,
            MessageArgument.untrusted("shown", Integer.toString(slices.size())),
            MessageArgument.untrusted("total", Integer.toString(Math.max(slices.size(), countBuckets(raw))))
        ),
        headerColor()
    );
  }

  protected abstract TextKey title();

  /**
   * False shows the viewer-unavailable state instead of collecting buckets.
   */
  protected abstract boolean viewerAvailable(MapRenderTarget target);

  /**
   * Label to weight, called at most once per viewer every few milliseconds on the render thread.
   */
  protected abstract Map<String, Long> collectBuckets(MapRenderTarget target);

  protected String totalUnitLabel() {
    return ReactLanguage.raw(totalUnit());
  }

  protected TextKey totalUnit() {
    return RendererMessages.PIE_UNIT_CHUNKS;
  }

  /**
   * Slices smaller than this share of the total join "Other" once the largest few are drawn; 0 keeps every slice that
   * fits the legend.
   */
  protected double minimumSliceShare() {
    return 0D;
  }

  protected TinyColor headerColor() {
    return HEADER;
  }

  private Map<String, Long> cachedBuckets(MapRenderTarget target) {
    BucketCache cache = BUCKET_CACHE.get();
    long now = System.currentTimeMillis();
    UUID viewer = target == null ? null : target.viewerId();
    if (cache.owner == this
        && Objects.equals(viewer, cache.viewer)
        && (now - cache.stampMs) < BUCKET_CACHE_TTL_MS) {
      return cache.buckets;
    }

    cache.owner = this;
    cache.viewer = viewer;
    cache.stampMs = now;
    cache.buckets = collectBuckets(target);
    return cache.buckets;
  }

  private int countBuckets(Map<String, Long> values) {
    if (values == null || values.isEmpty()) {
      return 0;
    }

    int count = 0;
    for (Long value : values.values()) {
      if (value != null && value > 0L) {
        count++;
      }
    }
    return count;
  }

  private Region legendContent(Region area) {
    int scale = uiScale();
    return area.inset(
        MapTheme.pad(scale),
        MapTheme.borderThickness(scale) + scale,
        MapTheme.pad(scale),
        MapTheme.borderThickness(scale) + scale
    );
  }

  private static int sliceBudget(int rowCapacity) {
    return Math.max(MIN_VISIBLE_SLICES, Math.min(MAX_VISIBLE_SLICES, rowCapacity));
  }

  private List<Slice> normalize(Map<String, Long> values, int maxSlices) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }

    List<Bucket> buckets = new ArrayList<>();
    for (Map.Entry<String, Long> entry : values.entrySet()) {
      if (entry == null) {
        continue;
      }

      long count = entry.getValue() == null ? 0L : entry.getValue();
      if (count <= 0L) {
        continue;
      }

      buckets.add(new Bucket(cleanLabel(entry.getKey()), count));
    }

    if (buckets.isEmpty()) {
      return List.of();
    }

    buckets.sort(Comparator.comparingLong(Bucket::count).reversed());
    long total = buckets.stream().mapToLong(Bucket::count).sum();
    if (total <= 0L) {
      return List.of();
    }

    int limit = sliceBudget(maxSlices);
    double minimumCount = total * Math.max(0D, minimumSliceShare());
    List<Bucket> visible = new ArrayList<>();
    List<Bucket> folded = new ArrayList<>();
    for (int i = 0; i < buckets.size(); i++) {
      Bucket bucket = buckets.get(i);
      boolean fits = buckets.size() <= limit || i < (limit - 1);
      boolean large = i < MIN_VISIBLE_SLICES || bucket.count() >= minimumCount;
      if (fits && large) {
        visible.add(bucket);
      } else {
        folded.add(bucket);
      }
    }

    if (folded.size() == 1) {
      visible.add(folded.get(0));
    } else if (!folded.isEmpty()) {
      long other = 0L;
      for (Bucket bucket : folded) {
        other += bucket.count();
      }
      visible.add(new Bucket(ReactLanguage.raw(RendererMessages.PIE_OTHER), other));
    }

    List<Slice> slices = new ArrayList<>();
    double cursor = 0D;
    for (int i = 0; i < visible.size(); i++) {
      Bucket bucket = visible.get(i);
      double ratio = bucket.count() / (double) total;
      if (ratio <= 0D) {
        continue;
      }
      double end = Math.min(1D, cursor + ratio);
      slices.add(new Slice(bucket.label(), bucket.count(), ratio, cursor, end, MapTheme.categorical(i)));
      cursor = end;
    }

    if (!slices.isEmpty()) {
      Slice last = slices.get(slices.size() - 1);
      if (last.end() < 1D) {
        slices.set(slices.size() - 1, new Slice(last.label(), last.count(), last.ratio(), last.start(), 1D, last.color()));
      }
    }

    return slices;
  }

  private void drawPie(Region area, List<Slice> slices, long total) {
    if (!visible(area)) {
      return;
    }

    int scale = uiScale();
    int radius = (Math.min(area.width(), area.height()) / 2) - MapTheme.pad(scale);
    if (radius < 6) {
      return;
    }

    int cx = area.centerX();
    int cy = area.centerY();
    CentreLabel label = centreLabel(radius, compact(total), compactShort(total), this::textWidth,
        MapTheme.lineHeight(scale), textHeight());
    int innerRadius = label.innerRadius();
    int radiusSq = radius * radius;
    int innerSq = innerRadius * innerRadius;

    // Only rasterize the part of the disc this tile actually owns, otherwise every tile of a
    // megamap wall pays for the whole pie.
    Region disc = new Region(cx - radius, cy - radius, (2 * radius) + 1, (2 * radius) + 1).intersect(tileRegion());
    if (disc.isEmpty()) {
      return;
    }

    pushClip(area);
    try {
      for (int x = disc.x(); x < disc.right(); x++) {
        for (int y = disc.y(); y < disc.bottom(); y++) {
          int dx = x - cx;
          int dy = y - cy;
          int distSq = (dx * dx) + (dy * dy);
          if (distSq > radiusSq) {
            continue;
          }

          if (distSq <= innerSq) {
            set(x, y, MapTheme.SURFACE_0);
            continue;
          }

          double angle = Math.atan2(dy, dx);
          if (angle < 0D) {
            angle += (Math.PI * 2D);
          }

          Slice slice = sliceAt(slices, angle / (Math.PI * 2D));
          set(x, y, slice == null ? EMPTY : slice.color());
        }
      }

      drawDonutRing(disc, cx, cy, radius, innerRadius);
      drawDonutLabels(cx, cy, innerRadius, label.value());
    } finally {
      popClip();
    }
  }

  /**
   * The hole and the total drawn in it. The hole grows toward three fifths of the radius until the total fits, then
   * the total drops its decimal; a total that never fits is left to the footer.
   */
  static CentreLabel centreLabel(int radius, String full, String shortened, ToIntFunction<String> width, int lineHeight,
                                 int textHeight) {
    int innerRadius = Math.max(3, (radius * 2) / 5);
    int widest = Math.max(innerRadius, (radius * 3) / 5);
    for (String form : new String[] {full, shortened}) {
      for (int inner = innerRadius; inner <= widest; inner++) {
        if (width.applyAsInt(form) <= labelRoom(inner, lineHeight, textHeight)) {
          return new CentreLabel(inner, form);
        }
      }
    }
    return new CentreLabel(innerRadius, "");
  }

  private static int labelExtent(int innerRadius) {
    return (innerRadius * 7) / 10;
  }

  private static boolean twoLineLabel(int innerRadius, int lineHeight) {
    return labelExtent(innerRadius) >= lineHeight;
  }

  /** Width for the total: the square inside the hole when the unit fits under it, else the hole's chord at the text. */
  static int labelRoom(int innerRadius, int lineHeight, int textHeight) {
    if (labelExtent(innerRadius) <= 2) {
      return 0;
    }
    if (twoLineLabel(innerRadius, lineHeight)) {
      return labelExtent(innerRadius) * 2;
    }
    int halfText = (textHeight + 1) / 2;
    return 2 * (int) Math.sqrt(Math.max(0, (innerRadius * innerRadius) - (halfText * halfText)));
  }

  private void drawDonutLabels(int cx, int cy, int innerRadius, String value) {
    int extent = labelExtent(innerRadius);
    if (extent <= 2) {
      return;
    }

    Region hole = new Region(cx - extent, cy - extent, extent * 2, extent * 2);
    int lineHeight = MapTheme.lineHeight(uiScale());
    if (twoLineLabel(innerRadius, lineHeight)) {
      int top = (hole.height() - (2 * lineHeight)) / 2;
      textCenteredIn(hole, top, value, MapTheme.TEXT);
      textCenteredIn(hole, top + lineHeight, totalUnitLabel(), MapTheme.TEXT_MUTED);
      return;
    }

    int half = labelRoom(innerRadius, lineHeight, textHeight()) / 2;
    Region chord = new Region(cx - half, hole.y(), half * 2, hole.height());
    textCenteredIn(chord, Math.max(0, (hole.height() - textHeight()) / 2), value, MapTheme.TEXT);
  }

  private void drawLegend(Region area, int rowHeight, List<Slice> slices) {
    if (!visible(area)) {
      return;
    }

    int scale = uiScale();
    panel(area, MapTheme.SURFACE_2, MapTheme.LINE);

    Region content = legendContent(area);
    if (content.isEmpty()) {
      return;
    }

    int capacity = Math.max(1, content.rowCapacity(rowHeight));
    boolean truncated = slices.size() > capacity;
    int limit = truncated ? Math.max(0, capacity - 1) : slices.size();
    int gutter = MapTheme.gutter(scale);
    boolean showCount = content.width() >= (72 * scale);

    for (int i = 0; i < limit; i++) {
      Region row = content.rowAt(i, rowHeight);
      if (!visible(row)) {
        continue;
      }

      Slice slice = slices.get(i);
      int chip = Math.max(3, MapTheme.lineHeight(scale) - (3 * scale));
      Region swatch = new Region(row.x(), row.y() + ((row.height() - chip) / 2), chip, chip);
      fill(swatch, slice.color());

      Region text = row.withoutLeft(chip + gutter);
      int baseline = RendererLayout.baseline(this, row);
      Region[] columns = showCount
          ? text.splitColumns(gutter, 54, 22, 24)
          : text.splitColumns(gutter, 68, 32);

      textIn(columns[0], 0, baseline, slice.label(), MapTheme.TEXT);
      String percent = Form.f(slice.ratio() * 100D, 1) + "%";
      if (textWidth(percent) > columns[1].width()) {
        percent = Math.round(slice.ratio() * 100D) + "%";
      }
      if (textWidth(percent) > columns[1].width()) {
        percent = Long.toString(Math.round(slice.ratio() * 100D));
      }
      textRightIn(columns[1], 0, baseline, percent, MapTheme.TEXT_SOFT);
      if (showCount) {
        textRightIn(columns[2], 0, baseline, compact(slice.count()), MapTheme.TEXT_MUTED);
      }
    }

    if (truncated) {
      Region row = content.rowAt(limit, rowHeight);
      textIn(
          row,
          0,
          RendererLayout.baseline(this, row),
          ReactLanguage.raw(
              MapMessages.MORE_ITEMS,
              MessageArgument.untrusted("count", Integer.toString(slices.size() - limit))
          ),
          MapTheme.TEXT_MUTED
      );
    }
  }

  private long total(List<Slice> slices) {
    long total = 0L;
    for (Slice slice : slices) {
      total += slice.count();
    }
    return total;
  }

  private Slice sliceAt(List<Slice> slices, double fraction) {
    for (Slice slice : slices) {
      if (fraction >= slice.start() && fraction <= slice.end()) {
        return slice;
      }
    }
    return slices.isEmpty() ? null : slices.get(slices.size() - 1);
  }

  protected String compact(long value) {
    return compactCount(value);
  }

  /** The total without its decimal, for a hole too narrow for {@link #compact(long)}. */
  protected String compactShort(long value) {
    return compactCountShort(value);
  }

  public static String compactCountShort(long value) {
    if (value >= 999_500L) {
      return Math.round(value / 1_000_000D) + "m";
    }
    if (value >= 1_000L) {
      return Math.round(value / 1_000D) + "k";
    }
    return Long.toString(Math.max(0L, value));
  }

  public static String compactCount(long value) {
    if (value >= 1_000_000L) {
      return Form.f(value / 1_000_000D, 1) + "m";
    }
    if (value >= 1_000L) {
      return Form.f(value / 1_000D, 1) + "k";
    }
    return Long.toString(Math.max(0L, value));
  }

  private String cleanLabel(String label) {
    return label == null || label.isBlank()
        ? ReactLanguage.raw(RendererMessages.PIE_UNKNOWN)
        : label.trim();
  }

  private void drawDonutRing(Region disc, int cx, int cy, int outerRadius, int innerRadius) {
    int outerSq = outerRadius * outerRadius;
    int innerSq = innerRadius * innerRadius;

    for (int x = disc.x(); x < disc.right(); x++) {
      for (int y = disc.y(); y < disc.bottom(); y++) {
        int dx = x - cx;
        int dy = y - cy;
        int distSq = (dx * dx) + (dy * dy);
        if (distSq > outerSq || distSq < innerSq) {
          continue;
        }

        if (Math.abs(distSq - outerSq) < (outerRadius * 2)
            || Math.abs(distSq - innerSq) < (innerRadius * 2)) {
          set(x, y, MapTheme.LINE_STRONG);
        }
      }
    }
  }

  private static final class BucketCache {
    private Object owner;
    private UUID viewer;
    private long stampMs;
    private Map<String, Long> buckets = Map.of();
  }

  private record Bucket(String label, long count) {
  }

  record CentreLabel(int innerRadius, String value) {
  }

  private record Slice(
      String label,
      long count,
      double ratio,
      double start,
      double end,
      TinyColor color
  ) {
  }
}
