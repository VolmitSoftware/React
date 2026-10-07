package art.arcane.react.util.project.world;

import art.arcane.react.util.common.scheduling.J;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class NearbyEntitySampler {
  private final Map<UUID, Neighborhood> neighborhoods = new ConcurrentHashMap<>();

  public List<Entity> sample(Player player, Request request) {
    return sample(player, request, null);
  }

  public List<Entity> sample(Player player, Request request, Predicate<Entity> accept) {
    if (request.maximum() <= 0 || !J.isOwnedByCurrentRegion(player)) {
      return List.of();
    }
    UUID playerId = player.getUniqueId();
    UUID worldId = player.getWorld().getUID();
    int tick = player.getTicksLived();
    BoundingBox bounds = player.getBoundingBox().expand(
        request.horizontalRadius(), request.verticalRadius(), request.horizontalRadius()
    );
    Neighborhood neighborhood = neighborhoods.computeIfAbsent(playerId, ignored -> new Neighborhood());
    boolean sameBounds = bounds.equals(neighborhood.bounds);
    if (neighborhood.tick != tick
        || !worldId.equals(neighborhood.worldId)
        || neighborhood.bounds == null
        || !neighborhood.bounds.contains(bounds)
        || (accept != null && !sameBounds)) {
      neighborhood.entities = player.getNearbyEntities(
          request.horizontalRadius(), request.verticalRadius(), request.horizontalRadius()
      );
      neighborhood.bounds = bounds;
      neighborhood.worldId = worldId;
      neighborhood.tick = tick;
      sameBounds = true;
    }
    List<Entity> entities = neighborhood.entities;
    if (entities.isEmpty()) {
      return List.of();
    }
    int start = Math.floorMod(neighborhood.cursors.getOrDefault(request.consumer(), 0), entities.size());
    int limit = Math.min(request.maximum(), entities.size());
    List<Entity> samples = new ArrayList<>(limit);
    int inspected = 0;
    int inspectionLimit = (int) Math.min(entities.size(), Math.max(64L, (long) limit * 8L));
    for (; inspected < inspectionLimit && samples.size() < limit; inspected++) {
      Entity entity = entities.get((start + inspected) % entities.size());
      if (!sameBounds && !J.isOwnedByCurrentRegion(entity)) {
        neighborhood.bounds = null;
        return sample(player, request, accept);
      }
      if ((sameBounds || bounds.overlaps(entity.getBoundingBox())) && (accept == null || accept.test(entity))) {
        samples.add(entity);
      }
    }
    neighborhood.cursors.put(request.consumer(), (start + inspected) % entities.size());
    return samples;
  }

  public void forget(UUID playerId) {
    neighborhoods.remove(playerId);
  }

  public void clear() {
    neighborhoods.clear();
  }

  public record Request(String consumer, double horizontalRadius, double verticalRadius, int maximum) {
  }

  private static final class Neighborhood {
    private final Map<String, Integer> cursors = new HashMap<>();
    private UUID worldId;
    private int tick = Integer.MIN_VALUE;
    private BoundingBox bounds;
    private List<Entity> entities = List.of();
  }
}
