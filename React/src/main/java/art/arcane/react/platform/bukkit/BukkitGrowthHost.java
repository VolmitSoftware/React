package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.core.controller.NearbyPlayerIndexController;
import art.arcane.react.core.controller.NearbyPlayerIndexController.PlayerViewSnapshot;
import art.arcane.react.platform.BlockHost;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.GrowthHost;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.PlayerPosition;
import art.arcane.react.platform.Subscription;
import art.arcane.react.util.project.world.FastWorld;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.event.Cancellable;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class BukkitGrowthHost implements GrowthHost {
  @Override
  public Subscription onSpread(ListenerPriority priority, boolean ignoreCancelled, Consumer<SpreadEvent> listener) {
    return BukkitEvents.subscribe(BlockSpreadEvent.class, priority, ignoreCancelled, event -> listener.accept(new Spread(event)));
  }

  @Override
  public Subscription onGrow(ListenerPriority priority, boolean ignoreCancelled, Consumer<GrowEvent> listener) {
    return BukkitEvents.subscribe(BlockGrowEvent.class, priority, ignoreCancelled, event -> listener.accept(new Grow(event)));
  }

  @Override
  public Subscription onForm(ListenerPriority priority, boolean ignoreCancelled, Consumer<ChangeEvent> listener) {
    return BukkitEvents.subscribe(BlockFormEvent.class, priority, ignoreCancelled,
        event -> listener.accept(new Change<>(event.getBlock(), event)));
  }

  @Override
  public Subscription onFade(ListenerPriority priority, boolean ignoreCancelled, Consumer<ChangeEvent> listener) {
    return BukkitEvents.subscribe(BlockFadeEvent.class, priority, ignoreCancelled,
        event -> listener.accept(new Change<>(event.getBlock(), event)));
  }

  @Override
  public Subscription onBurn(ListenerPriority priority, boolean ignoreCancelled, Consumer<BlockHost.BlockEvent> listener) {
    return BukkitEvents.subscribe(BlockBurnEvent.class, priority, ignoreCancelled,
        event -> listener.accept(new Change<>(event.getBlock(), event)));
  }

  @Override
  public Subscription onLeavesDecay(ListenerPriority priority, boolean ignoreCancelled, Consumer<BlockHost.BlockEvent> listener) {
    return BukkitEvents.subscribe(LeavesDecayEvent.class, priority, ignoreCancelled,
        event -> listener.accept(new Change<>(event.getBlock(), event)));
  }

  @Override
  public String type(GameLocation location) {
    Block block = block(location);
    return block == null ? null : block.getType().name();
  }

  @Override
  public int leaf(GameLocation location) {
    Block block = block(location);
    return block == null ? NOT_LEAVES : leaf(block.getBlockData());
  }

  static int leaf(BlockData data) {
    if (!(data instanceof Leaves leaves)) {
      return NOT_LEAVES;
    }
    return leaves.getDistance() | (leaves.isPersistent() ? PERSISTENT_LEAVES : 0);
  }

  @Override
  public ChunkBlocks chunk(UUID worldId, int chunkX, int chunkZ) {
    World world = worldId == null ? null : Bukkit.getWorld(worldId);
    if (world == null || !world.isChunkLoaded(chunkX, chunkZ)) {
      return null;
    }
    Chunk chunk = world.getChunkAt(chunkX, chunkZ);
    return chunk == null ? null : new Snapshot(world, chunk.getChunkSnapshot(true, false, false));
  }

  @Override
  public boolean advanceAge(GameLocation location, AgeStep step) {
    Block block = block(location);
    if (block == null || !(block.getBlockData() instanceof Ageable ageable)) {
      return false;
    }
    int age = ageable.getAge();
    int targetAge = step.targetAge(age, ageable.getMaximumAge());
    if (targetAge == age) {
      return false;
    }
    ageable.setAge(targetAge);
    try {
      block.setBlockData(ageable, false);
    } catch (Throwable ex) {
      React.reportError(ex);
      return false;
    }
    return true;
  }

  @Override
  public boolean boneMeal(GameLocation location) {
    Block block = block(location);
    if (block == null) {
      return false;
    }
    try {
      BlockState before = block.getState();
      boolean grew = block.applyBoneMeal(BlockFace.UP);
      if (!grew) {
        return false;
      }
      return block.getType() != before.getType();
    } catch (Throwable ex) {
      return false;
    }
  }

  @Override
  public void setState(GameLocation location, Object state, boolean applyPhysics) {
    Block block = block(location);
    if (block != null) {
      block.setBlockData((BlockData) state, applyPhysics);
    }
  }

  @Override
  public void fastSet(GameLocation location, Object state) {
    Block block = block(location);
    if (block != null) {
      FastWorld.set(block, (BlockData) state);
    }
  }

  @Override
  public void breakNaturally(GameLocation location, boolean fast) {
    Block block = block(location);
    if (block == null) {
      return;
    }
    if (fast) {
      FastWorld.breakNaturally(block);
    } else {
      block.breakNaturally();
    }
  }

  @Override
  public void playSound(GameLocation location, String sound, float volume, float pitch) {
    Block block = block(location);
    if (block != null) {
      block.getWorld().playSound(block.getLocation(), sound, volume, pitch);
    }
  }

  @Override
  public int randomTickSpeed(UUID worldId) {
    World world = worldId == null ? null : Bukkit.getWorld(worldId);
    if (world == null) {
      return 0;
    }
    try {
      Integer value = world.getGameRuleValue(BukkitGameRuleHost.randomTickSpeedRule());
      return value == null ? 3 : Math.max(0, value);
    } catch (Throwable ex) {
      return 3;
    }
  }

  @Override
  public int simulationDistance(UUID worldId) {
    World world = worldId == null ? null : Bukkit.getWorld(worldId);
    return world == null ? 0 : world.getSimulationDistance();
  }

  @Override
  public Optional<List<PlayerPosition>> simulatingPlayers() {
    NearbyPlayerIndexController index = React.controller(NearbyPlayerIndexController.class);
    if (index == null) {
      return Optional.empty();
    }
    Collection<PlayerViewSnapshot> snapshots = index.playerSnapshots();
    List<PlayerPosition> positions = new ArrayList<>(snapshots.size());
    for (PlayerViewSnapshot snapshot : snapshots) {
      positions.add(new PlayerPosition(snapshot.playerId(), snapshot.worldId(), snapshot.x(), snapshot.y(), snapshot.z()));
    }
    return Optional.of(positions);
  }

  @Override
  public boolean anyPlayerInColumn(UUID worldId, double x, double z, double blocks) {
    NearbyPlayerIndexController index = React.controller(NearbyPlayerIndexController.class);
    World world = worldId == null ? null : Bukkit.getWorld(worldId);
    return index != null && world != null && index.hasNearbyPlayerInColumn(world, x, z, blocks);
  }

  private static Block block(GameLocation location) {
    World world = location == null || location.worldId() == null ? null : Bukkit.getWorld(location.worldId());
    return world == null ? null : world.getBlockAt(location.blockX(), location.blockY(), location.blockZ());
  }

  private static class Change<T extends Cancellable> implements ChangeEvent {
    protected final Block block;
    protected final T event;

    private Change(Block block, T event) {
      this.block = block;
      this.event = event;
    }

    @Override
    public GameLocation location() {
      return BukkitEvents.location(block);
    }

    @Override
    public String type() {
      return block.getType().name();
    }

    @Override
    public Object state() {
      return block.getBlockData();
    }

    @Override
    public boolean isCancelled() {
      return event.isCancelled();
    }

    @Override
    public void cancel() {
      event.setCancelled(true);
    }
  }

  private static final class Grow extends Change<BlockGrowEvent> implements GrowEvent {
    private Grow(BlockGrowEvent event) {
      super(event.getBlock(), event);
    }

    @Override
    public Object newState() {
      return event.getNewState().getBlockData().clone();
    }
  }

  private static final class Spread extends Change<BlockSpreadEvent> implements SpreadEvent {
    private Spread(BlockSpreadEvent event) {
      super(event.getBlock(), event);
    }

    @Override
    public GameLocation source() {
      return BukkitEvents.location(event.getSource());
    }
  }

  private record Snapshot(World world, ChunkSnapshot snapshot) implements ChunkBlocks {
    @Override
    public int minHeight() {
      return world.getMinHeight();
    }

    @Override
    public int maxHeight() {
      return world.getMaxHeight();
    }

    @Override
    public int highestY(int x, int z) {
      return snapshot.getHighestBlockYAt(x, z);
    }

    @Override
    public String type(int x, int y, int z) {
      return snapshot.getBlockType(x, y, z).name();
    }

    @Override
    public int leaf(int x, int y, int z) {
      return BukkitGrowthHost.leaf(snapshot.getBlockData(x, y, z));
    }
  }
}
