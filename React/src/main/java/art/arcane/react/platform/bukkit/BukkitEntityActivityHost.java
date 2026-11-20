package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.platform.EntityActivityHost;
import art.arcane.react.platform.EntityRange;
import art.arcane.react.platform.EntityRanges;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.Subscription;
import art.arcane.react.util.project.world.NearbyEntitySampler;
import art.arcane.react.util.project.world.WorldEntitySnapshots;
import art.arcane.volmlib.nativelib.NativeAdapters;
import art.arcane.volmlib.nativelib.monitor.EntityRangeSettings;
import art.arcane.volmlib.nativelib.monitor.NativeWorldAccess;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Warden;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class BukkitEntityActivityHost implements EntityActivityHost, Listener {
  private static final String BUDGET_KEY = "react-path-budget";
  private final List<Consumer<GameEntity>> damageListeners = new CopyOnWriteArrayList<>();
  private final List<BiConsumer<GameEntity, GameEntity>> targetListeners = new CopyOnWriteArrayList<>();
  private final List<Consumer<List<GameEntity>>> loadListeners = new CopyOnWriteArrayList<>();
  private final List<Consumer<List<GameEntity>>> unloadListeners = new CopyOnWriteArrayList<>();
  private final NearbyEntitySampler fallbackSampler = new NearbyEntitySampler();
  private volatile NamespacedKey budgetKey;
  private volatile NativeWorldAccess nativeAccess;
  private volatile boolean nativeResolved;
  private volatile Boolean awarenessSupported;
  private boolean registered;

  @Override
  public List<GameEntity> nextEntities(ReactWorld world, int budget) {
    return wrap(WorldEntitySnapshots.next((World) world.handle(), budget));
  }

  @Override
  public List<GameEntity> sampleNearby(ReactPlayer player, String owner, double horizontalRadius, double verticalRadius,
                                       int limit, Predicate<GameEntity> accept) {
    if (player == null || !(player.handle() instanceof Player bukkit)) {
      return List.of();
    }
    EntityController controller = React.controller(EntityController.class);
    NearbyEntitySampler sampler = controller == null ? fallbackSampler : controller.getNearbyEntitySampler();
    NearbyEntitySampler.Request request = new NearbyEntitySampler.Request(owner, horizontalRadius, verticalRadius, limit);
    Predicate<Entity> filter = accept == null ? null : entity -> accept.test(new BukkitGameEntity(entity));
    return wrap(sampler.sample(bukkit, request, filter));
  }

  @Override
  public boolean isNamed(GameEntity entity) {
    String customName = BukkitGameEntity.bukkit(entity).getCustomName();
    return customName != null && !customName.isBlank();
  }

  @Override
  public boolean isVillager(GameEntity entity) {
    return BukkitGameEntity.bukkit(entity) instanceof Villager;
  }

  @Override
  public boolean isBoss(GameEntity entity) {
    Entity bukkit = BukkitGameEntity.bukkit(entity);
    return bukkit instanceof EnderDragon || bukkit instanceof Wither || bukkit instanceof Warden;
  }

  @Override
  public boolean isPersistent(GameEntity entity) {
    return BukkitGameEntity.bukkit(entity).isPersistent();
  }

  @Override
  public int entityId(GameEntity entity) {
    return BukkitGameEntity.bukkit(entity).getEntityId();
  }

  @Override
  public boolean supportsAwareness() {
    Boolean supported = awarenessSupported;
    if (supported == null) {
      try {
        Mob.class.getMethod("setAware", boolean.class);
        Mob.class.getMethod("isAware");
        supported = true;
      } catch (NoSuchMethodException e) {
        supported = false;
      }
      awarenessSupported = supported;
    }
    return supported;
  }

  @Override
  public Subscription onDamage(Consumer<GameEntity> listener) {
    return subscribe(damageListeners, listener);
  }

  @Override
  public Subscription onTarget(BiConsumer<GameEntity, GameEntity> listener) {
    return subscribe(targetListeners, listener);
  }

  @Override
  public Subscription onEntitiesLoad(Consumer<List<GameEntity>> listener) {
    return subscribe(loadListeners, listener);
  }

  @Override
  public Subscription onEntitiesUnload(Consumer<List<GameEntity>> listener) {
    return subscribe(unloadListeners, listener);
  }

  @Override
  public boolean supportsNavigationBudget() {
    return nativeAccess() != null;
  }

  @Override
  public boolean setNavigationBudget(GameEntity mob, float multiplier) {
    NativeWorldAccess access = nativeAccess();
    Mob bukkit = (Mob) BukkitGameEntity.bukkit(mob);
    if (access == null || !access.setNavigationBudget(bukkit, multiplier)) {
      return false;
    }
    bukkit.getPersistentDataContainer().set(budgetKey(), PersistentDataType.BYTE, (byte) 1);
    return true;
  }

  @Override
  public void resetNavigationBudget(GameEntity mob) {
    Mob bukkit = (Mob) BukkitGameEntity.bukkit(mob);
    try {
      NativeWorldAccess access = nativeAccess();
      if (access != null) {
        access.resetNavigationBudget(bukkit);
      }
    } finally {
      bukkit.getPersistentDataContainer().remove(budgetKey());
    }
  }

  @Override
  public boolean hasNavigationBudget(GameEntity mob) {
    return BukkitGameEntity.bukkit(mob).getPersistentDataContainer().getOrDefault(
        budgetKey(),
        PersistentDataType.BYTE,
        (byte) 0
    ) == 1;
  }

  @Override
  public Optional<EntityRanges> entityRanges(ReactWorld world) {
    NativeWorldAccess access = nativeAccess();
    EntityRangeSettings settings = access == null ? null : access.entityRanges((World) world.handle());
    return settings == null ? Optional.empty() : Optional.of(new BukkitEntityRanges(settings));
  }

  @EventHandler(ignoreCancelled = true)
  public void on(EntityDamageEvent event) {
    if (damageListeners.isEmpty()) {
      return;
    }
    GameEntity entity = new BukkitGameEntity(event.getEntity());
    for (Consumer<GameEntity> listener : damageListeners) {
      listener.accept(entity);
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void on(EntityTargetEvent event) {
    if (targetListeners.isEmpty()) {
      return;
    }
    GameEntity entity = new BukkitGameEntity(event.getEntity());
    GameEntity target = event.getTarget() == null ? null : new BukkitGameEntity(event.getTarget());
    for (BiConsumer<GameEntity, GameEntity> listener : targetListeners) {
      listener.accept(entity, target);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntitiesLoadEvent event) {
    dispatch(loadListeners, event.getEntities());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntitiesUnloadEvent event) {
    dispatch(unloadListeners, event.getEntities());
  }

  private static void dispatch(List<Consumer<List<GameEntity>>> listeners, List<Entity> entities) {
    if (listeners.isEmpty()) {
      return;
    }
    List<GameEntity> wrapped = wrap(entities);
    for (Consumer<List<GameEntity>> listener : listeners) {
      listener.accept(wrapped);
    }
  }

  private synchronized <T> Subscription subscribe(List<T> listeners, T listener) {
    listeners.add(listener);
    if (!registered && React.instance != null) {
      React.instance.registerListener(this);
      registered = true;
    }
    return () -> unsubscribe(listeners, listener);
  }

  private synchronized <T> void unsubscribe(List<T> listeners, T listener) {
    listeners.remove(listener);
    if (registered && damageListeners.isEmpty() && targetListeners.isEmpty() && loadListeners.isEmpty()
        && unloadListeners.isEmpty()) {
      registered = false;
      if (React.instance != null) {
        React.instance.unregisterListener(this);
      }
    }
  }

  private NativeWorldAccess nativeAccess() {
    NativeWorldAccess access = nativeAccess;
    if (access != null || nativeResolved) {
      return access;
    }
    synchronized (this) {
      if (!nativeResolved) {
        nativeAccess = NativeAdapters.find(NativeWorldAccess.class).orElse(null);
        nativeResolved = true;
      }
      return nativeAccess;
    }
  }

  private NamespacedKey budgetKey() {
    NamespacedKey key = budgetKey;
    if (key == null) {
      key = new NamespacedKey(React.instance, BUDGET_KEY);
      budgetKey = key;
    }
    return key;
  }

  private static List<GameEntity> wrap(Collection<? extends Entity> entities) {
    if (entities == null || entities.isEmpty()) {
      return List.of();
    }
    List<GameEntity> result = new ArrayList<>(entities.size());
    for (Entity entity : entities) {
      if (entity != null) {
        result.add(new BukkitGameEntity(entity));
      }
    }
    return result;
  }

  private record BukkitEntityRanges(EntityRangeSettings settings) implements EntityRanges {
    @Override
    public Set<EntityRange> supportedRanges() {
      Set<EntityRange> supported = EnumSet.noneOf(EntityRange.class);
      for (EntityRangeSettings.Range range : settings.supportedRanges()) {
        supported.add(EntityRange.valueOf(range.name()));
      }
      return supported;
    }

    @Override
    public int range(EntityRange type) {
      return settings.range(EntityRangeSettings.Range.valueOf(type.name()));
    }

    @Override
    public void range(EntityRange type, int blocks) {
      settings.range(EntityRangeSettings.Range.valueOf(type.name()), blocks);
    }

    @Override
    public boolean tickInactiveVillagers() {
      return settings.tickInactiveVillagers();
    }

    @Override
    public void tickInactiveVillagers(boolean enabled) {
      settings.tickInactiveVillagers(enabled);
    }
  }
}
