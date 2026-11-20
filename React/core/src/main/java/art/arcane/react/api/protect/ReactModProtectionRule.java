package art.arcane.react.api.protect;

import java.util.Objects;
import java.util.Set;

/**
 * A standing protection rule from a mod, the counterpart of a Bukkit plugin's {@code ReactProtectionRule}. Entity
 * types are registry ids such as {@code minecraft:villager}; marker keys and scoreboard tags both match entity tags;
 * spawn reasons are the platform's spawn reason names such as {@code SPAWNER}. Empty sets match everything.
 */
public record ReactModProtectionRule(String ruleId, int operations, Set<String> entityTypes, Set<String> markerKeys,
                                     Set<String> scoreboardTags, Set<String> worldNames, Set<String> spawnReasons) {
  public ReactModProtectionRule {
    Objects.requireNonNull(ruleId, "ruleId");
    operations = ReactOperations.sanitize(operations);
    entityTypes = entityTypes == null ? Set.of() : Set.copyOf(entityTypes);
    markerKeys = markerKeys == null ? Set.of() : Set.copyOf(markerKeys);
    scoreboardTags = scoreboardTags == null ? Set.of() : Set.copyOf(scoreboardTags);
    worldNames = worldNames == null ? Set.of() : Set.copyOf(worldNames);
    spawnReasons = spawnReasons == null ? Set.of() : Set.copyOf(spawnReasons);
  }

  public static ReactModProtectionRule of(String ruleId, int operations) {
    return new ReactModProtectionRule(ruleId, operations, Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
  }

  public static ReactModProtectionRule of(String ruleId, ReactOperation... operations) {
    return of(ruleId, ReactOperations.of(operations));
  }

  public ReactModProtectionRule withEntityTypes(String... types) {
    return new ReactModProtectionRule(ruleId, operations, Set.of(types), markerKeys, scoreboardTags, worldNames,
        spawnReasons);
  }

  public ReactModProtectionRule withMarkerKeys(String... keys) {
    return new ReactModProtectionRule(ruleId, operations, entityTypes, Set.of(keys), scoreboardTags, worldNames,
        spawnReasons);
  }

  public ReactModProtectionRule withScoreboardTags(String... tags) {
    return new ReactModProtectionRule(ruleId, operations, entityTypes, markerKeys, Set.of(tags), worldNames,
        spawnReasons);
  }

  public ReactModProtectionRule withWorlds(String... worlds) {
    return new ReactModProtectionRule(ruleId, operations, entityTypes, markerKeys, scoreboardTags, Set.of(worlds),
        spawnReasons);
  }

  public ReactModProtectionRule withSpawnReasons(String... reasons) {
    return new ReactModProtectionRule(ruleId, operations, entityTypes, markerKeys, scoreboardTags, worldNames,
        Set.of(reasons));
  }
}
