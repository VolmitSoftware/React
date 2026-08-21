package art.arcane.react.core.controller;

import art.arcane.react.api.protect.ReactOperation;
import art.arcane.react.api.protect.ReactOperations;
import art.arcane.react.api.protect.ReactProtectionRule;
import art.arcane.react.api.protect.internal.ProtectionDeclaration;
import art.arcane.react.api.protect.internal.ProtectionRuleSet;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

class ProtectionControllerTest {

  private static ProtectionRuleSet builtIns() {
    return ProtectionRuleSet.compile(List.of(
        new ProtectionDeclaration("react", "React", ProtectionController.builtInRules())));
  }

  @Test
  void reactShipsTheLegacyNoStackBridgeRule() {
    List<ReactProtectionRule> rules = ProtectionController.builtInRules();

    Assertions.assertEquals(1, rules.size());
    Assertions.assertEquals(ProtectionController.LEGACY_RULE_ID, rules.getFirst().ruleId());
    Assertions.assertEquals(
        Set.of(ProtectionController.LEGACY_NO_STACK_KEY),
        rules.getFirst().markerKeys());
  }

  @Test
  void theLegacyKeyIsTheExactVolmLibStackExclusionKeySoAdaptKeepsWorking() {
    Assertions.assertEquals(new NamespacedKey("volmit", "no-stack"), ProtectionController.LEGACY_NO_STACK_KEY);
    Assertions.assertEquals("volmit", ProtectionController.LEGACY_NO_STACK_KEY.getNamespace());
    Assertions.assertEquals("no-stack", ProtectionController.LEGACY_NO_STACK_KEY.getKey());
  }

  @Test
  void theLegacyMarkerCoversEveryGateThatConsultedStackExclusionPlusSleep() {
    int mask = builtIns().entityMask(
        EntityType.ZOMBIE, "world", ProtectionController.LEGACY_NO_STACK_KEY::equals, tag -> false);

    Assertions.assertTrue(ReactOperations.covers(mask, ReactOperation.STACK));
    Assertions.assertTrue(ReactOperations.covers(mask, ReactOperation.TRIM));
    Assertions.assertTrue(ReactOperations.covers(mask, ReactOperation.PURGE));
    Assertions.assertTrue(ReactOperations.covers(mask, ReactOperation.DESPAWN));
    Assertions.assertTrue(ReactOperations.covers(mask, ReactOperation.SLEEP));
  }

  @Test
  void theLegacyMarkerDoesNotGrantSpawnCap() {
    int mask = builtIns().entityMask(
        EntityType.ZOMBIE, "world", ProtectionController.LEGACY_NO_STACK_KEY::equals, tag -> false);

    Assertions.assertFalse(ReactOperations.covers(mask, ReactOperation.SPAWN_CAP));
  }

  @Test
  void anUnmarkedEntityGetsNothingFromTheBuiltInRules() {
    Assertions.assertEquals(ReactOperations.NONE,
        builtIns().entityMask(EntityType.ZOMBIE, "world", key -> false, tag -> false));
  }

  @Test
  void theBuiltInRulesNeverGateSpawnsBecauseTheyDependOnEntityState() {
    Assertions.assertEquals(ReactOperations.NONE,
        builtIns().spawnMask(EntityType.ZOMBIE, "world", CreatureSpawnEvent.SpawnReason.CUSTOM));
  }
}
