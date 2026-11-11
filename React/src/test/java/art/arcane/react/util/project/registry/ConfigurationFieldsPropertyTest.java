package art.arcane.react.util.project.registry;

import art.arcane.react.util.project.registry.ConfigurationFieldScenarios.Leaf;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ConfigurationFieldsPropertyTest {
  private static final List<Field> FIELDS = ConfigurationFieldScenarios.hierarchyFields(Leaf.class);

  private static Leaf randomLeaf(long seed, String finalValue) {
    Leaf leaf = new Leaf(finalValue, (int) seed);
    ConfigFieldMutator.mutate(leaf, new Random(seed), true);
    return leaf;
  }

  private static Map<Field, Object> values(Object instance) {
    Map<Field, Object> values = new LinkedHashMap<>();
    for (Field field : FIELDS) {
      values.put(field, ConfigurationFieldScenarios.read(field, instance));
    }
    return values;
  }

  private static void assertSameValue(Field field, Object expected, Object actual) {
    if (field.getType().isPrimitive()) {
      assertEquals(expected, actual, field.getName());
    } else {
      assertSame(expected, actual, field.getName());
    }
  }

  @Property(tries = 200)
  void everyCopyableFieldTakesTheSourceValueUnlessItIsNull(@ForAll long sourceSeed, @ForAll long targetSeed) {
    ConfigurationFieldScenarios.resetStatics();
    Leaf source = randomLeaf(sourceSeed, "source");
    Leaf target = randomLeaf(targetSeed, "target");
    Map<Field, Object> sourceBefore = values(source);
    Map<Field, Object> targetBefore = values(target);

    ConfigurationFields.copy(source, target);

    Map<Field, Object> targetAfter = values(target);
    for (Field field : FIELDS) {
      Object sourceValue = sourceBefore.get(field);
      boolean copied = ConfigurationFieldScenarios.copyable(field) && sourceValue != null;
      assertSameValue(field, copied ? sourceValue : targetBefore.get(field), targetAfter.get(field));
    }
    Map<Field, Object> sourceAfter = values(source);
    for (Field field : FIELDS) {
      assertSameValue(field, sourceBefore.get(field), sourceAfter.get(field));
    }
  }

  @Property(tries = 200)
  void matchesTheLegacyCurseCopyOnTheSameInputs(@ForAll long sourceSeed, @ForAll long targetSeed) {
    ConfigurationFieldScenarios.resetStatics();
    Leaf source = randomLeaf(sourceSeed, "source");
    Leaf target = randomLeaf(targetSeed, "target");
    Leaf legacyTarget = randomLeaf(targetSeed, "target");

    ConfigurationFields.copy(source, target);
    LegacyCurseCopy.copy(source, legacyTarget);

    assertEquals(
        ConfigurationFieldScenarios.render(legacyTarget, new IdentityHashMap<>()),
        ConfigurationFieldScenarios.render(target, new IdentityHashMap<>())
    );
    for (Field field : FIELDS) {
      Object sourceValue = ConfigurationFieldScenarios.read(field, source);
      if (ConfigurationFieldScenarios.copyable(field) && sourceValue != null) {
        assertSameValue(field, ConfigurationFieldScenarios.read(field, legacyTarget), ConfigurationFieldScenarios.read(field, target));
      }
    }
  }

  @Property(tries = 100)
  void copyingAgainChangesNothing(@ForAll long sourceSeed, @ForAll long targetSeed) {
    Leaf source = randomLeaf(sourceSeed, "source");
    Leaf target = randomLeaf(targetSeed, "target");

    ConfigurationFields.copy(source, target);
    Map<Field, Object> afterFirst = values(target);
    ConfigurationFields.copy(source, target);

    Map<Field, Object> afterSecond = values(target);
    for (Field field : FIELDS) {
      assertSameValue(field, afterFirst.get(field), afterSecond.get(field));
    }
  }

  @Property(tries = 100)
  void subclassSourceFillsASuperclassTargetLikeTheLegacyCopy(@ForAll long sourceSeed) {
    Leaf source = randomLeaf(sourceSeed, "source");
    ConfigurationFieldScenarios.Mid target = new ConfigurationFieldScenarios.Mid("target");
    ConfigurationFieldScenarios.Mid legacyTarget = new ConfigurationFieldScenarios.Mid("target");

    ConfigurationFields.copy(source, target);
    LegacyCurseCopy.copy(source, legacyTarget);

    assertEquals(
        ConfigurationFieldScenarios.render(legacyTarget, new IdentityHashMap<>()),
        ConfigurationFieldScenarios.render(target, new IdentityHashMap<>())
    );
    for (Field field : ConfigurationFieldScenarios.hierarchyFields(ConfigurationFieldScenarios.Mid.class)) {
      Object sourceValue = ConfigurationFieldScenarios.read(field, source);
      if (ConfigurationFieldScenarios.copyable(field) && sourceValue != null) {
        assertSameValue(field, sourceValue, ConfigurationFieldScenarios.read(field, target));
      }
    }
  }
}
