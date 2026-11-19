package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.api.event.layer.ServerTickEvent;
import art.arcane.react.platform.SamplerEventCounters;
import art.arcane.react.platform.SamplerEventCounters.Counter;
import art.arcane.react.util.project.world.HopperMoveContext;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.entity.TrialSpawnerSpawnEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.RemoteServerCommandEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.EnumMap;
import java.util.Map;

/**
 * Feeds {@link SamplerEventCounters} from Bukkit events. Each counter's listener is registered only
 * while a sampler subscribes to it, so hot events such as block physics stay unlistened otherwise.
 */
public final class BukkitSamplerEventBridge {
  private static final SamplerEventCounters COUNTERS = SamplerEventCounters.get();
  private static final Map<Counter, Listener> REGISTERED = new EnumMap<>(Counter.class);

  private BukkitSamplerEventBridge() {
  }

  public static void install() {
    synchronized (REGISTERED) {
      REGISTERED.clear();
    }
    COUNTERS.setActivationObserver((counter, active) -> sync(counter));
    for (Counter counter : Counter.values()) {
      sync(counter);
    }
  }

  public static void uninstall() {
    COUNTERS.setActivationObserver(null);
    synchronized (REGISTERED) {
      React plugin = React.instance;
      if (plugin != null) {
        for (Listener listener : REGISTERED.values()) {
          plugin.unregisterListener(listener);
        }
      }
      REGISTERED.clear();
    }
  }

  private static void sync(Counter counter) {
    React plugin = React.instance;
    if (plugin == null) {
      return;
    }

    synchronized (REGISTERED) {
      boolean active = COUNTERS.isActive(counter);
      Listener current = REGISTERED.get(counter);
      if (active && current == null) {
        Listener listener = listenerFor(counter);
        REGISTERED.put(counter, listener);
        plugin.registerListener(listener);
      } else if (!active && current != null) {
        REGISTERED.remove(counter);
        plugin.unregisterListener(current);
      }
    }
  }

  private static Listener listenerFor(Counter counter) {
    return switch (counter) {
      case SERVER_TICK -> new ServerTick();
      case CHUNK_LOAD -> new ChunkLoad();
      case CHUNK_GENERATE -> new ChunkGenerate();
      case CHUNK_UNLOAD -> new ChunkUnload();
      case CHUNK_LOAD_BEGIN -> new ChunkLoadBegin(false);
      case CHUNK_LOAD_END -> new ChunkLoadEnd(false);
      case CHUNK_GENERATE_BEGIN -> new ChunkLoadBegin(true);
      case CHUNK_GENERATE_END -> new ChunkLoadEnd(true);
      case WORLD_UNLOAD -> new WorldUnload();
      case WORLD_SAVE -> new WorldSave();
      case BLOCK_PHYSICS -> new BlockPhysics();
      case PISTON_MOVE -> new PistonMove();
      case REDSTONE -> new Redstone();
      case FLUID_FLOW -> new FluidFlow();
      case FLUID_EVENT -> new FluidEvent();
      case HOPPER_MOVE -> new HopperMove();
      case ENTITY_SPAWN -> new EntitySpawn();
      case SPAWNER_SPAWN -> new SpawnerSpawn();
      case COMMAND -> new Command();
    };
  }

  public static final class ServerTick implements Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    public void on(ServerTickEvent event) {
      COUNTERS.count(Counter.SERVER_TICK, null);
    }
  }

  public static final class ChunkLoad implements Listener {
    @EventHandler
    public void on(ChunkLoadEvent event) {
      COUNTERS.count(Counter.CHUNK_LOAD, event.getChunk());
    }
  }

  public static final class ChunkGenerate implements Listener {
    @EventHandler
    public void on(ChunkLoadEvent event) {
      if (event.isNewChunk()) {
        COUNTERS.count(Counter.CHUNK_GENERATE, event.getChunk());
      }
    }
  }

  public static final class ChunkUnload implements Listener {
    @EventHandler
    public void on(ChunkUnloadEvent event) {
      COUNTERS.count(Counter.CHUNK_UNLOAD, event.getChunk());
    }
  }

  public static final class ChunkLoadBegin implements Listener {
    private final boolean newChunksOnly;

    ChunkLoadBegin(boolean newChunksOnly) {
      this.newChunksOnly = newChunksOnly;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void on(ChunkLoadEvent event) {
      if (!newChunksOnly) {
        COUNTERS.count(Counter.CHUNK_LOAD_BEGIN, event.getChunk());
      } else if (event.isNewChunk()) {
        COUNTERS.count(Counter.CHUNK_GENERATE_BEGIN, event.getChunk());
      }
    }
  }

  public static final class ChunkLoadEnd implements Listener {
    private final boolean newChunksOnly;

    ChunkLoadEnd(boolean newChunksOnly) {
      this.newChunksOnly = newChunksOnly;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(ChunkLoadEvent event) {
      if (!newChunksOnly) {
        COUNTERS.count(Counter.CHUNK_LOAD_END, event.getChunk());
      } else if (event.isNewChunk()) {
        COUNTERS.count(Counter.CHUNK_GENERATE_END, event.getChunk());
      }
    }
  }

  public static final class WorldUnload implements Listener {
    @EventHandler
    public void on(WorldUnloadEvent event) {
      COUNTERS.count(Counter.WORLD_UNLOAD, event.getWorld());
    }
  }

  public static final class WorldSave implements Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    public void on(WorldSaveEvent event) {
      if (event.getWorld() != null) {
        COUNTERS.count(Counter.WORLD_SAVE, event.getWorld().getUID());
      }
    }
  }

  public static final class BlockPhysics implements Listener {
    @EventHandler(priority = EventPriority.LOW)
    public void on(BlockPhysicsEvent event) {
      COUNTERS.count(Counter.BLOCK_PHYSICS, event.getBlock());
    }
  }

  public static final class PistonMove implements Listener {
    @EventHandler
    public void on(BlockPistonExtendEvent event) {
      COUNTERS.count(Counter.PISTON_MOVE, event.getBlock(), event.getBlocks().size() + 2);
    }

    @EventHandler
    public void on(BlockPistonRetractEvent event) {
      COUNTERS.count(Counter.PISTON_MOVE, event.getBlock(), event.getBlocks().size() + 2);
    }
  }

  public static final class Redstone implements Listener {
    @EventHandler
    public void on(BlockRedstoneEvent event) {
      COUNTERS.count(Counter.REDSTONE, event.getBlock());
    }
  }

  public static final class FluidFlow implements Listener {
    @EventHandler
    public void on(BlockFromToEvent event) {
      if (event.getBlock().getBlockData() instanceof Levelled) {
        COUNTERS.count(Counter.FLUID_FLOW, event.getBlock());
      }
    }
  }

  public static final class FluidEvent implements Listener {
    @EventHandler
    public void on(BlockFromToEvent event) {
      if (event.getBlock().getBlockData() instanceof Levelled || event.getToBlock().getBlockData() instanceof Levelled) {
        COUNTERS.count(Counter.FLUID_EVENT, event.getBlock());
      }
    }
  }

  public static final class HopperMove implements Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    public void on(InventoryMoveItemEvent event) {
      Block hopper = HopperMoveContext.resolve(event).hopperBlock();
      if (hopper != null) {
        COUNTERS.count(Counter.HOPPER_MOVE, hopper);
      }
    }
  }

  public static final class EntitySpawn implements Listener {
    @EventHandler
    public void on(EntitySpawnEvent event) {
      COUNTERS.count(Counter.ENTITY_SPAWN, event.getEntity());
    }
  }

  public static final class SpawnerSpawn implements Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(SpawnerSpawnEvent event) {
      COUNTERS.count(Counter.SPAWNER_SPAWN, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(TrialSpawnerSpawnEvent event) {
      COUNTERS.count(Counter.SPAWNER_SPAWN, null);
    }
  }

  public static final class Command implements Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(PlayerCommandPreprocessEvent event) {
      COUNTERS.count(Counter.COMMAND, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(ServerCommandEvent event) {
      COUNTERS.count(Counter.COMMAND, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(RemoteServerCommandEvent event) {
      COUNTERS.count(Counter.COMMAND, null);
    }
  }
}
