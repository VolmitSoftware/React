package art.arcane.react.api.protect.internal;

import art.arcane.react.api.protect.ReactOperations;
import art.arcane.react.api.protect.ReactProtectionRule;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

public final class ProtectionRuleSet {
  private static final ProtectionRuleSet EMPTY = new ProtectionRuleSet(List.of());

  private final List<CompiledRule> rules;
  private final List<CompiledRule> stableRules;
  private final List<CompiledRule> markerRules;
  private final int declaredOperations;
  private final boolean markerSensitive;
  private final boolean tagSensitive;
  private final boolean spawnSensitive;
  private final boolean worldSensitive;
  private final boolean markerRuleTagSensitive;
  private final boolean markerRuleWorldSensitive;

  private ProtectionRuleSet(List<CompiledRule> rules) {
    this.rules = List.copyOf(rules);
    List<CompiledRule> stable = new ArrayList<>();
    List<CompiledRule> markers = new ArrayList<>();
    int declared = ReactOperations.NONE;
    boolean tags = false;
    boolean spawn = false;
    boolean worlds = false;
    boolean markerTags = false;
    boolean markerWorlds = false;

    for (CompiledRule rule : this.rules) {
      declared |= rule.operations();
      tags |= !rule.scoreboardTags().isEmpty();
      spawn |= !rule.spawnReasons().isEmpty();
      worlds |= !rule.worldNames().isEmpty();

      if (rule.markerKeys().isEmpty()) {
        stable.add(rule);
        continue;
      }

      markers.add(rule);
      markerTags |= !rule.scoreboardTags().isEmpty();
      markerWorlds |= !rule.worldNames().isEmpty();
    }

    this.stableRules = List.copyOf(stable);
    this.markerRules = List.copyOf(markers);
    this.declaredOperations = declared;
    this.markerSensitive = !markers.isEmpty();
    this.tagSensitive = tags;
    this.spawnSensitive = spawn;
    this.worldSensitive = worlds;
    this.markerRuleTagSensitive = markerTags;
    this.markerRuleWorldSensitive = markerWorlds;
  }

  public static ProtectionRuleSet empty() {
    return EMPTY;
  }

  public static ProtectionRuleSet compile(Collection<ProtectionDeclaration> declarations) {
    if (declarations == null || declarations.isEmpty()) {
      return EMPTY;
    }

    List<CompiledRule> compiled = new ArrayList<>();
    Set<String> seenRuleIds = new LinkedHashSet<>();

    for (ProtectionDeclaration declaration : declarations) {
      if (declaration == null || declaration.rules().isEmpty()) {
        continue;
      }

      for (ReactProtectionRule rule : declaration.rules()) {
        CompiledRule candidate = compileOne(declaration.providerId(), rule, seenRuleIds);

        if (candidate != null) {
          compiled.add(candidate);
        }
      }
    }

    return compiled.isEmpty() ? EMPTY : new ProtectionRuleSet(compiled);
  }

  private static CompiledRule compileOne(String providerId, ReactProtectionRule rule, Set<String> seenRuleIds) {
    if (rule == null) {
      return null;
    }

    int operations = ReactOperations.sanitize(rule.operations());

    if (operations == ReactOperations.NONE) {
      return null;
    }

    String ruleId = ProtectionText.sanitize(rule.ruleId(), 96);

    if (ruleId.isEmpty()) {
      return null;
    }

    String qualified = ProtectionText.sanitize(providerId, 64) + "/" + ruleId;

    if (!seenRuleIds.add(qualified)) {
      return null;
    }

    Set<NamespacedKey> markerKeys = new LinkedHashSet<>();

    for (NamespacedKey key : rule.markerKeys()) {
      if (key != null) {
        markerKeys.add(key);
      }
    }

    Set<String> scoreboardTags = new LinkedHashSet<>();

    for (String tag : rule.scoreboardTags()) {
      String clean = ProtectionText.sanitize(tag, 64);

      if (!clean.isEmpty()) {
        scoreboardTags.add(clean);
      }
    }

    Set<String> worldNames = new LinkedHashSet<>();

    for (String world : rule.worldNames()) {
      String clean = ProtectionText.sanitize(world, 64).toLowerCase(Locale.ROOT);

      if (!clean.isEmpty()) {
        worldNames.add(clean);
      }
    }

    EnumSet<EntityType> entityTypes = EnumSet.noneOf(EntityType.class);

    for (EntityType type : rule.entityTypes()) {
      if (type != null) {
        entityTypes.add(type);
      }
    }

    EnumSet<CreatureSpawnEvent.SpawnReason> spawnReasons = EnumSet.noneOf(CreatureSpawnEvent.SpawnReason.class);

    for (CreatureSpawnEvent.SpawnReason reason : rule.spawnReasons()) {
      if (reason != null) {
        spawnReasons.add(reason);
      }
    }

    return new CompiledRule(qualified, operations, entityTypes, Set.copyOf(markerKeys),
        Set.copyOf(scoreboardTags), Set.copyOf(worldNames), spawnReasons);
  }

  public boolean isEmpty() {
    return rules.isEmpty();
  }

  public int size() {
    return rules.size();
  }

  public int declaredOperations() {
    return declaredOperations;
  }

  public boolean isMarkerSensitive() {
    return markerSensitive;
  }

  public boolean isTagSensitive() {
    return tagSensitive;
  }

  public boolean isSpawnSensitive() {
    return spawnSensitive;
  }

  public boolean isWorldSensitive() {
    return worldSensitive;
  }

  public boolean isMarkerRuleTagSensitive() {
    return markerRuleTagSensitive;
  }

  public boolean isMarkerRuleWorldSensitive() {
    return markerRuleWorldSensitive;
  }

  public List<String> ruleIds() {
    List<String> out = new ArrayList<>(rules.size());

    for (CompiledRule rule : rules) {
      out.add(rule.ruleId());
    }

    return List.copyOf(out);
  }

  public int entityMask(EntityType type, String worldName, Predicate<NamespacedKey> hasMarker,
                        Predicate<String> hasScoreboardTag) {
    return stableMask(type, worldName, hasScoreboardTag) | markerMask(type, worldName, hasMarker, hasScoreboardTag);
  }

  public int stableMask(EntityType type, String worldName, Predicate<String> hasScoreboardTag) {
    return maskOf(stableRules, type, worldName, null, hasScoreboardTag);
  }

  public int markerMask(EntityType type, String worldName, Predicate<NamespacedKey> hasMarker,
                        Predicate<String> hasScoreboardTag) {
    return maskOf(markerRules, type, worldName, hasMarker, hasScoreboardTag);
  }

  private static int maskOf(List<CompiledRule> subset, EntityType type, String worldName,
                            Predicate<NamespacedKey> hasMarker, Predicate<String> hasScoreboardTag) {
    if (subset.isEmpty()) {
      return ReactOperations.NONE;
    }

    String world = worldName == null ? "" : worldName.toLowerCase(Locale.ROOT);
    int mask = ReactOperations.NONE;

    for (CompiledRule rule : subset) {
      if ((mask & rule.operations()) == rule.operations()) {
        continue;
      }

      if (!rule.matchesType(type) || !rule.matchesWorld(world)) {
        continue;
      }

      if (!rule.markerKeys().isEmpty() && !anyMarker(rule.markerKeys(), hasMarker)) {
        continue;
      }

      if (!rule.scoreboardTags().isEmpty() && !anyTag(rule.scoreboardTags(), hasScoreboardTag)) {
        continue;
      }

      mask |= rule.operations();
    }

    return mask;
  }

  public int spawnMask(EntityType type, String worldName, CreatureSpawnEvent.SpawnReason reason) {
    if (rules.isEmpty()) {
      return ReactOperations.NONE;
    }

    String world = worldName == null ? "" : worldName.toLowerCase(Locale.ROOT);
    int mask = ReactOperations.NONE;

    for (CompiledRule rule : rules) {
      if (!rule.markerKeys().isEmpty() || !rule.scoreboardTags().isEmpty()) {
        continue;
      }

      if (!rule.matchesType(type) || !rule.matchesWorld(world)) {
        continue;
      }

      if (!rule.spawnReasons().isEmpty() && (reason == null || !rule.spawnReasons().contains(reason))) {
        continue;
      }

      mask |= rule.operations();
    }

    return mask;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }

    return other instanceof ProtectionRuleSet set && rules.equals(set.rules);
  }

  @Override
  public int hashCode() {
    return rules.hashCode();
  }

  private static boolean anyMarker(Set<NamespacedKey> keys, Predicate<NamespacedKey> hasMarker) {
    if (hasMarker == null) {
      return false;
    }

    for (NamespacedKey key : keys) {
      if (hasMarker.test(key)) {
        return true;
      }
    }

    return false;
  }

  private static boolean anyTag(Set<String> tags, Predicate<String> hasScoreboardTag) {
    if (hasScoreboardTag == null) {
      return false;
    }

    for (String tag : tags) {
      if (hasScoreboardTag.test(tag)) {
        return true;
      }
    }

    return false;
  }

  private record CompiledRule(String ruleId, int operations, EnumSet<EntityType> entityTypes,
                              Set<NamespacedKey> markerKeys, Set<String> scoreboardTags,
                              Set<String> worldNames,
                              EnumSet<CreatureSpawnEvent.SpawnReason> spawnReasons) {
    private boolean matchesType(EntityType type) {
      return entityTypes.isEmpty() || (type != null && entityTypes.contains(type));
    }

    private boolean matchesWorld(String lowerCaseWorldName) {
      return worldNames.isEmpty() || worldNames.contains(lowerCaseWorldName);
    }
  }
}
