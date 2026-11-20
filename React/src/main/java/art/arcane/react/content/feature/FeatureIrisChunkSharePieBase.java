package art.arcane.react.content.feature;

import art.arcane.react.platform.bukkit.BukkitMapTargets;

import art.arcane.react.React;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.api.rendering.MapTheme;
import art.arcane.react.api.rendering.MegamapGrid;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.core.controller.MapController;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.RendererMessages;
import art.arcane.react.platform.MapRenderTarget;
import art.arcane.react.util.data.TinyColor;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.generator.ChunkGenerator;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

abstract class FeatureIrisChunkSharePieBase extends ReactFeature implements Listener, ReactRenderer {
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
  private final transient SharePieRenderer pie;

  protected FeatureIrisChunkSharePieBase(String id) {
    super(id);
    pie = new SharePieRenderer(id) {
      @Override
      protected TextKey title() {
        return FeatureIrisChunkSharePieBase.this.title();
      }

      @Override
      protected boolean viewerAvailable(MapRenderTarget target) {
        Player viewer = BukkitMapTargets.player(target);
        return viewer != null && viewer.isOnline();
      }

      @Override
      protected Map<String, Long> collectBuckets(MapRenderTarget target) {
        return FeatureIrisChunkSharePieBase.this.collectBuckets(BukkitMapTargets.player(target));
      }

      @Override
      protected TextKey totalUnit() {
        return FeatureIrisChunkSharePieBase.this.totalUnit();
      }

      @Override
      protected TinyColor headerColor() {
        return FeatureIrisChunkSharePieBase.this.headerColor();
      }

      @Override
      protected String compact(long value) {
        return FeatureIrisChunkSharePieBase.this.compact(value);
      }
    };
  }

  @Override
  public MegamapGrid.MegamapCapability megamapCapability() {
    return pie.megamapCapability();
  }

  @Override
  public final void render() {
    pie.render();
  }

  protected abstract TextKey title();

  protected abstract Map<String, Long> collectBuckets(Player viewer);

  protected TextKey totalUnit() {
    return RendererMessages.PIE_UNIT_CHUNKS;
  }

  protected TinyColor headerColor() {
    return MapTheme.INFO;
  }

  protected String compact(long value) {
    return SharePieRenderer.compactCount(value);
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
      if (BukkitMapTargets.world(target()) != null) {
        return BukkitMapTargets.world(target());
      }
    } catch (Throwable ignored) {
    }

    Location anchor = anchorLocation(viewer);
    if (anchor != null && anchor.getWorld() != null) {
      return anchor.getWorld();
    }

    return viewer == null ? null : viewer.getWorld();
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

    return controller.resolveMapAnchor(target());
  }

  protected String labelForChunkBiome(Chunk chunk, int y) {
    if (chunk == null || chunk.getWorld() == null) {
      return ReactLanguage.raw(RendererMessages.PIE_UNKNOWN);
    }

    World world = chunk.getWorld();
    if (isLikelyIrisWorld(world)) {
      String irisBiome = resolveIrisBiomeLabel(world, (chunk.getX() << 4) + 8, y, (chunk.getZ() << 4) + 8);
      if (irisBiome != null && !irisBiome.isBlank()) {
        return SharePieRenderer.displayName(irisBiome);
      }
    }

    try {
      return SharePieRenderer.displayName(chunk.getBlock(8, y, 8).getBiome().getKey().getKey());
    } catch (Throwable ignored) {
      return ReactLanguage.raw(RendererMessages.PIE_UNKNOWN);
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
          Class<?> toolbelt = Class.forName("art.arcane.iris.world.IrisToolbelt");
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

}
