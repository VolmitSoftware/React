package art.arcane.react.api.protect;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.Objects;
import java.util.Set;

public record ReactProtectionRule(String ruleId, int operations, Set<EntityType> entityTypes,
                                  Set<NamespacedKey> markerKeys, Set<String> scoreboardTags,
                                  Set<String> worldNames,
                                  Set<CreatureSpawnEvent.SpawnReason> spawnReasons) {
  public ReactProtectionRule {
    Objects.requireNonNull(ruleId, "ruleId");
    operations = ReactOperations.sanitize(operations);
    entityTypes = entityTypes == null ? Set.of() : Set.copyOf(entityTypes);
    markerKeys = markerKeys == null ? Set.of() : Set.copyOf(markerKeys);
    scoreboardTags = scoreboardTags == null ? Set.of() : Set.copyOf(scoreboardTags);
    worldNames = worldNames == null ? Set.of() : Set.copyOf(worldNames);
    spawnReasons = spawnReasons == null ? Set.of() : Set.copyOf(spawnReasons);
  }

  public static ReactProtectionRule of(String ruleId, int operations) {
    return new ReactProtectionRule(ruleId, operations, Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
  }

  public static ReactProtectionRule of(String ruleId, ReactOperation... operations) {
    return of(ruleId, ReactOperations.of(operations));
  }

  public ReactProtectionRule withEntityTypes(EntityType... types) {
    return new ReactProtectionRule(ruleId, operations, Set.of(types), markerKeys, scoreboardTags,
        worldNames, spawnReasons);
  }

  public ReactProtectionRule withMarkerKeys(NamespacedKey... keys) {
    return new ReactProtectionRule(ruleId, operations, entityTypes, Set.of(keys), scoreboardTags,
        worldNames, spawnReasons);
  }

  public ReactProtectionRule withScoreboardTags(String... tags) {
    return new ReactProtectionRule(ruleId, operations, entityTypes, markerKeys, Set.of(tags),
        worldNames, spawnReasons);
  }

  public ReactProtectionRule withWorlds(String... worlds) {
    return new ReactProtectionRule(ruleId, operations, entityTypes, markerKeys, scoreboardTags,
        Set.of(worlds), spawnReasons);
  }

  public ReactProtectionRule withSpawnReasons(CreatureSpawnEvent.SpawnReason... reasons) {
    return new ReactProtectionRule(ruleId, operations, entityTypes, markerKeys, scoreboardTags,
        worldNames, Set.of(reasons));
  }
}
