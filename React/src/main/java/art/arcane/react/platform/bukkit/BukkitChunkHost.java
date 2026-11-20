package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.core.controller.LoadedChunkCoordinate;
import art.arcane.react.core.controller.NearbyPlayerIndexController;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.model.SampledChunk;
import art.arcane.react.model.SampledWorld;
import art.arcane.react.platform.ChunkHost;
import art.arcane.react.platform.ChunkRef;
import art.arcane.react.platform.ListenerPriority;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.Subscription;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import art.arcane.volmlib.util.scheduling.WorldChunks;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class BukkitChunkHost implements ChunkHost {
  @Override
  public Optional<ReactWorld> world(String worldKey) {
    if (worldKey == null || worldKey.isBlank()) {
      return Optional.empty();
    }
    return WorldIdentity.resolve(worldKey).map(BukkitReactWorld::new);
  }

  @Override
  public Optional<ReactWorld> world(UUID worldId) {
    World world = worldId == null ? null : Bukkit.getWorld(worldId);
    return world == null ? Optional.empty() : Optional.of(new BukkitReactWorld(world));
  }

  @Override
  public String worldKey(ReactWorld world) {
    return WorldIdentity.serialize(bukkit(world));
  }

  @Override
  public boolean isLoaded(ReactWorld world, int chunkX, int chunkZ) {
    return bukkit(world).isChunkLoaded(chunkX, chunkZ);
  }

  @Override
  public boolean isGenerated(ReactWorld world, int chunkX, int chunkZ) {
    return bukkit(world).isChunkGenerated(chunkX, chunkZ);
  }

  @Override
  public boolean unload(ReactWorld world, int chunkX, int chunkZ) {
    return bukkit(world).getChunkAt(chunkX, chunkZ).unload(true);
  }

  @Override
  public Object loadedChunk(ReactWorld world, int chunkX, int chunkZ) {
    World bukkit = bukkit(world);
    return bukkit.isChunkLoaded(chunkX, chunkZ) ? bukkit.getChunkAt(chunkX, chunkZ) : null;
  }

  @Override
  @SuppressWarnings("unchecked")
  public CompletableFuture<Object> load(ReactWorld world, int chunkX, int chunkZ, boolean generate) {
    CompletableFuture<?> loading = WorldChunks.load(React.instance, bukkit(world), chunkX, chunkZ, generate);
    return (CompletableFuture<Object>) loading;
  }

  @Override
  public void warm(Object chunk, boolean touchSnapshot) {
    Chunk bukkit = (Chunk) chunk;
    if (touchSnapshot) {
      bukkit.getChunkSnapshot(false, false, false);
    }
    bukkit.getEntities();
  }

  @Override
  public boolean ownsChunk(ReactWorld world, int chunkX, int chunkZ) {
    return J.isOwnedByCurrentRegion(new Location(bukkit(world), (chunkX << 4) + 8D, 64D, (chunkZ << 4) + 8D));
  }

  @Override
  public boolean isLoadedIndexReady() {
    ObserverController observer = observer();
    return observer == null || observer.isLoadedChunkCoordinateIndexReady();
  }

  @Override
  public LoadedChunkCursor openLoadedCursor() {
    ObserverController observer = observer();
    return observer == null ? LoadedChunkCursor.EMPTY : wrap(observer.openLoadedChunkCursor());
  }

  @Override
  public LoadedChunkCursor openLoadedCursor(UUID worldId) {
    ObserverController observer = observer();
    return observer == null ? LoadedChunkCursor.EMPTY : wrap(observer.openLoadedChunkCursor(worldId));
  }

  @Override
  public List<LoadedChunkCoordinate> loadedInRadius(UUID worldId, int centerX, int centerZ, int radius) {
    ObserverController observer = observer();
    return observer == null ? List.of() : observer.loadedChunkCoordinatesInRadius(worldId, centerX, centerZ, radius);
  }

  @Override
  public List<SampledChunk> sampledChunks() {
    ObserverController observer = React.controller(ObserverController.class);
    if (observer == null || observer.getSampled() == null) {
      return List.of();
    }
    List<SampledChunk> chunks = new ArrayList<>();
    for (SampledWorld world : observer.getSampled().getWorlds().values()) {
      if (world.getWorldKey() != null) {
        chunks.addAll(world.getChunks().values());
      }
    }
    return chunks;
  }

  @Override
  public boolean mayHavePlayerNear(ReactWorld world, double x, double z, double radius) {
    NearbyPlayerIndexController playerIndex = React.controller(NearbyPlayerIndexController.class);
    return playerIndex == null
        || !playerIndex.isInitialSeedReady()
        || playerIndex.hasNearbyPlayerInColumn(bukkit(world), x, z, radius);
  }

  private static LoadedChunkCursor wrap(ObserverController.LoadedChunkCursor cursor) {
    return new LoadedChunkCursor() {
      @Override
      public List<ChunkRef> next(int maximum) {
        List<ObserverController.LoadedChunkTarget> targets = cursor.next(maximum);
        List<ChunkRef> refs = new ArrayList<>(targets.size());
        for (ObserverController.LoadedChunkTarget target : targets) {
          refs.add(new ChunkRef(target.worldId(), target.chunkX(), target.chunkZ()));
        }
        return refs;
      }

      @Override
      public int estimatedTotal() {
        return cursor.estimatedTotal();
      }
    };
  }

  private static ObserverController observer() {
    return React.instance == null ? null : React.controller(ObserverController.class);
  }

  private static World bukkit(ReactWorld world) {
    return (World) world.handle();
  }

  @Override
  public boolean isLoaded(UUID worldId, int chunkX, int chunkZ) {
    World world = worldId == null ? null : Bukkit.getWorld(worldId);
    return world != null && world.isChunkLoaded(chunkX, chunkZ);
  }

  @Override
  public List<ChunkRef> nextLoaded(int maximum) {
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
  public Subscription onChunkLoad(ListenerPriority priority, Consumer<ChunkRef> listener) {
    return BukkitEvents.subscribe(ChunkLoadEvent.class, priority, false, event -> listener.accept(ref(event.getChunk())));
  }

  @Override
  public Subscription onChunkUnload(ListenerPriority priority, Consumer<ChunkRef> listener) {
    return BukkitEvents.subscribe(ChunkUnloadEvent.class, priority, false, event -> listener.accept(ref(event.getChunk())));
  }

  @Override
  public Subscription onWorldUnload(ListenerPriority priority, boolean ignoreCancelled, Consumer<WorldUnload> listener) {
    return BukkitEvents.subscribe(WorldUnloadEvent.class, priority, ignoreCancelled, event -> listener.accept(new BukkitWorldUnload(event)));
  }

  private static ChunkRef ref(Chunk chunk) {
    return new ChunkRef(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
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
