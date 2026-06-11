package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.core.controller.MapController;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.generator.ChunkGenerator;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

abstract class FeatureIrisChunkSharePieBase extends ReactFeature implements Listener, ReactRenderer {
  private static final int MAX_VISIBLE_SLICES = 6;
  private static final TinyColor BACKGROUND_TOP = new TinyColor(10, 16, 20);
  private static final TinyColor BACKGROUND_BOTTOM = new TinyColor(18, 24, 30);
  private static final TinyColor HEADER = new TinyColor(38, 116, 164);
  private static final TinyColor PANEL = new TinyColor(16, 21, 26);
  private static final TinyColor PANEL_BORDER = new TinyColor(32, 44, 56);
  private static final TinyColor EMPTY = new TinyColor(66, 66, 74);
  private static final TinyColor DONUT_CENTER = new TinyColor(14, 19, 24);
  private static final TinyColor BACKDROP_DOT = new TinyColor(22, 30, 38);
  private static final TinyColor DONUT_RING = new TinyColor(34, 44, 56);
  private static final TinyColor[] COLORS = new TinyColor[]{
      new TinyColor(78, 186, 132),
      new TinyColor(72, 140, 204),
      new TinyColor(252, 194, 86),
      new TinyColor(242, 124, 88),
      new TinyColor(176, 138, 255),
      new TinyColor(112, 202, 216)
  };
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
  public final void render() {
    drawBackdrop();
    dashHeader(title(), null, headerColor());

    Player viewer = player();
    if (viewer == null || !viewer.isOnline()) {
      drawInfoPanel("Viewer unavailable");
      return;
    }

    Map<String, Long> raw = collectBuckets(viewer);
    List<Slice> slices = normalize(raw);
    if (slices.isEmpty()) {
      drawInfoPanel("No data");
      return;
    }

    int centerX = 34;
    int centerY = 69;
    long total = total(slices);
    drawPie(centerX, centerY, 27, 11, slices, total);
    drawLegend(62, 16, slices);
    text(4, 116, "Total: " + compact(total) + " " + totalUnitLabel(), TEXT_DIM);
  }

  @Override
  public void onActivate() {

  }

  @Override
  public void onDeactivate() {

  }

  @Override
  public int getTickInterval() {
    return -1;
  }

  @Override
  public void onTick() {

  }

  protected abstract String title();

  protected abstract Map<String, Long> collectBuckets(Player viewer);

  protected String totalUnitLabel() {
    return "chunks";
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

    return world.getName() != null && world.getName().toLowerCase(Locale.ROOT).contains("iris");
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
      return "Unknown";
    }

    String spaced = value.replace('_', ' ')
        .replace('-', ' ')
        .trim()
        .toLowerCase(Locale.ROOT);
    if (spaced.isBlank()) {
      return "Unknown";
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
      return "Unknown";
    }

    World world = chunk.getWorld();
    if (isLikelyIrisWorld(world)) {
      String irisBiome = resolveIrisBiomeLabel(world, (chunk.getX() << 4) + 8, y, (chunk.getZ() << 4) + 8);
      if (irisBiome != null && !irisBiome.isBlank()) {
        return displayName(irisBiome);
      }
    }

    try {
      return displayName(chunk.getBlock(8, y, 8).getBiome().name());
    } catch (Throwable ignored) {
      return "Unknown";
    }
  }

  private List<Slice> normalize(Map<String, Long> values) {
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

      buckets.add(new Bucket(trimLabel(entry.getKey(), 16), count));
    }

    if (buckets.isEmpty()) {
      return List.of();
    }

    buckets.sort(Comparator.comparingLong(Bucket::count).reversed());
    long total = buckets.stream().mapToLong(Bucket::count).sum();
    if (total <= 0L) {
      return List.of();
    }

    List<Bucket> visible = new ArrayList<>();
    long other = 0L;
    for (int i = 0; i < buckets.size(); i++) {
      Bucket bucket = buckets.get(i);
      if (i < (MAX_VISIBLE_SLICES - 1)) {
        visible.add(bucket);
      } else {
        other += bucket.count();
      }
    }

    if (other > 0L) {
      visible.add(new Bucket("Other", other));
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
      slices.add(new Slice(bucket.label(), bucket.count(), ratio, cursor, end, COLORS[i % COLORS.length]));
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

  private void drawPie(int cx, int cy, int radius, int innerRadius, List<Slice> slices, long total) {
    int radiusSq = radius * radius;
    int innerSq = innerRadius * innerRadius;

    for (int x = cx - radius; x <= cx + radius; x++) {
      for (int y = cy - radius; y <= cy + radius; y++) {
        int dx = x - cx;
        int dy = y - cy;
        int distSq = (dx * dx) + (dy * dy);
        if (distSq > radiusSq) {
          continue;
        }

        if (distSq <= innerSq) {
          set(x, y, DONUT_CENTER);
          continue;
        }

        double angle = Math.atan2(dy, dx);
        if (angle < 0D) {
          angle += (Math.PI * 2D);
        }

        double fraction = angle / (Math.PI * 2D);
        Slice slice = sliceAt(slices, fraction);
        TinyColor base = slice == null ? EMPTY : slice.color();
        double radial = Math.sqrt(distSq) / Math.max(1D, radius);
        double highlight = ((dx - dy) / Math.max(1D, radius * 3D));
        double shade = Math.max(0.78D, Math.min(1.22D, (1.12D - (radial * 0.24D)) + highlight));
        set(x, y, shade(base, shade));
      }
    }

    drawDonutRing(cx, cy, radius, innerRadius);

    String center = compact(total);
    textNear(cx + (textWidth(center) / 2), cy + 2, center);
    textNear(cx + (textWidth(totalUnitLabel()) / 2), cy + 12, totalUnitLabel());
  }

  private void drawLegend(int startX, int startY, List<Slice> slices) {
    int panelWidth = 64;
    int textLeft = startX + 10;
    int textRight = startX + panelWidth - 3;
    int index = 0;
    for (Slice slice : slices) {
      if (index >= 6) {
        break;
      }

      int y = startY + (index * 16);
      set(startX, y - 1, panelWidth, 13, PANEL);
      set(startX, y - 1, panelWidth, 1, PANEL_BORDER);
      set(startX, y + 11, panelWidth, 1, PANEL_BORDER);
      set(startX + 2, y + 2, 5, 5, slice.color());
      String percent = Form.f(slice.ratio() * 100D, 0) + "%";
      String count = compact(slice.count());
      int countX = (textRight - textWidth(count)) + 1;
      String merged = percent + " " + count;

      text(textLeft, y, trimToWidth(slice.label(), textRight - textLeft + 1));
      if (countX > (textLeft + textWidth(percent) + 1)) {
        text(textLeft, y + 8, percent);
        text(countX, y + 8, count);
      } else {
        text(textLeft, y + 8, trimToWidth(merged, textRight - textLeft + 1));
      }
      index++;
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

  private String trimLabel(String label, int max) {
    String safe = label == null || label.isBlank() ? "Unknown" : label.trim();
    int limit = Math.max(4, max);
    if (safe.length() <= limit) {
      return safe;
    }
    return safe.substring(0, limit - 1) + ".";
  }

  private String trimToWidth(String value, int maxWidth) {
    String safe = value == null || value.isBlank() ? "Unknown" : value.trim();
    int limit = Math.max(8, maxWidth);
    if (textWidth(safe) <= limit) {
      return safe;
    }

    String suffix = ".";
    int end = safe.length();
    while (end > 1) {
      String candidate = safe.substring(0, end) + suffix;
      if (textWidth(candidate) <= limit) {
        return candidate;
      }
      end--;
    }

    return safe.substring(0, 1);
  }

  private void drawBackdrop() {
    for (int y = 0; y < height(); y++) {
      double n = y / (double) Math.max(1, height() - 1);
      TinyColor row = gradient(n, BACKGROUND_TOP, BACKGROUND_BOTTOM);
      set(0, y, width(), 1, row);
    }

    for (int x = 0; x < width(); x += 6) {
      for (int y = 14; y < height(); y += 6) {
        set(x, y, BACKDROP_DOT);
      }
    }
  }

  private void drawInfoPanel(String message) {
    set(12, 42, 104, 30, PANEL);
    set(12, 42, 104, 1, PANEL_BORDER);
    set(12, 71, 104, 1, PANEL_BORDER);
    text(18, 53, message);
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
          set(x, y, DONUT_RING);
        }
      }
    }
  }

  private TinyColor gradient(double normalized, TinyColor low, TinyColor high) {
    return new TinyColor(gradientRgb(normalized, low, high));
  }

  private TinyColor shade(TinyColor base, double factor) {
    int r = (int) Math.round(base.getRed() * factor);
    int g = (int) Math.round(base.getGreen() * factor);
    int b = (int) Math.round(base.getBlue() * factor);
    return new TinyColor(
        Math.max(0, Math.min(255, r)),
        Math.max(0, Math.min(255, g)),
        Math.max(0, Math.min(255, b))
    );
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
