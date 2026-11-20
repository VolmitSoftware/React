package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.platform.DropHost;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.react.platform.Subscription;
import art.arcane.volmlib.util.bukkit.BukkitExperience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class BukkitDropHost implements DropHost, Listener {
  private final List<DropHost.Listener<EntityDeath>> deaths = new CopyOnWriteArrayList<>();
  private final List<DropHost.Listener<BlockDrops>> blockDrops = new CopyOnWriteArrayList<>();
  private final List<DropHost.Listener<BlockBreak>> breaks = new CopyOnWriteArrayList<>();
  private boolean registered;

  @Override
  public Subscription onEntityDeath(DropHost.Listener<EntityDeath> listener) {
    return subscribe(deaths, listener);
  }

  @Override
  public Subscription onBlockDrops(DropHost.Listener<BlockDrops> listener) {
    return subscribe(blockDrops, listener);
  }

  @Override
  public Subscription onBlockBreak(DropHost.Listener<BlockBreak> listener) {
    return subscribe(breaks, listener);
  }

  private synchronized <T> Subscription subscribe(List<DropHost.Listener<T>> listeners, DropHost.Listener<T> listener) {
    listeners.add(listener);
    if (!registered && React.instance != null) {
      React.instance.registerListener(this);
      registered = true;
    }
    return () -> unsubscribe(listeners, listener);
  }

  private synchronized <T> void unsubscribe(List<DropHost.Listener<T>> listeners, DropHost.Listener<T> listener) {
    listeners.remove(listener);
    if (registered && deaths.isEmpty() && blockDrops.isEmpty() && breaks.isEmpty()) {
      registered = false;
      if (React.instance != null) {
        React.instance.unregisterListener(this);
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDeathEvent e) {
    if (deaths.isEmpty()) {
      return;
    }
    BukkitEntityDeath death = new BukkitEntityDeath(e);
    for (DropHost.Listener<EntityDeath> listener : deaths) {
      listener.on(death);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockDropItemEvent e) {
    if (blockDrops.isEmpty()) {
      return;
    }
    BukkitBlockDrops drops = new BukkitBlockDrops(e);
    for (DropHost.Listener<BlockDrops> listener : blockDrops) {
      listener.on(drops);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    if (breaks.isEmpty()) {
      return;
    }
    BukkitBlockBreak broken = new BukkitBlockBreak(e);
    for (DropHost.Listener<BlockBreak> listener : breaks) {
      listener.on(broken);
    }
  }

  @Override
  public Object copyStack(Object stack) {
    return ((ItemStack) stack).clone();
  }

  @Override
  public boolean isCreative(ReactPlayer player) {
    return player(player).getGameMode().equals(GameMode.CREATIVE);
  }

  @Override
  public boolean isSurvival(ReactPlayer player) {
    return player(player).getGameMode().equals(GameMode.SURVIVAL);
  }

  @Override
  public void awardExperience(ReactPlayer player, int experience) {
    BukkitExperience.award(player(player), experience);
  }

  @Override
  public boolean give(ReactPlayer player, Object stack, Object dropAt) {
    Player bukkit = player(player);
    boolean dropped = false;
    for (ItemStack leftover : bukkit.getInventory().addItem((ItemStack) stack).values()) {
      bukkit.getWorld().dropItemNaturally(dropAt == null ? bukkit.getLocation() : (Location) dropAt, leftover);
      dropped = true;
    }
    return dropped;
  }

  @Override
  public void dropNaturally(ReactPlayer player, Object dropAt, Object stack) {
    Player bukkit = player(player);
    bukkit.getWorld().dropItemNaturally(dropAt == null ? bukkit.getLocation() : (Location) dropAt, (ItemStack) stack);
  }

  @Override
  public void playSound(ReactPlayer player, String soundKey, float volume, float pitch) {
    // audience delivery: spigot Player has no playSound(net.kyori Sound)
    React.audiences().player(player(player)).playSound(Sound.sound(Key.key(soundKey), Sound.Source.PLAYER, volume, pitch));
  }

  private static Player player(ReactPlayer player) {
    return (Player) player.handle();
  }

  private record BukkitEntityDeath(EntityDeathEvent event) implements EntityDeath {
    @Override
    public ReactPlayer killer() {
      Player killer = event.getEntity().getKiller();
      return killer == null ? null : new BukkitReactPlayer(killer);
    }

    @Override
    public double killerDistanceSquared() {
      return event.getEntity().getKiller().getLocation().distanceSquared(event.getEntity().getLocation());
    }

    @Override
    public int droppedExperience() {
      return event.getDroppedExp();
    }

    @Override
    public void setDroppedExperience(int experience) {
      event.setDroppedExp(experience);
    }

    @Override
    public List<Object> takeDrops() {
      List<Object> drops = new ArrayList<>(event.getDrops());
      event.getDrops().clear();
      return drops;
    }
  }

  private record BukkitBlockDrops(BlockDropItemEvent event) implements BlockDrops {
    @Override
    public ReactPlayer player() {
      return new BukkitReactPlayer(event.getPlayer());
    }

    @Override
    public boolean fromContainer() {
      return event.getBlockState() instanceof InventoryHolder;
    }

    @Override
    public List<DroppedItem> items() {
      List<DroppedItem> items = new ArrayList<>();
      for (Item item : event.getItems()) {
        items.add(new BukkitDroppedItem(event, item));
      }
      return items;
    }
  }

  private record BukkitDroppedItem(BlockDropItemEvent event, Item item) implements DroppedItem {
    @Override
    public Object copyStack() {
      return item.getItemStack().clone();
    }

    @Override
    public boolean remove() {
      return event.getItems().remove(item);
    }

    @Override
    public Object dropLocation() {
      return item.getLocation();
    }
  }

  private record BukkitBlockBreak(BlockBreakEvent event) implements BlockBreak {
    @Override
    public ReactPlayer player() {
      return new BukkitReactPlayer(event.getPlayer());
    }

    @Override
    public int experience() {
      return event.getExpToDrop();
    }

    @Override
    public void setExperience(int experience) {
      event.setExpToDrop(experience);
    }
  }
}
