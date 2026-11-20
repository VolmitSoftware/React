package art.arcane.react.platform.bukkit;

import art.arcane.react.React;
import art.arcane.react.core.controller.EntityController;
import art.arcane.react.core.controller.NearbyPlayerIndexController;
import art.arcane.react.core.controller.NearbyPlayerIndexController.PlayerViewSnapshot;
import art.arcane.react.platform.GameLocation;
import art.arcane.react.platform.PlayerPosition;
import art.arcane.react.platform.ReactPlayer;
import art.arcane.react.platform.ReactWorld;
import art.arcane.react.platform.WorldHost;
import art.arcane.react.util.common.scheduling.J;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class BukkitWorldHost implements WorldHost {
  @Override
  public List<ReactWorld> worlds() {
    List<World> worlds = Bukkit.getWorlds();
    List<ReactWorld> result = new ArrayList<>(worlds.size());
    for (World world : worlds) {
      result.add(new BukkitReactWorld(world));
    }
    return result;
  }

  @Override
  public Optional<ReactWorld> world(UUID worldId) {
    World world = worldId == null ? null : Bukkit.getWorld(worldId);
    return world == null ? Optional.empty() : Optional.of(new BukkitReactWorld(world));
  }

  @Override
  public String worldKey(ReactWorld world) {
    return WorldIdentity.serialize((World) world.handle());
  }

  @Override
  public List<ReactPlayer> onlinePlayers() {
    List<ReactPlayer> result = new ArrayList<>();
    if (J.isFoliaThreading()) {
      EntityController controller = React.controller(EntityController.class);
      Player[] players = controller == null ? null : controller.getFoliaPlayers();
      if (players != null) {
        for (Player player : players) {
          result.add(new BukkitReactPlayer(player));
        }
      }
      return result;
    }
    for (Player player : Bukkit.getOnlinePlayers()) {
      result.add(new BukkitReactPlayer(player));
    }
    return result;
  }

  @Override
  public int onlinePlayerCount() {
    return Bukkit.getOnlinePlayers().size();
  }

  @Override
  public boolean isOnline(ReactPlayer player) {
    return player != null && player.handle() instanceof Player bukkit && bukkit.isOnline();
  }

  @Override
  public Optional<GameLocation> location(ReactPlayer player) {
    if (player == null || !(player.handle() instanceof Player bukkit)) {
      return Optional.empty();
    }
    return Optional.ofNullable(BukkitGameEntity.location(bukkit.getLocation()));
  }

  @Override
  public boolean anyPlayerNear(GameLocation location, double radius) {
    World world = location == null ? null : Bukkit.getWorld(location.worldId());
    return world != null && React.hasNearbyPlayer(new Location(world, location.x(), location.y(), location.z()), radius);
  }

  @Override
  public boolean isPlayerIndexReady() {
    NearbyPlayerIndexController index = React.controller(NearbyPlayerIndexController.class);
    return index != null && index.isInitialSeedReady();
  }

  @Override
  public Optional<List<PlayerPosition>> indexedPlayers() {
    NearbyPlayerIndexController index = React.controller(NearbyPlayerIndexController.class);
    if (index == null || !index.isInitialSeedReady()) {
      return Optional.empty();
    }
    Collection<PlayerViewSnapshot> snapshots = index.playerSnapshots();
    List<PlayerPosition> positions = new ArrayList<>(snapshots.size());
    for (PlayerViewSnapshot snapshot : snapshots) {
      positions.add(position(snapshot));
    }
    return Optional.of(positions);
  }

  @Override
  public Optional<PlayerPosition> indexedPlayer(UUID playerId) {
    NearbyPlayerIndexController index = React.controller(NearbyPlayerIndexController.class);
    return index == null ? Optional.empty() : index.playerSnapshot(playerId).map(BukkitWorldHost::position);
  }

  private static PlayerPosition position(PlayerViewSnapshot snapshot) {
    return new PlayerPosition(snapshot.playerId(), snapshot.worldId(), snapshot.x(), snapshot.y(), snapshot.z());
  }
}
