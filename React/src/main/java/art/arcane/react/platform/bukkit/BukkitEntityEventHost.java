package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.platform.EntityEventHost;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.react.platform.Subscription;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class BukkitEntityEventHost implements EntityEventHost, Listener {
  private final List<Consumer<Damage>> damageListeners = new CopyOnWriteArrayList<>();
  private final List<Consumer<Launch>> launchListeners = new CopyOnWriteArrayList<>();
  private int subscriptions;
  private boolean registered;

  @Override
  public Subscription onDamage(Consumer<Damage> listener) {
    return subscribe(damageListeners, listener);
  }

  @Override
  public Subscription onLaunch(Consumer<Launch> listener) {
    return subscribe(launchListeners, listener);
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

  @EventHandler
  public void on(EntityDamageEvent event) {
    if (damageListeners.isEmpty()) {
      return;
    }
    BukkitDamage damage = new BukkitDamage(event);
    for (Consumer<Damage> listener : damageListeners) {
      listener.accept(damage);
    }
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void on(ProjectileLaunchEvent event) {
    BukkitLaunch launch = new BukkitLaunch(event);
    for (Consumer<Launch> listener : launchListeners) {
      if (event.isCancelled()) {
        return;
      }
      listener.accept(launch);
    }
  }

  @Override
  public boolean isMonster(GameEntity entity) {
    return entity.handle() instanceof Monster;
  }

  @Override
  public boolean isBubbled(GameEntity entity) {
    Location location = BukkitGameEntity.bukkit(entity).getLocation();
    World world = location.getWorld();
    if (world == null) {
      return false;
    }
    Block block = world.getBlockAt(location);
    Block blockBelow = block.getRelative(BlockFace.DOWN);
    if (blockBelow.isLiquid() || block.isLiquid()) {
      if (block.getType().equals(Material.BUBBLE_COLUMN) || blockBelow.getType().equals(Material.BUBBLE_COLUMN)) {
        return true;
      } else {
        return block.getType().equals(Material.SOUL_SAND) || blockBelow.getType().equals(Material.SOUL_SAND);
      }
    }
    return false;
  }

  @Override
  public boolean nearPlayer(GameEntity entity, double radius) {
    return React.hasNearbyPlayer(BukkitGameEntity.bukkit(entity).getLocation(), radius);
  }

  @Override
  public void removeLater(GameEntity entity, int seconds) {
    React.kill(BukkitGameEntity.bukkit(entity), seconds);
  }

  private static final class BukkitDamage implements Damage {
    private final EntityDamageEvent event;
    private final GameEntity entity;

    private BukkitDamage(EntityDamageEvent event) {
      this.event = event;
      this.entity = new BukkitGameEntity(event.getEntity());
    }

    @Override
    public GameEntity entity() {
      return entity;
    }

    @Override
    public boolean isFireTick() {
      return EntityDamageEvent.DamageCause.FIRE_TICK.equals(event.getCause());
    }
  }

  private static final class BukkitLaunch implements Launch {
    private final ProjectileLaunchEvent event;
    private final GameEntity projectile;

    private BukkitLaunch(ProjectileLaunchEvent event) {
      this.event = event;
      this.projectile = new BukkitGameEntity(event.getEntity());
    }

    @Override
    public GameEntity projectile() {
      return projectile;
    }

    @Override
    public Optional<ReactPlayer> shooter() {
      return event.getEntity().getShooter() instanceof Player player
          ? Optional.of(new BukkitReactPlayer(player))
          : Optional.empty();
    }

    @Override
    public void cancel() {
      event.setCancelled(true);
    }
  }
}
