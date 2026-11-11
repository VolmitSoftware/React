package art.arcane.react.util.project.registry;

import art.arcane.react.util.project.registry.ConfigurationFieldScenarios.GenericConfig;
import art.arcane.react.util.project.registry.ConfigurationFieldScenarios.Leaf;
import art.arcane.react.util.project.registry.ConfigurationFieldScenarios.Mid;
import art.arcane.react.util.project.registry.ConfigurationFieldScenarios.Outcome;
import art.arcane.react.util.project.registry.ConfigurationFieldScenarios.Outer;
import art.arcane.react.util.project.registry.ConfigurationFieldScenarios.RecordConfig;
import art.arcane.react.util.project.registry.ConfigurationFieldScenarios.Scenario;
import art.arcane.react.util.project.registry.ConfigurationFieldScenarios.ShadowIncompatible;
import art.arcane.react.util.project.registry.ConfigurationFieldScenarios.ShadowIncompatibleBase;
import art.arcane.react.util.project.registry.ConfigurationFieldScenarios.ShadowSameType;
import art.arcane.react.util.project.registry.ConfigurationFieldScenarios.SiblingA;
import art.arcane.react.util.project.registry.ConfigurationFieldScenarios.SiblingTwin;
import art.arcane.react.util.project.registry.ConfigurationFieldScenarios.StaticShadowBase;
import art.arcane.react.util.project.registry.ConfigurationFieldScenarios.StaticShadowSub;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationFieldsTest {
  static class BaseConfig {
    private int baseLimit = 1;
    private String baseName = "base-default";
  }

  static class FeatureConfig extends BaseConfig {
    static final String CONSTANT = "constant";
    static String sharedValue = "static-default";
    private final String fixedValue;
    private transient String runtimeValue = "runtime-default";
    private boolean enabled = false;
    private double radius = 2.5;
    private List<String> worlds = List.of("default");
    private String optional = "kept";

    FeatureConfig(String fixedValue) {
      this.fixedValue = fixedValue;
    }
  }

  @Test
  void copiesNonNullInstanceFieldsIncludingSuperclassFields() {
    FeatureConfig loaded = new FeatureConfig("loaded-fixed");
    loaded.enabled = true;
    loaded.radius = 9.0;
    loaded.worlds = List.of("world", "world_nether");
    loaded.optional = null;
    ((BaseConfig) loaded).baseLimit = 42;
    ((BaseConfig) loaded).baseName = "loaded-base";
    FeatureConfig current = new FeatureConfig("current-fixed");

    ConfigurationFields.copy(loaded, current);

    assertEquals(true, current.enabled);
    assertEquals(9.0, current.radius);
    assertEquals(List.of("world", "world_nether"), current.worlds);
    assertEquals("kept", current.optional);
    assertEquals(42, ((BaseConfig) current).baseLimit);
    assertEquals("loaded-base", ((BaseConfig) current).baseName);
  }

  @Test
  void skipsStaticFinalAndTransientFields() {
    FeatureConfig loaded = new FeatureConfig("loaded-fixed");
    loaded.runtimeValue = "loaded-runtime";
    FeatureConfig current = new FeatureConfig("current-fixed");
    current.runtimeValue = null;
    FeatureConfig.sharedValue = "static-before";

    ConfigurationFields.copy(loaded, current);

    assertEquals("current-fixed", current.fixedValue);
    assertNull(current.runtimeValue);
    assertEquals("static-before", FeatureConfig.sharedValue);
  }

  /**
   * Scenarios where plain reflection behaves differently from Curse 23.4.3. Each needs a source
   * that is not the target's own class, or a field name declared twice in one hierarchy. Neither
   * happens through Registered: snapshots are parsed as the live object's class, and
   * RegisteredConfigSweepTest proves no shipped config class repeats a field name.
   */
  private static final Set<String> KNOWN_DIVERGENCES = Set.of(
      "siblingSourceWithDifferentFieldNames",
      "siblingSourceWithMatchingFieldNames",
      "unrelatedSourceWithSomeMatchingFieldNames",
      "shadowedFieldSameType",
      "shadowedFieldWideningType",
      "shadowedFieldIncompatibleType",
      "staticFieldShadowsInstanceFieldSameClass",
      "staticFieldShadowsInstanceFieldSubclassSource",
      "nullSourceWithNoCopyableFieldsOnTarget"
  );

  static Stream<String> scenariosMatchingCurse() {
    return ConfigurationFieldScenarios.all().stream()
        .map(Scenario::name)
        .filter(name -> !KNOWN_DIVERGENCES.contains(name));
  }

  static Stream<String> everyScenario() {
    return ConfigurationFieldScenarios.all().stream().map(Scenario::name);
  }

  private static Object field(Object instance, String name) {
    Field field = ConfigurationFieldScenarios.findByName(instance.getClass(), name);
    return ConfigurationFieldScenarios.read(field, instance);
  }

  private static Object field(Object instance, Class<?> declaringType, String name) {
    try {
      Field field = declaringType.getDeclaredField(name);
      field.setAccessible(true);
      return field.get(instance);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  @ParameterizedTest
  @MethodSource("scenariosMatchingCurse")
  void leavesTheSameFieldValuesAsCurse(String name) {
    Outcome recorded = CurseRecordedBehavior.outcomes().get(name);
    Outcome actual = ConfigurationFieldScenarios.run(ConfigurationFieldScenarios.byName(name), ConfigurationFields::copy);

    assertEquals(recorded.linesWithoutException(), actual.linesWithoutException());
  }

  @ParameterizedTest
  @MethodSource("everyScenario")
  void legacyReferenceCopyReproducesTheCurseRecording(String name) {
    Outcome recorded = CurseRecordedBehavior.outcomes().get(name);
    Outcome actual = ConfigurationFieldScenarios.run(ConfigurationFieldScenarios.byName(name), LegacyCurseCopy::copy);

    assertEquals(recorded.lines(), actual.lines());
  }

  @Test
  void recordingCoversEveryScenario() {
    assertEquals(
        ConfigurationFieldScenarios.all().stream().map(Scenario::name).toList(),
        new ArrayList<>(CurseRecordedBehavior.outcomes().keySet())
    );
  }

  @Test
  void differsFromCurseOnlyInTheKnownScenarios() {
    Map<String, Outcome> recorded = CurseRecordedBehavior.outcomes();
    Set<String> differing = new TreeSet<>();
    for (Scenario scenario : ConfigurationFieldScenarios.all()) {
      Outcome actual = ConfigurationFieldScenarios.run(scenario, ConfigurationFields::copy);
      if (!recorded.get(scenario.name()).linesWithoutException().equals(actual.linesWithoutException())) {
        differing.add(scenario.name());
      }
    }

    assertEquals(new TreeSet<>(KNOWN_DIVERGENCES), differing);
  }

  @Test
  void copiesFieldsOfEveryVisibilityFromThreeClassLevels() {
    Leaf loaded = ConfigurationFieldScenarios.mutatedLeaf();
    Leaf current = ConfigurationFieldScenarios.defaultLeaf();

    ConfigurationFields.copy(loaded, current);

    assertEquals(2, field(current, "basePrivate"));
    assertEquals("base-protected-loaded", field(current, "baseProtected"));
    assertEquals("base-package-loaded", field(current, "basePackage"));
    assertEquals(20L, field(current, "basePublic"));
    assertEquals(0.75, field(current, "midRatio"));
    assertEquals(701, field(current, "midBoxed"));
    assertEquals("leaf-loaded", field(current, "text"));
  }

  @Test
  void copiesEveryPrimitiveAndBoxedType() {
    Leaf loaded = ConfigurationFieldScenarios.mutatedLeaf();
    Leaf current = ConfigurationFieldScenarios.defaultLeaf();

    ConfigurationFields.copy(loaded, current);

    assertEquals(true, field(current, "flag"));
    assertEquals((byte) 21, field(current, "tiny"));
    assertEquals((short) 22, field(current, "small"));
    assertEquals('z', field(current, "letter"));
    assertEquals(23, field(current, "count"));
    assertEquals(24L, field(current, "big"));
    assertEquals(21.5F, field(current, "ratio"));
    assertEquals(22.5, field(current, "precise"));
    assertEquals(25, field(current, "volatileCount"));
    for (String boxed : List.of("boxedFlag", "boxedTiny", "boxedSmall", "boxedLetter", "boxedCount", "boxedBig", "boxedRatio", "boxedPrecise")) {
      assertSame(field(loaded, boxed), field(current, boxed), boxed);
    }
  }

  @Test
  void sharesCollectionArrayAndNestedObjectReferencesWithTheSource() {
    Leaf loaded = ConfigurationFieldScenarios.mutatedLeaf();
    Leaf current = ConfigurationFieldScenarios.defaultLeaf();

    ConfigurationFields.copy(loaded, current);

    for (String shared : List.of("list", "set", "map", "nestedGeneric", "numbers", "names", "grid", "nested", "box", "point", "mode", "any", "midWorlds")) {
      assertSame(field(loaded, shared), field(current, shared), shared);
    }
    @SuppressWarnings("unchecked")
    List<String> loadedList = (List<String>) field(loaded, "list");
    loadedList.add("added-after-copy");
    assertEquals(List.of("x", "y", "added-after-copy"), field(current, "list"));
  }

  @Test
  void nullSourceValueKeepsTheTargetValueAndNullTargetValueIsFilled() {
    Leaf loadedWithNulls = ConfigurationFieldScenarios.leafWithNullReferences();
    Leaf current = ConfigurationFieldScenarios.defaultLeaf();
    Object listBefore = field(current, "list");

    ConfigurationFields.copy(loadedWithNulls, current);

    assertSame(listBefore, field(current, "list"));
    assertEquals("leaf", field(current, "text"));
    assertEquals(300, field(current, "boxedCount"));
    assertEquals(23, field(current, "count"));

    Leaf loaded = ConfigurationFieldScenarios.mutatedLeaf();
    Leaf currentWithNulls = ConfigurationFieldScenarios.leafWithNullReferences();
    ConfigurationFields.copy(loaded, currentWithNulls);
    assertSame(field(loaded, "list"), field(currentWithNulls, "list"));
    assertEquals("leaf-loaded", field(currentWithNulls, "text"));
  }

  @Test
  void leavesStaticFinalAndTransientFieldsOfEveryLevelAlone() {
    Leaf loaded = ConfigurationFieldScenarios.mutatedLeaf();
    Leaf current = ConfigurationFieldScenarios.defaultLeaf();
    Object finalListBefore = field(current, "leafFinalList");
    Object transientListBefore = field(current, "leafTransientList");

    ConfigurationFields.copy(loaded, current);

    assertEquals(11, field(current, "leafFinal"));
    assertSame(finalListBefore, field(current, "leafFinalList"));
    assertEquals(List.of("final"), field(current, "leafFinalList"));
    assertEquals(12, field(current, "leafTransient"));
    assertSame(transientListBefore, field(current, "leafTransientList"));
    assertEquals("target-final", field(current, "baseFinal"));
    assertEquals("base-transient", field(current, "baseTransient"));
    assertEquals(9, Leaf.leafStatic);
    assertEquals("leaf-static-transient", Leaf.leafStaticTransient);
    assertEquals("base-static", ConfigurationFieldScenarios.Base.baseStatic);
  }

  @Test
  void neverChangesTheSource() {
    Leaf loaded = ConfigurationFieldScenarios.mutatedLeaf();
    String before = ConfigurationFieldScenarios.render(loaded, new java.util.IdentityHashMap<>());

    ConfigurationFields.copy(loaded, ConfigurationFieldScenarios.defaultLeaf());

    assertEquals(before, ConfigurationFieldScenarios.render(loaded, new java.util.IdentityHashMap<>()));
  }

  @Test
  void copiesSuperclassFieldsFromASubclassSource() {
    Leaf loaded = ConfigurationFieldScenarios.mutatedLeaf();
    Mid current = new Mid("current-final");

    ConfigurationFields.copy(loaded, current);

    assertEquals(0.75, field(current, "midRatio"));
    assertSame(field(loaded, "midWorlds"), field(current, "midWorlds"));
    assertEquals(20L, field(current, "basePublic"));
    assertEquals("current-final", field(current, "baseFinal"));
  }

  @Test
  void copiesGenericFieldsWithoutCheckingTypeArguments() {
    GenericConfig<Object> loaded = new GenericConfig<>();
    loaded.value = Integer.valueOf(5000);
    loaded.values = new ArrayList<>(List.of(1, 2));
    GenericConfig<String> current = new GenericConfig<>();

    ConfigurationFields.copy(loaded, current);

    assertSame(loaded.value, field(current, "value"));
    assertSame(loaded.values, field(current, "values"));
  }

  @Test
  void recordTargetsAndRecordValuedFieldsBehaveLikeFinalAndReferenceFields() {
    RecordConfig current = new RecordConfig(1, "current");
    ConfigurationFields.copy(new RecordConfig(9, "loaded"), current);
    assertEquals(new RecordConfig(1, "current"), current);

    Leaf loaded = ConfigurationFieldScenarios.mutatedLeaf();
    Leaf leaf = ConfigurationFieldScenarios.defaultLeaf();
    ConfigurationFields.copy(loaded, leaf);
    assertSame(field(loaded, "point"), field(leaf, "point"));
  }

  @Test
  void innerClassKeepsItsOuterInstanceAndSyntheticNonFinalFieldsAreCopied() throws ReflectiveOperationException {
    Outer.Inner loadedInner = new Outer(100).new Inner();
    loadedInner.innerValue = 5;
    Outer currentOuter = new Outer(200);
    Outer.Inner currentInner = currentOuter.new Inner();

    ConfigurationFields.copy(loadedInner, currentInner);

    assertEquals(5, currentInner.innerValue);
    assertEquals(205, currentInner.read());

    Class<?> holderType = ConfigurationFieldScenarios.syntheticHolderType();
    Field synthetic = holderType.getDeclaredField("syn$value");
    assertTrue(synthetic.isSynthetic());
    Object loadedHolder = holderType.getDeclaredConstructor().newInstance();
    Object currentHolder = holderType.getDeclaredConstructor().newInstance();
    synthetic.set(loadedHolder, "syn-loaded");
    synthetic.set(currentHolder, "syn-current");
    ConfigurationFields.copy(loadedHolder, currentHolder);
    assertEquals("syn-loaded", synthetic.get(currentHolder));
  }

  @Test
  void anonymousSubclassTargetAcceptsASourceOfItsBaseClass() {
    Leaf loaded = ConfigurationFieldScenarios.mutatedLeaf();
    Leaf current = new Leaf("current-final", 11) {
    };

    ConfigurationFields.copy(loaded, current);

    assertEquals("leaf-loaded", field(current, "text"));
    assertEquals(20L, field(current, "basePublic"));
  }

  @Test
  void sourceMissingTheTargetClassIsRejectedBeforeAnyFieldChanges() {
    Mid loadedSuperclass = new Mid("loaded-final");
    loadedSuperclass.basePublic = 77L;
    Leaf current = ConfigurationFieldScenarios.defaultLeaf();

    // Curse threw NoSuchElementException here; both leave the target untouched.
    assertThrows(IllegalArgumentException.class, () -> ConfigurationFields.copy(loadedSuperclass, current));
    assertEquals(10L, field(current, "basePublic"));
    assertEquals("leaf", field(current, "text"));

    assertThrows(IllegalArgumentException.class, () -> ConfigurationFields.copy("a string", current));
    assertEquals("leaf", field(current, "text"));
  }

  @Test
  void siblingSourceWithMatchingFieldNamesIsRejectedWhereCurseCopiedByName() {
    SiblingTwin loaded = new SiblingTwin();
    SiblingA current = new SiblingA();

    assertThrows(IllegalArgumentException.class, () -> ConfigurationFields.copy(loaded, current));

    assertEquals(1, field(current, "shared"));
    assertEquals("a", field(current, "onlyA"));
    assertEquals(
        List.of("SiblingA.shared = Integer(3) [sourceByName:SiblingTwin]", "SiblingA.onlyA = String(twin) [sourceByName:SiblingTwin]"),
        CurseRecordedBehavior.outcomes().get("siblingSourceWithMatchingFieldNames").state().subList(0, 2)
    );
  }

  @Test
  void shadowedFieldsCopyPerDeclaringClassWhereCurseCopiedTheSubclassValueIntoBoth() {
    ShadowSameType loaded = new ShadowSameType();
    loaded.name = "sub-loaded";
    ((ConfigurationFieldScenarios.ShadowBase) loaded).name = "base-loaded";
    ShadowSameType current = new ShadowSameType();

    ConfigurationFields.copy(loaded, current);

    assertEquals("sub-loaded", current.name);
    assertEquals("base-loaded", ((ConfigurationFieldScenarios.ShadowBase) current).name);
    assertTrue(CurseRecordedBehavior.outcomes().get("shadowedFieldSameType").state()
        .contains("ShadowBase.name = String(sub-loaded) [sourceByName:ShadowSameType]"));
  }

  @Test
  void shadowedFieldOfAnotherTypeCopiesWhereCurseFailedHalfway() {
    ShadowIncompatible loaded = new ShadowIncompatible();
    ((ShadowIncompatibleBase) loaded).limit = 9;
    ((ShadowIncompatibleBase) loaded).after = "after-loaded";
    ShadowIncompatible current = new ShadowIncompatible();

    ConfigurationFields.copy(loaded, current);

    assertEquals(9, ((ShadowIncompatibleBase) current).limit);
    assertEquals("after-loaded", ((ShadowIncompatibleBase) current).after);
    Outcome recorded = CurseRecordedBehavior.outcomes().get("shadowedFieldIncompatibleType");
    assertTrue(recorded.threw());
    assertEquals("java.lang.RuntimeException <- java.lang.IllegalArgumentException", recorded.exception());
  }

  @Test
  void staticFieldSharingAnInstanceFieldNameIsIgnoredWhereCurseReadTheStatic() {
    ConfigurationFieldScenarios.resetStatics();
    StaticShadowSub loaded = new StaticShadowSub();
    ((StaticShadowBase) loaded).mode = "base-loaded";
    StaticShadowBase current = new StaticShadowBase();

    ConfigurationFields.copy(loaded, current);

    assertEquals("base-loaded", current.mode);
    assertEquals("static-sub-mode", StaticShadowSub.mode);
    assertTrue(CurseRecordedBehavior.outcomes().get("staticFieldShadowsInstanceFieldSubclassSource").state()
        .contains("StaticShadowBase.mode = String(static-sub-mode) [sourceByName:StaticShadowSub]"));
  }

  @Test
  void nullArgumentsThrowExceptWhenTheTargetHasNothingToCopy() {
    Leaf leaf = ConfigurationFieldScenarios.defaultLeaf();

    assertThrows(NullPointerException.class, () -> ConfigurationFields.copy(null, leaf));
    assertEquals("leaf", field(leaf, "text"));
    assertThrows(NullPointerException.class, () -> ConfigurationFields.copy(leaf, null));

    // Curse threw NullPointerException for any null source; plain reflection only reads the source per field.
    RecordConfig record = new RecordConfig(1, "current");
    ConfigurationFields.copy(null, record);
    assertEquals(new RecordConfig(1, "current"), record);
    assertTrue(CurseRecordedBehavior.outcomes().get("nullSourceWithNoCopyableFieldsOnTarget").threw());
  }

  @Test
  void encapsulatedJdkFieldsFailWithoutBeingWrapped() {
    List<String> loaded = new ArrayList<>(List.of("loaded"));
    List<String> current = new ArrayList<>(List.of("current"));

    // Curse wrapped this in a bare RuntimeException.
    assertThrows(InaccessibleObjectException.class, () -> ConfigurationFields.copy(loaded, current));
    assertEquals(List.of("current"), current);
    assertNotSame(loaded, current);
  }

  @Test
  void copyingTwiceEqualsCopyingOnce() {
    Leaf loaded = ConfigurationFieldScenarios.mutatedLeaf();
    Leaf once = ConfigurationFieldScenarios.defaultLeaf();
    Leaf twice = ConfigurationFieldScenarios.defaultLeaf();

    ConfigurationFields.copy(loaded, once);
    ConfigurationFields.copy(loaded, twice);
    ConfigurationFields.copy(loaded, twice);

    for (Field field : ConfigurationFieldScenarios.hierarchyFields(Leaf.class)) {
      if (ConfigurationFieldScenarios.copyable(field)) {
        assertEquals(ConfigurationFieldScenarios.read(field, once), ConfigurationFieldScenarios.read(field, twice), field.getName());
      }
    }
    assertEquals("target-final", field(once, ConfigurationFieldScenarios.Base.class, "baseFinal"));
  }
}
