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
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.generator.ChunkGenerator;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

abstract class FeatureIrisChunkSharePieBase extends ReactFeature implements Listener, ReactRenderer {
  private static final int MIN_VISIBLE_SLICES = 3;
  private static final int MAX_VISIBLE_SLICES = 16;
  private static final long BUCKET_CACHE_TTL_MS = 45L;
  private static final ThreadLocal<BucketCache> BUCKET_CACHE = ThreadLocal.withInitial(BucketCache::new);
  private static final TinyColor HEADER = MapTheme.INFO;
  private static final TinyColor EMPTY = MapTheme.LINE_STRONG;
  private static final Object IRIS_REFLECTION_LOCK = new Object();
  private static final Map<Class<?>, Method> accessEngineMethods = new ConcurrentHashMap<>();
  private static final Set<Class<?>> accessEngineMethodMisses = ConcurrentHashMap.newKeySet();
  private static final Map<Class<?>, Method> engineBiomeMethods = new ConcurrentHashMap<>();
  private static final Set<Class<?>> engineBiomeMethodMisses = ConcurrentHashMap.newKeySet();
  private static final Map<Class<?>, Method> biomeNameMethods = new ConcurrentHashMap<>();
  private static final Set<Class<?>> biomeNameMethodMisses = ConcurrentHashMap.newKeySet();
  private static final Map<Class<?>, Method> biomeLoadKeyMethods = new ConcurrentHashMap<>();
  private static final Set<Class<?>> biomeLoadKeyMethodMisses = ConcurrentHashMap.newKeySet();
  private static volatile boolean irisAccessLookupAttempted = false;
  private static volatile Method irisAccessMethod = null;

  protected FeatureIrisChunkSharePieBase(String id) {
    super(id);
  }

  protected static Map<String, Long> newCounterMap() {
    return new LinkedHashMap<>();
  }

  @Override
  public MegamapGrid.MegamapCapability megamapCapability() {
    return MegamapGrid.MegamapCapability.adaptive(4, 4);
  }

  @Override
  public final void render() {
    RendererLayout.backdrop(this, MapTheme.SURFACE_0, MapTheme.SURFACE_1);
    dashHeader(ReactLanguage.raw(title()), null, headerColor());

    Region body = RendererLayout.body(this);
    Player viewer = player();
    if (viewer == null || !viewer.isOnline()) {
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
    int legendCapacity = Math.max(
        MIN_VISIBLE_SLICES,
        Math.min(MAX_VISIBLE_SLICES, legendArea.inset(gutter).rowCapacity(legendRowHeight))
    );

    Map<String, Long> raw = cachedBuckets(viewer);
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

  protected abstract Map<String, Long> collectBuckets(Player viewer);

  protected String totalUnitLabel() {
    return ReactLanguage.raw(totalUnit());
  }

  protected TextKey totalUnit() {
    return RendererMessages.PIE_UNIT_CHUNKS;
  }

  protected TinyColor headerColor() {
    return HEADER;
  }

  protected boolean isLikelyIrisWorld(World world) {
    if (world == null) {
      return false;
    }

    try {
      ChunkGenerator generator = world.getGenerator();
      if (generator != null) {
        String className = generator.getClass().getName().toLowerCase(Locale.ROOT);
        if (className.contains("iris")) {
          return true;
        }
      }
    } catch (Throwable ignored) {
    }

    return WorldIdentity.serialize(world).toLowerCase(Locale.ROOT).contains("iris");
  }

  protected World targetWorld(Player viewer) {
    try {
      if (view() != null && view().getWorld() != null) {
        return view().getWorld();
      }
    } catch (Throwable ignored) {
    }

    Location anchor = anchorLocation(viewer);
    if (anchor != null && anchor.getWorld() != null) {
      return anchor.getWorld();
    }

    return viewer == null ? null : viewer.getWorld();
  }

  protected String displayName(String value) {
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

  protected int sampleY(World world, Player viewer) {
    int min = world == null ? 0 : world.getMinHeight();
    int max = world == null ? 255 : (world.getMaxHeight() - 1);
    Location anchor = anchorLocation(viewer);
    int y = anchor == null ? (viewer == null ? 64 : viewer.getLocation().getBlockY()) : anchor.getBlockY();
    return Math.max(min, Math.min(max, y));
  }

  protected Location anchorLocation(Player viewer) {
    MapController controller = React.controller(MapController.class);
    if (controller == null) {
      return viewer == null ? null : viewer.getLocation();
    }

    return controller.resolveMapAnchor(view(), viewer);
  }

  protected String labelForChunkBiome(Chunk chunk, int y) {
    if (chunk == null || chunk.getWorld() == null) {
      return ReactLanguage.raw(RendererMessages.PIE_UNKNOWN);
    }

    World world = chunk.getWorld();
    if (isLikelyIrisWorld(world)) {
      String irisBiome = resolveIrisBiomeLabel(world, (chunk.getX() << 4) + 8, y, (chunk.getZ() << 4) + 8);
      if (irisBiome != null && !irisBiome.isBlank()) {
        return displayName(irisBiome);
      }
    }

    try {
      return displayName(chunk.getBlock(8, y, 8).getBiome().key().value());
    } catch (Throwable ignored) {
      return ReactLanguage.raw(RendererMessages.PIE_UNKNOWN);
    }
  }

  private Map<String, Long> cachedBuckets(Player viewer) {
    BucketCache cache = BUCKET_CACHE.get();
    long now = System.currentTimeMillis();
    if (cache.owner == this
        && viewer.getUniqueId().equals(cache.viewer)
        && (now - cache.stampMs) < BUCKET_CACHE_TTL_MS) {
      return cache.buckets;
    }

    cache.owner = this;
    cache.viewer = viewer.getUniqueId();
    cache.stampMs = now;
    cache.buckets = collectBuckets(viewer);
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

    int limit = Math.max(MIN_VISIBLE_SLICES, Math.min(MAX_VISIBLE_SLICES, maxSlices));
    List<Bucket> visible = new ArrayList<>();
    long other = 0L;
    for (int i = 0; i < buckets.size(); i++) {
      Bucket bucket = buckets.get(i);
      if (buckets.size() <= limit || i < (limit - 1)) {
        visible.add(bucket);
      } else {
        other += bucket.count();
      }
    }

    if (other > 0L) {
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
    int innerRadius = Math.max(3, (radius * 2) / 5);
    int radiusSq = radius * radius;
    int innerSq = innerRadius * innerRadius;

    pushClip(area);
    try {
      for (int x = cx - radius; x <= cx + radius; x++) {
        for (int y = cy - radius; y <= cy + radius; y++) {
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

      drawDonutRing(cx, cy, radius, innerRadius);
      drawDonutLabels(cx, cy, innerRadius, total);
    } finally {
      popClip();
    }
  }

  private void drawDonutLabels(int cx, int cy, int innerRadius, long total) {
    int extent = (innerRadius * 7) / 10;
    if (extent <= 2) {
      return;
    }

    Region hole = new Region(cx - extent, cy - extent, extent * 2, extent * 2);
    int lineHeight = MapTheme.lineHeight(uiScale());
    String value = compact(total);
    if (hole.height() >= (2 * lineHeight)) {
      int top = (hole.height() - (2 * lineHeight)) / 2;
      textCenteredIn(hole, top, value, MapTheme.TEXT);
      textCenteredIn(hole, top + lineHeight, totalUnitLabel(), MapTheme.TEXT_MUTED);
      return;
    }

    textCenteredIn(hole, Math.max(0, (hole.height() - textHeight()) / 2), value, MapTheme.TEXT);
  }

  private void drawLegend(Region area, int rowHeight, List<Slice> slices) {
    if (!visible(area)) {
      return;
    }

    int scale = uiScale();
    panel(area, MapTheme.SURFACE_2, MapTheme.LINE);

    Region content = area.inset(MapTheme.pad(scale), MapTheme.borderThickness(scale) + scale, MapTheme.pad(scale), MapTheme.borderThickness(scale) + scale);
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
      textRightIn(columns[1], 0, baseline, Form.f(slice.ratio() * 100D, 1) + "%", MapTheme.TEXT_SOFT);
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

  private String compact(long value) {
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

  private void drawDonutRing(int cx, int cy, int outerRadius, int innerRadius) {
    int outerSq = outerRadius * outerRadius;
    int innerSq = innerRadius * innerRadius;

    for (int x = cx - outerRadius; x <= cx + outerRadius; x++) {
      for (int y = cy - outerRadius; y <= cy + outerRadius; y++) {
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

  private String resolveIrisBiomeLabel(World world, int x, int y, int z) {
    Method access = irisAccessMethod();
    if (access == null || world == null) {
      return null;
    }

    try {
      Object provider = access.invoke(null, world);
      if (provider == null) {
        return null;
      }

      Method getEngine = cachedMethod(accessEngineMethods, accessEngineMethodMisses, provider.getClass(), "getEngine");
      if (getEngine == null) {
        return null;
      }

      Object engine = getEngine.invoke(provider);
      if (engine == null) {
        return null;
      }

      Method getBiome = cachedMethod(engineBiomeMethods, engineBiomeMethodMisses, engine.getClass(), "getBiome", int.class, int.class, int.class);
      if (getBiome == null) {
        return null;
      }

      Object biome = getBiome.invoke(engine, x, y, z);
      if (biome == null) {
        return null;
      }

      Method getName = cachedMethod(biomeNameMethods, biomeNameMethodMisses, biome.getClass(), "getName");
      Method getLoadKey = cachedMethod(biomeLoadKeyMethods, biomeLoadKeyMethodMisses, biome.getClass(), "getLoadKey");
      String name = invokeString(biome, getName);
      if (name != null && !name.isBlank()) {
        return name;
      }

      String loadKey = invokeString(biome, getLoadKey);
      if (loadKey != null && !loadKey.isBlank()) {
        return loadKey;
      }

      return null;
    } catch (Throwable ignored) {
      return null;
    }
  }

  private Method irisAccessMethod() {
    Method known = irisAccessMethod;
    if (known != null) {
      return known;
    }

    if (irisAccessLookupAttempted) {
      return null;
    }

    synchronized (IRIS_REFLECTION_LOCK) {
      if (!irisAccessLookupAttempted) {
        irisAccessLookupAttempted = true;
        try {
          Class<?> toolbelt = Class.forName("art.arcane.iris.core.tools.IrisToolbelt");
          Method method = toolbelt.getMethod("access", World.class);
          method.setAccessible(true);
          irisAccessMethod = method;
        } catch (Throwable ignored) {
          irisAccessMethod = null;
        }
      }
      return irisAccessMethod;
    }
  }

  private Method cachedMethod(
      Map<Class<?>, Method> cache,
      Set<Class<?>> misses,
      Class<?> owner,
      String methodName,
      Class<?>... params
  ) {
    if (owner == null || misses.contains(owner)) {
      return null;
    }

    Method cached = cache.get(owner);
    if (cached != null) {
      return cached;
    }

    try {
      Method method = owner.getMethod(methodName, params);
      method.setAccessible(true);
      cache.put(owner, method);
      return method;
    } catch (Throwable ignored) {
      misses.add(owner);
      return null;
    }
  }

  private String invokeString(Object target, Method method) {
    if (target == null || method == null) {
      return null;
    }

    try {
      Object value = method.invoke(target);
      return value == null ? null : value.toString();
    } catch (Throwable ignored) {
      return null;
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
