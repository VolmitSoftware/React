package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.content.tweak.TweakFastDrops;
import art.arcane.react.core.bridge.AgentHookSlot;
import art.arcane.react.nms.NmsBridges;
import art.arcane.react.util.data.B;
import art.arcane.volmlib.nativelib.NativeAdapters;
import art.arcane.volmlib.nativelib.monitor.FallingBlockTickHook;
import art.arcane.volmlib.nativelib.monitor.NativeMonitor;
import art.arcane.volmlib.nativelib.monitor.NativeWorldAccess;
import art.arcane.volmlib.nativelib.monitor.TickDecision;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.platform.BlockHost;
import art.arcane.react.platform.ChunkRef;
import art.arcane.react.platform.GameEntity;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.Subscription;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.FluidLevelChangeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class BukkitBlockHost implements BlockHost {
  private final AgentHookSlot fallingHookSlot = new AgentHookSlot();
  private volatile NativeWorldAccess fluidAccess;

  @Override
  public Subscription onBreak(ListenerPriority priority, boolean ignoreCancelled, Consumer<BlockEvent> listener) {
    return BukkitEvents.subscribe(BlockBreakEvent.class, priority, ignoreCancelled, event -> listener.accept(new Broken(event)));
  }

  @Override
  public Subscription onPlace(ListenerPriority priority, boolean ignoreCancelled, Consumer<BlockEvent> listener) {
    return BukkitEvents.subscribe(BlockPlaceEvent.class, priority, ignoreCancelled, event -> listener.accept(new Placed(event)));
  }

  @Override
  public Subscription onPhysics(ListenerPriority priority, boolean ignoreCancelled, Consumer<PhysicsEvent> listener) {
    return BukkitEvents.subscribe(BlockPhysicsEvent.class, priority, ignoreCancelled, event -> listener.accept(new Physics(event)));
  }

  @Override
  public Subscription onPiston(ListenerPriority priority, boolean ignoreCancelled, Consumer<PistonEvent> listener) {
    Subscription extend = BukkitEvents.subscribe(BlockPistonExtendEvent.class, priority, ignoreCancelled,
        event -> listener.accept(new Piston(event, true, event.getBlocks())));
    Subscription retract = BukkitEvents.subscribe(BlockPistonRetractEvent.class, priority, ignoreCancelled,
        event -> listener.accept(new Piston(event, false, event.getBlocks())));
    return () -> {
      extend.cancel();
      retract.cancel();
    };
  }

  @Override
  public Subscription onChunkLoad(Consumer<ChunkRef> listener) {
    return BukkitEvents.subscribe(ChunkLoadEvent.class, ListenerPriority.MONITOR, false, event -> listener.accept(ref(event.getChunk())));
  }

  @Override
  public Subscription onChunkUnload(Consumer<ChunkRef> listener) {
    return BukkitEvents.subscribe(ChunkUnloadEvent.class, ListenerPriority.MONITOR, false, event -> listener.accept(ref(event.getChunk())));
  }

  @Override
  public Subscription onWorldUnload(Consumer<UUID> listener) {
    return BukkitEvents.subscribe(WorldUnloadEvent.class, ListenerPriority.MONITOR, true, event -> listener.accept(event.getWorld().getUID()));
  }

  @Override
  public List<ChunkRef> nextLoadedChunks(int maximum) {
    ObserverController observer = React.controller(ObserverController.class);
    if (observer == null) {
      return List.of();
    }
    List<ObserverController.LoadedChunkTarget> targets = observer.nextLoadedChunkCoordinateBatch(maximum);
    List<ChunkRef> chunks = new ArrayList<>(targets.size());
    for (ObserverController.LoadedChunkTarget target : targets) {
      chunks.add(new ChunkRef(target.worldId(), target.chunkX(), target.chunkZ()));
    }
    return chunks;
  }

  @Override
  public boolean isChunkLoaded(UUID worldId, int chunkX, int chunkZ) {
    World world = Bukkit.getWorld(worldId);
    return world != null && world.isChunkLoaded(chunkX, chunkZ);
  }

  @Override
  public Subscription onFluidFlow(ListenerPriority priority, boolean ignoreCancelled, Consumer<FluidFlowEvent> listener) {
    return BukkitEvents.subscribe(BlockFromToEvent.class, priority, ignoreCancelled, event -> listener.accept(new Flow(event)));
  }

  @Override
  public Subscription onFluidLevelChange(ListenerPriority priority, boolean ignoreCancelled, Consumer<FluidLevelEvent> listener) {
    return BukkitEvents.subscribe(FluidLevelChangeEvent.class, priority, ignoreCancelled, event -> listener.accept(new FluidLevel(event)));
  }

  @Override
  public Subscription onFallingBlockChange(ListenerPriority priority, boolean ignoreCancelled, Consumer<FallingBlockEvent> listener) {
    return BukkitEvents.subscribe(EntityChangeBlockEvent.class, priority, ignoreCancelled, event -> {
      if (event.getEntity() instanceof FallingBlock falling) {
        listener.accept(new Falling(event, falling));
      }
    });
  }

  @Override
  public void installFallingTickHook(FallingTickHook hook, Runnable onInstalled) {
    NativeMonitor bridge = NmsBridges.get();
    if (bridge == null) {
      return;
    }
    FallingBlockTickHook adapted = (FallingBlock falling) -> hook.skipTick(new BukkitGameEntity(falling))
        ? TickDecision.SKIP
        : TickDecision.RUN_VANILLA;
    fallingHookSlot.install(() -> {
      if (bridge.installFallingBlockTickHook(adapted)) {
        onInstalled.run();
      }
    });
  }

  @Override
  public void uninstallFallingTickHook() {
    NativeMonitor bridge = NmsBridges.get();
    fallingHookSlot.release(() -> {
      if (bridge != null) {
        bridge.uninstallFallingBlockTickHook();
      }
    });
  }

  @Override
  public boolean canTickFluids() {
    fluidAccess = NativeAdapters.find(NativeWorldAccess.class).orElse(null);
    return fluidAccess != null;
  }

  @Override
  public boolean tickFluid(UUID worldId, int x, int y, int z, boolean water, boolean lava) {
    NativeWorldAccess access = fluidAccess;
    World world = Bukkit.getWorld(worldId);
    return access != null && world != null && access.tickFluid(world, x, y, z, water, lava);
  }

  @Override
  public int minHeight(UUID worldId) {
    World world = Bukkit.getWorld(worldId);
    return world == null ? 0 : world.getMinHeight();
  }

  @Override
  public int maxHeight(UUID worldId) {
    World world = Bukkit.getWorld(worldId);
    return world == null ? 0 : world.getMaxHeight();
  }

  @Override
  public String blockType(UUID worldId, int x, int y, int z) {
    World world = Bukkit.getWorld(worldId);
    return world == null ? "AIR" : world.getBlockAt(x, y, z).getType().name();
  }

  @Override
  public boolean isEmpty(UUID worldId, int x, int y, int z) {
    World world = Bukkit.getWorld(worldId);
    return world == null || world.getBlockAt(x, y, z).isEmpty();
  }

  @Override
  public void setBlock(UUID worldId, int x, int y, int z, String type, boolean applyPhysics) {
    World world = Bukkit.getWorld(worldId);
    Material material = Material.matchMaterial(type);
    if (world != null && material != null) {
      world.getBlockAt(x, y, z).setType(material, applyPhysics);
    }
  }

  @Override
  public void dropItem(GameLocation at, String itemType, int amount) {
    Location location = bukkit(at);
    Material material = Material.matchMaterial(itemType);
    if (location != null && material != null) {
      location.getWorld().dropItemNaturally(location, new ItemStack(material, amount));
    }
  }

  @Override
  public void giveItem(GameEntity player, GameLocation at, String itemType, int amount) {
    Location location = bukkit(at);
    Material material = Material.matchMaterial(itemType);
    if (location != null && material != null && BukkitGameEntity.bukkit(player) instanceof Player handle) {
      React.tweak(TweakFastDrops.class).giveItem(location, new BukkitReactPlayer(handle), new ItemStack(material, amount));
    }
  }

  @Override
  public int matchingAbove(GameLocation block, Object blockState) {
    World world = Bukkit.getWorld(block.worldId());
    if (world == null) {
      return 0;
    }
    int bonus = 0;
    for (int i = block.blockY() + 1; i < world.getMaxHeight() - 1; i++) {
      if (world.getBlockAt(block.blockX(), i, block.blockZ()).getBlockData().equals(blockState)) {
        bonus++;
      } else {
        break;
      }
    }
    return bonus;
  }

  @Override
  public void dropColumn(GameLocation block, Object blockState, int bonus) {
    Location blockLocation = bukkit(block);
    if (blockLocation == null || !(blockState instanceof BlockData d)) {
      return;
    }
    Block target = blockLocation.getBlock();
    if (!target.getBlockData().equals(d)) {
      return;
    }

    target.setBlockData(B.getAir());

    for (int i = 0; i < bonus; i++) {
      Block bx = target.getWorld().getBlockAt(target.getX(), target.getY() + 1 + i, target.getZ());
      if (!bx.getBlockData().equals(d)) {
        return;
      }

      bx.setBlockData(B.getAir(), false);
    }

    fallEffect(blockLocation, d);
    for (int i = target.getY(); i > target.getWorld().getMinHeight() + 1; i--) {
      Block bb = target.getWorld().getBlockAt(target.getX(), i, target.getZ());

      if (bb.getRelative(BlockFace.DOWN).getType().isSolid()) {
        for (int j = 0; j < bonus; j++) {
          bb.getWorld().getBlockAt(bb.getX(), bb.getY() + 1 + j, bb.getZ()).setBlockData(d, false);
        }

        bb.setBlockData(d, false);
        landEffect(bb.getLocation(), d);
        break;
      }

      if (!bb.isEmpty() && bb.isPassable() && !B.isFluid(bb.getBlockData())) {
        if (d.getMaterial().isItem()) {
          bb.getWorld().dropItemNaturally(bb.getLocation(), new ItemStack(d.getMaterial(), 1 + bonus));
        }
        break;
      }
    }
  }

  private static void fallEffect(Location at, BlockData item) {
    at.getWorld().playSound(at, item.getSoundGroup().getBreakSound(), 1f, 1f);
    at.getWorld().spawnParticle(Particle.FALLING_DUST, at.getBlock().getLocation().add(0.5, 0.5, 0.5), 6, 0.25, 0.5, 0.25, 0.1, item);
  }

  private static void landEffect(Location at, BlockData item) {
    at.getWorld().playSound(at, item.getSoundGroup().getPlaceSound(), 1f, 1f);
    if (item.getMaterial().isItem()) {
      at.getWorld().spawnParticle(Particle.ITEM, at.getBlock().getLocation().add(0.5, -0.5, 0.5), 24, 0.6, 0.6, 0.6, 0.15, new ItemStack(item.getMaterial(), 1));
    } else {
      at.getWorld().spawnParticle(Particle.BLOCK, at.getBlock().getLocation().add(0.5, -0.5, 0.5), 24, 0.6, 0.6, 0.6, 0.15, item);
    }
  }

  private static Location bukkit(GameLocation location) {
    World world = location == null ? null : Bukkit.getWorld(location.worldId());
    return world == null ? null : new Location(world, location.x(), location.y(), location.z());
  }

  private static ChunkRef ref(Chunk chunk) {
    return new ChunkRef(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
  }

  private static class Change<T extends org.bukkit.event.block.BlockEvent & Cancellable> implements BlockEvent {
    protected final T event;

    private Change(T event) {
      this.event = event;
    }

    @Override
    public GameLocation location() {
      return BukkitEvents.location(event.getBlock());
    }

    @Override
    public String type() {
      return event.getBlock().getType().name();
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

  private static final class Broken extends Change<BlockBreakEvent> {
    private Broken(BlockBreakEvent event) {
      super(event);
    }

    @Override
    public GameEntity player() {
      return new BukkitGameEntity(event.getPlayer());
    }
  }

  private static final class Placed extends Change<BlockPlaceEvent> {
    private Placed(BlockPlaceEvent event) {
      super(event);
    }

    @Override
    public GameEntity player() {
      return new BukkitGameEntity(event.getPlayer());
    }
  }

  private record Flow(BlockFromToEvent event) implements FluidFlowEvent {
    @Override
    public GameLocation from() {
      return BukkitEvents.location(event.getBlock());
    }

    @Override
    public String fromType() {
      return event.getBlock().getType().name();
    }

    @Override
    public GameLocation to() {
      return BukkitEvents.location(event.getToBlock());
    }

    @Override
    public String toType() {
      return event.getToBlock().getType().name();
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

  private record FluidLevel(FluidLevelChangeEvent event) implements FluidLevelEvent {
    @Override
    public GameLocation location() {
      return BukkitEvents.location(event.getBlock());
    }

    @Override
    public String type() {
      return event.getBlock().getType().name();
    }

    @Override
    public int level() {
      return event.getBlock().getBlockData() instanceof Levelled levelled ? levelled.getLevel() : -1;
    }

    @Override
    public String newType() {
      BlockData data = event.getNewData();
      return data == null ? null : data.getMaterial().name();
    }

    @Override
    public int newLevel() {
      return event.getNewData() instanceof Levelled levelled ? levelled.getLevel() : -1;
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

  private record Falling(EntityChangeBlockEvent event, FallingBlock falling) implements FallingBlockEvent {
    @Override
    public GameEntity entity() {
      return new BukkitGameEntity(falling);
    }

    @Override
    public GameLocation block() {
      return BukkitEvents.location(event.getBlock());
    }

    @Override
    public Object blockState() {
      return falling.getBlockData();
    }

    @Override
    public double velocityY() {
      return falling.getVelocity().getY();
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

  private static final class Physics extends Change<BlockPhysicsEvent> implements PhysicsEvent {
    private Physics(BlockPhysicsEvent event) {
      super(event);
    }

    @Override
    public String changedType() {
      return event.getChangedType().name();
    }
  }

  private record Piston(org.bukkit.event.block.BlockPistonEvent event, boolean extending, List<Block> moved) implements PistonEvent {
    @Override
    public GameLocation piston() {
      return BukkitEvents.location(event.getBlock());
    }

    @Override
    public List<GameLocation> blocks() {
      List<GameLocation> locations = new ArrayList<>(moved.size());
      for (Block block : moved) {
        locations.add(BukkitEvents.location(block));
      }
      return locations;
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
}
