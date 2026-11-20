package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.core.bridge.AgentHookSlot;
import art.arcane.react.nms.NmsBridges;
import art.arcane.react.platform.ExplosionHost;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.Subscription;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.data.B;
import art.arcane.react.util.project.world.FastWorld;
import art.arcane.volmlib.nativelib.monitor.ExplosionDecision;
import art.arcane.volmlib.nativelib.monitor.ExplosionHook;
import art.arcane.volmlib.nativelib.monitor.ExplosionPacketSuppressor;
import art.arcane.volmlib.nativelib.monitor.NativeMonitor;
import org.bukkit.Bukkit;
import org.bukkit.ExplosionResult;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.Cancellable;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class BukkitExplosionHost implements ExplosionHost {
  private final AgentHookSlot hookSlot;

  public BukkitExplosionHost() {
    this(new AgentHookSlot());
  }

  BukkitExplosionHost(AgentHookSlot hookSlot) {
    this.hookSlot = hookSlot;
  }

  @Override
  public Subscription onExplode(ListenerPriority priority, boolean ignoreCancelled, Consumer<Explosion> listener) {
    Subscription blocks = BukkitEvents.subscribe(BlockExplodeEvent.class, priority, ignoreCancelled,
        event -> listener.accept(blockExplosion(event)));
    Subscription entities = BukkitEvents.subscribe(EntityExplodeEvent.class, priority, ignoreCancelled,
        event -> listener.accept(entityExplosion(event)));
    return () -> {
      blocks.cancel();
      entities.cancel();
    };
  }

  @Override
  public Subscription onTntPrimed(ListenerPriority priority, boolean ignoreCancelled, Consumer<PrimedTnt> listener) {
    return BukkitEvents.subscribe(EntitySpawnEvent.class, priority, ignoreCancelled, event -> primed(event, listener));
  }

  static void primed(EntitySpawnEvent event, Consumer<PrimedTnt> listener) {
    if (event.getEntity() instanceof TNTPrimed tnt) {
      listener.accept(new BukkitPrimedTnt(event, tnt));
    }
  }

  @Override
  public Subscription onWorldUnload(ListenerPriority priority, boolean ignoreCancelled, Consumer<WorldUnload> listener) {
    return BukkitEvents.subscribe(WorldUnloadEvent.class, priority, ignoreCancelled, event -> listener.accept(new BukkitWorldUnload(event)));
  }

  static Explosion blockExplosion(BlockExplodeEvent event) {
    Block block = event.getBlock();
    GameLocation location = block == null ? null : BukkitGameEntity.location(block.getLocation());
    return new BukkitExplosion(event, location, true, true, event::blockList, event::getYield);
  }

  static Explosion entityExplosion(EntityExplodeEvent event) {
    return new BukkitExplosion(event, BukkitGameEntity.location(event.getLocation()), false,
        destroysBlocks(event.getExplosionResult()), event::blockList, event::getYield);
  }

  private static boolean destroysBlocks(ExplosionResult result) {
    if (result == null) {
      return false;
    }
    return switch (result) {
      case DESTROY, DESTROY_WITH_DECAY -> true;
      case KEEP, TRIGGER_BLOCK -> false;
    };
  }

  @Override
  public String blockType(GameLocation location) {
    Block block = block(location);
    return block == null ? null : block.getType().name();
  }

  @Override
  public void dropContents(GameLocation location) {
    Block block = block(location);
    if (block == null) {
      return;
    }
    if (block.getState() instanceof Container container) {
      ItemStack[] contents = container.getInventory().getContents();
      for (ItemStack item : contents) {
        if (item != null) {
          block.getWorld().dropItemNaturally(block.getLocation(), item);
        }
      }
      container.getInventory().clear();
    } else {
      block.getDrops(null).forEach(drop -> block.getWorld().dropItemNaturally(block.getLocation(), drop));
    }
  }

  @Override
  public void clear(GameLocation location, boolean fastBlockUpdates) {
    Block block = block(location);
    if (block != null) {
      FastWorld.set(block, B.getAir(), fastBlockUpdates);
    }
  }

  @Override
  public void explode(GameLocation location, float power) {
    Block block = block(location);
    if (block != null) {
      block.getWorld().createExplosion(block.getLocation(), power, false, true);
    }
  }

  private static Block block(GameLocation location) {
    World world = location == null ? null : Bukkit.getWorld(location.worldId());
    return world == null ? null : world.getBlockAt(new Location(world, location.blockX(), location.blockY(), location.blockZ()));
  }

  @Override
  public void installPacketHooks(PacketHooks hooks) {
    NativeMonitor bridge = NmsBridges.get();
    if (bridge == null) {
      return;
    }
    ExplosionHook hook = new ExplosionHook() {
      @Override
      public void observe(Location center) {
        hooks.observe(BukkitGameEntity.location(center));
      }

      @Override
      public ExplosionDecision onExplodePacket(World world, double x, double y, double z, float radius) {
        return ExplosionDecision.BROADCAST;
      }
    };
    ExplosionPacketSuppressor suppressor = (World world, double x, double y, double z, float radius, int packetCount) ->
        world != null && hooks.suppress(world.getUID(), x, y, z, radius, packetCount);
    hookSlot.install(() -> {
      boolean observing = bridge.installExplosionHook(hook);
      boolean suppressing = !J.isFoliaThreading() && bridge.installExplosionPacketSuppressor(suppressor);
      hooks.installed(observing, suppressing);
    });
  }

  @Override
  public void uninstallPacketHooks() {
    NativeMonitor bridge = NmsBridges.get();
    hookSlot.release(() -> {
      if (bridge != null) {
        bridge.uninstallExplosionHook();
        bridge.uninstallExplosionPacketSuppressor();
      }
    });
  }

  @Override
  public boolean canBroadcast() {
    return NmsBridges.get() != null;
  }

  @Override
  public WorldState worldState(UUID worldId) {
    Server server = Bukkit.getServer();
    if (server == null) {
      return WorldState.UNKNOWN;
    }
    return server.getWorld(worldId) == null ? WorldState.UNLOADED : WorldState.LOADED;
  }

  @Override
  public boolean broadcastMergedExplosion(UUID worldId, double x, double y, double z, float radius, double rangeBlocks) {
    NativeMonitor bridge = NmsBridges.get();
    Server server = Bukkit.getServer();
    World world = server == null ? null : server.getWorld(worldId);
    return bridge != null && world != null && bridge.broadcastMergedExplosion(world, x, y, z, radius, rangeBlocks);
  }

  private static final class BukkitExplosion implements Explosion {
    private final Cancellable event;
    private final GameLocation location;
    private final boolean fromBlock;
    private final boolean destroysBlocks;
    private final Supplier<List<Block>> blocks;
    private final DoubleSupplier yield;

    private BukkitExplosion(Cancellable event, GameLocation location, boolean fromBlock, boolean destroysBlocks,
                            Supplier<List<Block>> blocks, DoubleSupplier yield) {
      this.event = event;
      this.location = location;
      this.fromBlock = fromBlock;
      this.destroysBlocks = destroysBlocks;
      this.blocks = blocks;
      this.yield = yield;
    }

    private List<Block> list() {
      return blocks.get();
    }

    @Override
    public GameLocation location() {
      return location;
    }

    @Override
    public boolean fromBlock() {
      return fromBlock;
    }

    @Override
    public boolean destroysBlocks() {
      return destroysBlocks;
    }

    @Override
    public float yield() {
      return (float) yield.getAsDouble();
    }

    @Override
    public List<ExplodedBlock> blocks() {
      List<Block> source = list();
      List<ExplodedBlock> exploded = new ArrayList<>(source.size());
      for (Block block : source) {
        exploded.add(block == null ? null : exploded(block));
      }
      return exploded;
    }

    @Override
    public void removeBlocks(Predicate<ExplodedBlock> filter) {
      list().removeIf(block -> block != null && filter.test(exploded(block)));
    }

    private static ExplodedBlock exploded(Block block) {
      return new ExplodedBlock(BukkitGameEntity.location(block.getLocation()), block.getType().name());
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

  private record BukkitPrimedTnt(EntitySpawnEvent event, TNTPrimed tnt) implements PrimedTnt {
    @Override
    public int fuseTicks() {
      return tnt.getFuseTicks();
    }

    @Override
    public void setFuseTicks(int ticks) {
      tnt.setFuseTicks(ticks);
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

  private record BukkitWorldUnload(WorldUnloadEvent event) implements WorldUnload {
    @Override
    public UUID worldId() {
      return event.getWorld().getUID();
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
