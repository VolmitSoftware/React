package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.content.feature.FeatureHopperItemIndex;
import art.arcane.react.core.controller.NearbyPlayerIndexController;
import art.arcane.react.core.integration.GlossDropNameIntegration;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.ItemHost;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.Subscription;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.world.BundleUtils;
import art.arcane.react.util.project.world.WorldEntitySnapshots;
import art.arcane.volmlib.util.math.RNG;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class BukkitItemHost implements ItemHost, Listener {
  private static final double MERGE_SOUND_RADIUS = 32D;
  private static final int MAX_MERGE_SOUND_RECIPIENTS = 64;
  private final GlossDropNameIntegration glossDropNames;
  private final List<Consumer<GameEntity>> loadListeners = new CopyOnWriteArrayList<>();
  private final List<Consumer<GameEntity>> removeListeners = new CopyOnWriteArrayList<>();
  private final List<Consumer<PlayerPickup>> playerPickupListeners = new CopyOnWriteArrayList<>();
  private final List<Consumer<ContainerPickup>> containerPickupListeners = new CopyOnWriteArrayList<>();
  private final List<Consumer<SlotClick>> slotClickListeners = new CopyOnWriteArrayList<>();
  private final List<Consumer<Damage>> damageListeners = new CopyOnWriteArrayList<>();
  private int subscriptions;
  private boolean registered;

  public BukkitItemHost() {
    this(new GlossDropNameIntegration());
  }

  public BukkitItemHost(GlossDropNameIntegration glossDropNames) {
    this.glossDropNames = glossDropNames;
  }

  @Override
  public Subscription onSpawn(Consumer<GameEntity> listener) {
    return ItemHost.spawnsOf(BukkitReactPlatform.instance().spawnHost().orElse(null), true, listener);
  }

  @Override
  public Subscription onLoad(Consumer<GameEntity> listener) {
    return subscribe(loadListeners, listener);
  }

  @Override
  public Subscription onRemove(Consumer<GameEntity> listener) {
    return subscribe(removeListeners, listener);
  }

  @Override
  public Subscription onPlayerPickup(Consumer<PlayerPickup> listener) {
    return subscribe(playerPickupListeners, listener);
  }

  @Override
  public Subscription onContainerPickup(Consumer<ContainerPickup> listener) {
    return subscribe(containerPickupListeners, listener);
  }

  @Override
  public Subscription onSlotClick(Consumer<SlotClick> listener) {
    return subscribe(slotClickListeners, listener);
  }

  @Override
  public Subscription onDamage(Consumer<Damage> listener) {
    return subscribe(damageListeners, listener);
  }

  @Override
  public Subscription onOrbSpawn(Consumer<GameEntity> listener) {
    return ItemHost.spawnsOf(BukkitReactPlatform.instance().spawnHost().orElse(null), false, listener);
  }

  private synchronized <T> Subscription subscribe(List<T> listeners, T listener) {
    listeners.add(listener);
    subscriptions++;
    if (!registered && React.instance != null) {
      React.instance.registerListener(this);
      registered = true;
    }
    return () -> unsubscribe(listeners, listener);
  }

  private synchronized <T> void unsubscribe(List<T> listeners, T listener) {
    if (!listeners.remove(listener)) {
      return;
    }
    subscriptions--;
    if (subscriptions == 0 && registered) {
      registered = false;
      if (React.instance != null) {
        React.instance.unregisterListener(this);
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntitiesLoadEvent event) {
    if (loadListeners.isEmpty()) {
      return;
    }
    for (Entity entity : event.getEntities()) {
      if (entity instanceof Item item) {
        dispatch(loadListeners, item);
      }
    }
  }

  @EventHandler
  public void on(EntityRemoveEvent event) {
    if (event.getEntity() instanceof Item item) {
      dispatch(removeListeners, item);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityPickupItemEvent event) {
    if (event.getEntity() instanceof Player player) {
      BukkitPlayerPickup pickup = new BukkitPlayerPickup(event, player);
      for (Consumer<PlayerPickup> listener : playerPickupListeners) {
        if (event.isCancelled()) {
          return;
        }
        listener.accept(pickup);
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(InventoryClickEvent event) {
    if (event.getWhoClicked() instanceof Player player) {
      BukkitSlotClick click = new BukkitSlotClick(event, player);
      for (Consumer<SlotClick> listener : slotClickListeners) {
        if (event.isCancelled()) {
          return;
        }
        listener.accept(click);
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(InventoryPickupItemEvent event) {
    BukkitContainerPickup pickup = new BukkitContainerPickup(event);
    for (Consumer<ContainerPickup> listener : containerPickupListeners) {
      if (event.isCancelled()) {
        return;
      }
      listener.accept(pickup);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageEvent event) {
    if (event.getEntity() instanceof Item item) {
      BukkitDamage damage = new BukkitDamage(event, item);
      for (Consumer<Damage> listener : damageListeners) {
        if (event.isCancelled()) {
          return;
        }
        listener.accept(damage);
      }
    }
  }

  private static void dispatch(List<Consumer<GameEntity>> listeners, Entity entity) {
    if (listeners.isEmpty()) {
      return;
    }
    GameEntity wrapped = new BukkitGameEntity(entity);
    for (Consumer<GameEntity> listener : listeners) {
      listener.accept(wrapped);
    }
  }

  @Override
  public GameEntity entity(Object handle) {
    return new BukkitGameEntity((Entity) handle);
  }

  @Override
  public boolean isItem(GameEntity entity) {
    return entity.handle() instanceof Item;
  }

  @Override
  public boolean isLive(GameEntity entity) {
    Entity bukkit = BukkitGameEntity.bukkit(entity);
    return !bukkit.isDead() && bukkit.isValid();
  }

  @Override
  public List<GameEntity> scan(ReactWorld world, int budget) {
    List<Entity> entities = WorldEntitySnapshots.next((World) world.handle(), budget);
    if (entities.isEmpty()) {
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

  @Override
  public void discard(GameEntity entity) {
    BukkitGameEntity.bukkit(entity).remove();
  }

  @Override
  public String customName(GameEntity entity) {
    return BukkitGameEntity.bukkit(entity).getCustomName();
  }

  @Override
  public String material(GameEntity item) {
    return stack(item).getType().name();
  }

  @Override
  public int amount(GameEntity item) {
    return stack(item).getAmount();
  }

  @Override
  public int maxStackSize(GameEntity item) {
    return stack(item).getMaxStackSize();
  }

  @Override
  public boolean isSimilar(GameEntity item, GameEntity other) {
    return stack(item).isSimilar(stack(other));
  }

  @Override
  public void setAmount(GameEntity item, int amount) {
    ItemStack updated = stack(item).clone();
    updated.setAmount(amount);
    bukkitItem(item).setItemStack(updated);
  }

  @Override
  public void setTicksLived(GameEntity item, int ticks) {
    BukkitGameEntity.bukkit(item).setTicksLived(ticks);
  }

  @Override
  public boolean isSuperStack(GameEntity item) {
    return BundleUtils.isFlagged(stack(item));
  }

  @Override
  public int superStackCount(GameEntity item) {
    return BundleUtils.superStackCount(stack(item));
  }

  @Override
  public boolean bundle(GameEntity source, GameEntity target, int maxBundle) {
    ItemStack bundled = BundleUtils.merge(stack(source), stack(target), maxBundle);
    if (bundled == null) {
      return false;
    }
    bukkitItem(target).setItemStack(bundled);
    return true;
  }

  @Override
  public void discardMerged(GameEntity item) {
    Item bukkit = bukkitItem(item);
    if (React.instance != null) {
      FeatureHopperItemIndex hopperItemIndex = React.feature(FeatureHopperItemIndex.class);
      if (hopperItemIndex != null && hopperItemIndex.getItemIndex() != null) {
        hopperItemIndex.getItemIndex().removeItem(bukkit.getUniqueId());
      }
    }
    bukkit.remove();
  }

  @Override
  public void mergeEffect(GameEntity item, GameEntity into, double radius, boolean sound) {
    Item source = bukkitItem(item);
    Location from = source.getLocation();
    Location buf = from.clone();
    source.getWorld().spawnParticle(Particle.ITEM, from, 7, 0.1, 0.1, 0.1, 0.1, source.getItemStack());

    Location target = BukkitGameEntity.bukkit(into).getLocation();
    double distance = target.distance(from);
    int steps = Math.max(1, (int) Math.ceil(radius * 2.0D));
    if (distance > 1.0E-6D) {
      Vector step = target.clone().subtract(from).toVector().multiply(1.0D / steps);
      for (int index = 0; index < steps; index++) {
        buf.add(step);
        source.getWorld().spawnParticle(Particle.ITEM, buf, 3, 0, 0, 0, 0, source.getItemStack());
      }
    }

    if (sound) {
      playMergeSound(from);
    }
  }

  public void playMergeSound(Location source) {
    World world = source.getWorld();
    if (world == null) {
      return;
    }

    UUID worldId = world.getUID();
    double x = source.getX();
    double y = source.getY();
    double z = source.getZ();
    Sound sound = Sound.sound(
        Key.key("minecraft:item.bundle.insert"),
        Sound.Source.NEUTRAL,
        0.5f,
        1.2f + RNG.r.f(-0.1f, 0.1f)
    );
    NearbyPlayerIndexController controller = React.controller(NearbyPlayerIndexController.class);
    if (controller == null) {
      return;
    }

    List<NearbyPlayerIndexController.PlayerViewSnapshot> recipients = controller.playerSnapshotsInColumn(
        world,
        x,
        z,
        MERGE_SOUND_RADIUS,
        MAX_MERGE_SOUND_RECIPIENTS
    );
    for (NearbyPlayerIndexController.PlayerViewSnapshot recipient : recipients) {
      Player player = Bukkit.getPlayer(recipient.playerId());
      if (player == null) {
        continue;
      }

      try {
        J.runEntity(
            player,
            () -> playMergeSoundOwned(player, worldId, x, y, z, sound),
            0,
            null
        );
      } catch (Throwable throwable) {
        React.reportError(throwable);
      }
    }
  }

  private void playMergeSoundOwned(Player player, UUID worldId, double x, double y, double z, Sound sound) {
    if (!J.isOwnedByCurrentRegion(player) || !player.isOnline()) {
      return;
    }

    World playerWorld = player.getWorld();
    if (playerWorld == null || !worldId.equals(playerWorld.getUID())) {
      return;
    }

    React.audiences().player(player).playSound(sound, x, y, z);
  }

  @Override
  public boolean refreshLabel(GameEntity item, String headerFormat, String entryFormat, String moreFormat, int entryLimit) {
    return glossDropNames.refresh(bukkitItem(item), headerFormat, entryFormat, moreFormat, entryLimit);
  }

  @Override
  public void clearLabel(GameEntity item) {
    glossDropNames.remove(bukkitItem(item));
  }

  @Override
  public int experience(GameEntity orb) {
    return ((ExperienceOrb) orb.handle()).getExperience();
  }

  @Override
  public void setExperience(GameEntity orb, int experience) {
    ((ExperienceOrb) orb.handle()).setExperience(experience);
  }

  @Override
  public List<GameEntity> nearbyOrbs(GameEntity orb, double radius) {
    ExperienceOrb bukkit = (ExperienceOrb) orb.handle();
    List<GameEntity> result = new ArrayList<>();
    for (Entity nearby : bukkit.getNearbyEntities(radius, radius, radius)) {
      if (nearby instanceof ExperienceOrb other && other != bukkit) {
        result.add(new BukkitGameEntity(other));
      }
    }
    return result;
  }

  private static Item bukkitItem(GameEntity item) {
    return (Item) item.handle();
  }

  private static ItemStack stack(GameEntity item) {
    return bukkitItem(item).getItemStack();
  }

  private static void give(Player player, List<ItemStack> contents) {
    for (ItemStack content : contents) {
      player.getInventory().addItem(content).values().forEach(leftover -> player.getWorld().dropItem(player.getLocation(), leftover));
    }
  }

  private static final class BukkitPlayerPickup implements PlayerPickup {
    private final EntityPickupItemEvent event;
    private final Player player;
    private final GameEntity item;

    private BukkitPlayerPickup(EntityPickupItemEvent event, Player player) {
      this.event = event;
      this.player = player;
      this.item = new BukkitGameEntity(event.getItem());
    }

    @Override
    public GameEntity item() {
      return item;
    }

    @Override
    public boolean isCancelled() {
      return event.isCancelled();
    }

    @Override
    public void cancel() {
      event.setCancelled(true);
    }

    @Override
    public void giveContents() {
      give(player, BundleUtils.explode(event.getItem().getItemStack()));
    }

    @Override
    public void playUnpackSound() {
      Location location = event.getItem().getLocation();
      React.audiences().player(player).playSound(Sound.sound(
          Key.key("minecraft:item.bundle.drop_contents"),
          Sound.Source.PLAYER,
          1f,
          0.85f + RNG.r.f(-0.1f, 0.1f)
      ), location.getX(), location.getY(), location.getZ());
    }
  }

  private static final class BukkitSlotClick implements SlotClick {
    private final InventoryClickEvent event;
    private final Player player;

    private BukkitSlotClick(InventoryClickEvent event, Player player) {
      this.event = event;
      this.player = player;
    }

    @Override
    public boolean isSuperStack() {
      ItemStack current = event.getCurrentItem();
      return current != null && BundleUtils.isFlagged(current);
    }

    @Override
    public void unpack() {
      List<ItemStack> items = BundleUtils.explode(event.getCurrentItem());
      event.setCancelled(true);
      event.setCurrentItem(null);
      give(player, items);
    }
  }

  private static final class BukkitContainerPickup implements ContainerPickup {
    private final InventoryPickupItemEvent event;
    private final GameEntity item;
    private int kept;

    private BukkitContainerPickup(InventoryPickupItemEvent event) {
      this.event = event;
      this.item = new BukkitGameEntity(event.getItem());
    }

    @Override
    public GameEntity item() {
      return item;
    }

    @Override
    public boolean isCancelled() {
      return event.isCancelled();
    }

    @Override
    public void cancel() {
      event.setCancelled(true);
    }

    @Override
    public Insertion insertContents() {
      Item bukkit = event.getItem();
      List<ItemStack> leftovers = insert(event.getInventory(), BundleUtils.explode(bukkit.getItemStack()));
      if (leftovers.isEmpty()) {
        return Insertion.INSERTED;
      }

      ItemStack residualBundle = BundleUtils.createBundle(leftovers);
      if (residualBundle != null) {
        bukkit.setItemStack(residualBundle);
        kept = BundleUtils.getTotalCount(leftovers);
        return Insertion.KEPT;
      }

      Location location = bukkit.getLocation();
      World world = bukkit.getWorld();
      for (ItemStack leftover : leftovers) {
        world.dropItemNaturally(location, leftover);
      }
      return Insertion.DROPPED;
    }

    @Override
    public int keptCount() {
      return kept;
    }

    private static List<ItemStack> insert(Inventory inventory, List<ItemStack> contents) {
      List<ItemStack> leftovers = new ArrayList<>();
      for (ItemStack content : contents) {
        Map<Integer, ItemStack> overflow = inventory.addItem(content.clone());
        leftovers.addAll(overflow.values());
      }
      return leftovers;
    }
  }

  private static final class BukkitDamage implements Damage {
    private final EntityDamageEvent event;
    private final GameEntity item;

    private BukkitDamage(EntityDamageEvent event, Item item) {
      this.event = event;
      this.item = new BukkitGameEntity(item);
    }

    @Override
    public GameEntity item() {
      return item;
    }

    @Override
    public boolean isCancelled() {
      return event.isCancelled();
    }

    @Override
    public void cancel() {
      event.setCancelled(true);
    }

    @Override
    public boolean isVoid() {
      return event.getCause() == EntityDamageEvent.DamageCause.VOID;
    }
  }
}
