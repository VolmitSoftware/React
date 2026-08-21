package art.arcane.react.api.protect.internal;

import art.arcane.react.api.protect.ReactOperation;
import art.arcane.react.api.protect.ReactOperations;
import art.arcane.react.api.protect.ReactProtectionRule;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

class ProtectionRuleSetTest {
  private static final NamespacedKey PET = new NamespacedKey("example", "pet");
  private static final NamespacedKey OTHER = new NamespacedKey("example", "other");
  private static final Predicate<NamespacedKey> NO_MARKER = key -> false;
  private static final Predicate<String> NO_TAG = tag -> false;

  private static ProtectionRuleSet compile(ReactProtectionRule... rules) {
    return ProtectionRuleSet.compile(List.of(new ProtectionDeclaration("example", "Example", List.of(rules))));
  }

  @Test
  void emptyRuleSetProtectsNothing() {
    ProtectionRuleSet set = ProtectionRuleSet.compile(List.of());
    Assertions.assertTrue(set.isEmpty());
    Assertions.assertEquals(ReactOperations.NONE,
        set.entityMask(EntityType.WOLF, "world", key -> true, tag -> true));
  }

  @Test
  void unconstrainedRuleMatchesEveryEntity() {
    ProtectionRuleSet set = compile(ReactProtectionRule.of("all", ReactOperation.TRIM));
    Assertions.assertTrue(ReactOperations.covers(
        set.entityMask(EntityType.ZOMBIE, "nether", NO_MARKER, NO_TAG), ReactOperation.TRIM));
  }

  @Test
  void entityTypeFilterExcludesOtherTypes() {
    ProtectionRuleSet set = compile(
        ReactProtectionRule.of("wolves", ReactOperation.STACK).withEntityTypes(EntityType.WOLF));
    Assertions.assertTrue(ReactOperations.covers(
        set.entityMask(EntityType.WOLF, "world", NO_MARKER, NO_TAG), ReactOperation.STACK));
    Assertions.assertFalse(ReactOperations.covers(
        set.entityMask(EntityType.ZOMBIE, "world", NO_MARKER, NO_TAG), ReactOperation.STACK));
  }

  @Test
  void worldFilterIsCaseInsensitiveAndExcludesOtherWorlds() {
    ProtectionRuleSet set = compile(
        ReactProtectionRule.of("arena", ReactOperation.PURGE).withWorlds("Arena"));
    Assertions.assertTrue(ReactOperations.covers(
        set.entityMask(EntityType.ZOMBIE, "arena", NO_MARKER, NO_TAG), ReactOperation.PURGE));
    Assertions.assertTrue(ReactOperations.covers(
        set.entityMask(EntityType.ZOMBIE, "ARENA", NO_MARKER, NO_TAG), ReactOperation.PURGE));
    Assertions.assertFalse(ReactOperations.covers(
        set.entityMask(EntityType.ZOMBIE, "world", NO_MARKER, NO_TAG), ReactOperation.PURGE));
  }

  @Test
  void markerKeysMatchWhenAnyKeyIsPresent() {
    ProtectionRuleSet set = compile(
        ReactProtectionRule.of("pets", ReactOperation.SLEEP).withMarkerKeys(PET, OTHER));
    Assertions.assertTrue(set.isMarkerSensitive());
    Assertions.assertTrue(ReactOperations.covers(
        set.entityMask(EntityType.WOLF, "world", key -> OTHER.equals(key), NO_TAG), ReactOperation.SLEEP));
    Assertions.assertFalse(ReactOperations.covers(
        set.entityMask(EntityType.WOLF, "world", NO_MARKER, NO_TAG), ReactOperation.SLEEP));
  }

  @Test
  void scoreboardTagsMatchWhenAnyTagIsPresent() {
    ProtectionRuleSet set = compile(
        ReactProtectionRule.of("tagged", ReactOperation.DESPAWN).withScoreboardTags("quest", "boss"));
    Assertions.assertTrue(set.isTagSensitive());
    Assertions.assertTrue(ReactOperations.covers(
        set.entityMask(EntityType.ZOMBIE, "world", NO_MARKER, "boss"::equals), ReactOperation.DESPAWN));
    Assertions.assertFalse(ReactOperations.covers(
        set.entityMask(EntityType.ZOMBIE, "world", NO_MARKER, "loot"::equals), ReactOperation.DESPAWN));
  }

  @Test
  void facetsAreAndedTogether() {
    ProtectionRuleSet set = compile(ReactProtectionRule.of("narrow", ReactOperation.TRIM)
        .withEntityTypes(EntityType.WOLF)
        .withWorlds("world")
        .withMarkerKeys(PET));

    Assertions.assertTrue(ReactOperations.covers(
        set.entityMask(EntityType.WOLF, "world", PET::equals, NO_TAG), ReactOperation.TRIM));
    Assertions.assertFalse(ReactOperations.covers(
        set.entityMask(EntityType.WOLF, "nether", PET::equals, NO_TAG), ReactOperation.TRIM));
    Assertions.assertFalse(ReactOperations.covers(
        set.entityMask(EntityType.CAT, "world", PET::equals, NO_TAG), ReactOperation.TRIM));
    Assertions.assertFalse(ReactOperations.covers(
        set.entityMask(EntityType.WOLF, "world", NO_MARKER, NO_TAG), ReactOperation.TRIM));
  }

  @Test
  void operationsFromSeveralMatchingRulesAreUnioned() {
    ProtectionRuleSet set = compile(
        ReactProtectionRule.of("a", ReactOperation.STACK),
        ReactProtectionRule.of("b", ReactOperation.SLEEP));
    int mask = set.entityMask(EntityType.WOLF, "world", NO_MARKER, NO_TAG);
    Assertions.assertEquals(ReactOperations.of(ReactOperation.STACK, ReactOperation.SLEEP), mask);
  }

  @Test
  void spawnMaskHonoursSpawnReasons() {
    ProtectionRuleSet set = compile(ReactProtectionRule.of("summons", ReactOperation.SPAWN_CAP)
        .withEntityTypes(EntityType.WOLF)
        .withSpawnReasons(CreatureSpawnEvent.SpawnReason.CUSTOM));

    Assertions.assertTrue(set.isSpawnSensitive());
    Assertions.assertTrue(ReactOperations.covers(
        set.spawnMask(EntityType.WOLF, "world", CreatureSpawnEvent.SpawnReason.CUSTOM),
        ReactOperation.SPAWN_CAP));
    Assertions.assertFalse(ReactOperations.covers(
        set.spawnMask(EntityType.WOLF, "world", CreatureSpawnEvent.SpawnReason.NATURAL),
        ReactOperation.SPAWN_CAP));
    Assertions.assertFalse(ReactOperations.covers(
        set.spawnMask(EntityType.WOLF, "world", null),
        ReactOperation.SPAWN_CAP));
  }

  @Test
  void spawnMaskAppliesReasonFreeRulesWithoutAReason() {
    ProtectionRuleSet set = compile(ReactProtectionRule.of("summons", ReactOperation.SPAWN_CAP)
        .withEntityTypes(EntityType.ARMOR_STAND));
    Assertions.assertTrue(ReactOperations.covers(
        set.spawnMask(EntityType.ARMOR_STAND, "world", null), ReactOperation.SPAWN_CAP));
  }

  @Test
  void spawnMaskIgnoresRulesThatDependOnEntityState() {
    ProtectionRuleSet set = compile(ReactProtectionRule.of("marked", ReactOperation.SPAWN_CAP)
        .withMarkerKeys(PET));
    Assertions.assertEquals(ReactOperations.NONE,
        set.spawnMask(EntityType.WOLF, "world", CreatureSpawnEvent.SpawnReason.CUSTOM));
  }

  @Test
  void entityMaskIgnoresSpawnReasonsBecauseTheyAreUnknowableAfterSpawn() {
    ProtectionRuleSet set = compile(ReactProtectionRule.of("summons", ReactOperation.SPAWN_CAP)
        .withSpawnReasons(CreatureSpawnEvent.SpawnReason.CUSTOM));
    Assertions.assertTrue(ReactOperations.covers(
        set.entityMask(EntityType.WOLF, "world", NO_MARKER, NO_TAG), ReactOperation.SPAWN_CAP));
  }

  @Test
  void rulesWithNoOperationsAreDropped() {
    ProtectionRuleSet set = compile(ReactProtectionRule.of("empty", ReactOperations.NONE));
    Assertions.assertTrue(set.isEmpty());
  }

  @Test
  void rulesWithBlankIdsAreDropped() {
    ProtectionRuleSet set = compile(ReactProtectionRule.of("   ", ReactOperation.TRIM));
    Assertions.assertTrue(set.isEmpty());
  }

  @Test
  void duplicateRuleIdsFromOneProviderAreDeduplicated() {
    ProtectionRuleSet set = compile(
        ReactProtectionRule.of("same", ReactOperation.TRIM),
        ReactProtectionRule.of("same", ReactOperation.PURGE));
    Assertions.assertEquals(1, set.size());
    Assertions.assertFalse(ReactOperations.covers(
        set.entityMask(EntityType.ZOMBIE, "world", NO_MARKER, NO_TAG), ReactOperation.PURGE));
  }

  @Test
  void sameRuleIdFromDifferentProvidersIsKept() {
    ProtectionRuleSet set = ProtectionRuleSet.compile(List.of(
        new ProtectionDeclaration("a", "A", List.of(ReactProtectionRule.of("same", ReactOperation.TRIM))),
        new ProtectionDeclaration("b", "B", List.of(ReactProtectionRule.of("same", ReactOperation.PURGE)))));
    Assertions.assertEquals(2, set.size());
  }

  @Test
  void ruleIdsAreStrippedOfControlCharacters() {
    ProtectionRuleSet set = compile(ReactProtectionRule.of("bad\u0007id\n", ReactOperation.TRIM));
    Assertions.assertEquals(List.of("example/badid"), set.ruleIds());
  }

  @Test
  void identicalDeclarationsCompileToEqualRuleSets() {
    ProtectionRuleSet first = compile(ReactProtectionRule.of("pets", ReactOperation.STACK).withMarkerKeys(PET));
    ProtectionRuleSet second = compile(ReactProtectionRule.of("pets", ReactOperation.STACK).withMarkerKeys(PET));
    Assertions.assertEquals(first, second);
  }

  @Test
  void differentDeclarationsCompileToDifferentRuleSets() {
    ProtectionRuleSet first = compile(ReactProtectionRule.of("pets", ReactOperation.STACK));
    ProtectionRuleSet second = compile(ReactProtectionRule.of("pets", ReactOperation.TRIM));
    Assertions.assertNotEquals(first, second);
  }

  @Test
  void declaredOperationsIsTheUnionAcrossRules() {
    ProtectionRuleSet set = compile(
        ReactProtectionRule.of("a", ReactOperation.STACK),
        ReactProtectionRule.of("b", ReactOperation.SPAWN_CAP));
    Assertions.assertEquals(
        ReactOperations.of(ReactOperation.STACK, ReactOperation.SPAWN_CAP),
        set.declaredOperations());
  }

  @Test
  void nullSetsInARuleAreTreatedAsUnconstrained() {
    ReactProtectionRule rule = new ReactProtectionRule("lenient",
        ReactOperations.of(ReactOperation.TRIM), null, null, null, null, null);
    Assertions.assertEquals(Set.of(), rule.entityTypes());
    ProtectionRuleSet set = compile(rule);
    Assertions.assertTrue(ReactOperations.covers(
        set.entityMask(EntityType.BAT, "anything", NO_MARKER, NO_TAG), ReactOperation.TRIM));
  }
}
