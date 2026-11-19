package art.arcane.react.content.sampler;

import art.arcane.react.api.sampler.Sampler;
import art.arcane.react.nms.NmsBridges;
import art.arcane.react.platform.WorldStatsHost;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.react.util.project.world.WorldEntitySnapshots;
import art.arcane.volmlib.nativelib.monitor.NativeMonitor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;

public final class BukkitWorldStatsHost implements WorldStatsHost {
  @Override
  public int worldCount() {
    return Bukkit.getWorlds().size();
  }

  @Override
  public int loadedChunkCount() {
    return countWorldChunks(Bukkit.getWorlds());
  }

  static int countWorldChunks(List<World> worlds) {
    long count = 0L;
    for (World world : worlds) {
      count += Math.max(0, WorldEntitySnapshots.chunkCount(world));
      if (count >= Integer.MAX_VALUE) {
        return Integer.MAX_VALUE;
      }
    }
    return (int) count;
  }

  @Override
  public int entityCount() {
    return BukkitEntityTracker.countWorldEntities(Bukkit.getWorlds());
  }

  @Override
  public long blockEntityCount() {
    long total = 0L;
    for (World world : Bukkit.getWorlds()) {
      total += world.getTileEntityCount();
    }
    return total;
  }

  @Override
  public long tickingBlockEntityCount() {
    long total = 0L;
    for (World world : Bukkit.getWorlds()) {
      total += world.getTickableTileEntityCount();
    }
    return total;
  }

  @Override
  public long forceLoadedChunkCount() {
    long total = 0L;
    for (World world : Bukkit.getWorlds()) {
      total += world.getForceLoadedChunks().size();
    }
    return total;
  }

  @Override
  public OptionalLong pluginChunkTicketCount() {
    boolean folia = J.isFoliaThreading();
    NativeMonitor bridge = folia ? NmsBridges.get() : null;
    if (folia && bridge == null) {
      return OptionalLong.empty();
    }

    long total = 0L;
    for (World world : Bukkit.getWorlds()) {
      if (folia) {
        total += bridge.countPluginChunkTickets(world);
      } else {
        for (Collection<Chunk> tickets : world.getPluginChunkTickets().values()) {
          total += tickets.size();
        }
      }
    }
    return OptionalLong.of(total);
  }

  @Override
  public List<WorldLoad> worldLoads() {
    List<World> worlds = Bukkit.getWorlds();
    List<WorldLoad> loads = new ArrayList<>(worlds.size());
    for (World world : worlds) {
      long entityCount = 0L;
      try {
        entityCount = WorldEntitySnapshots.count(world);
      } catch (Throwable ignored) {
      }
      long chunkCount = 0L;
      try {
        chunkCount = WorldEntitySnapshots.chunkCount(world);
      } catch (Throwable ignored) {
      }
      loads.add(new WorldLoad(world.getUID(), entityCount, chunkCount));
    }
    return loads;
  }

  @Override
  public EntityTracking trackEntities(Sampler owner, AtomicInteger liveCount) {
    return BukkitEntityTracker.start(owner, liveCount);
  }

  @Override
  public void acquireEntityCensus() {
    EntityCensusTracker.acquire();
  }

  @Override
  public void releaseEntityCensus() {
    EntityCensusTracker.release();
  }

  @Override
  public void refreshEntityCensusOnMainThread() {
    EntityCensusTracker.refreshMainThread();
  }

  @Override
  public void refreshEntityCensusConcurrently() {
    EntityCensusTracker.refreshFolia();
  }

  @Override
  public int entityCensus(EntityCategory category) {
    return switch (category) {
      case GROUND_ITEMS -> EntityCensusTracker.groundItems();
      case HOSTILE -> EntityCensusTracker.hostile();
      case ANIMALS -> EntityCensusTracker.animals();
      case VILLAGERS -> EntityCensusTracker.villagers();
      case PROJECTILES -> EntityCensusTracker.projectiles();
      case PHYSICS -> EntityCensusTracker.physics();
      case ACTIVE_AI -> EntityCensusTracker.activeAi();
    };
  }
}
