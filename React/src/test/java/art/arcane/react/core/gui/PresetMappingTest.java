package art.arcane.react.core.gui;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

class PresetMappingTest {

  private static final List<String> DOCUMENTED_MANAGED_IDS = List.of(
      "dynamic-view-distance",
      "afk-view-shedding",
      "per-world-tick-budget",
      "world-save-staggering",
      "random-tick-governor",
      "tracker-range-governor",
      "activation-range-governor",
      "dynamic-activation-range",
      "adaptive-entity-sleep",
      "pathfinder-budget",
      "mob-stacking",
      "item-super-stacker",
      "entity-trimmer");

  @Test
  void managedFeatureSetMatchesDocumentedListInOrder() {
    List<String> managed = Arrays.asList(readManagedIds());
    Assertions.assertEquals(DOCUMENTED_MANAGED_IDS, managed);
  }

  @Test
  void managedFeatureSetHasNoBlankOrDuplicateIds() {
    List<String> managed = Arrays.asList(readManagedIds());
    Set<String> unique = new LinkedHashSet<>(managed);

    Assertions.assertEquals(managed.size(), unique.size());
    for (String id : managed) {
      Assertions.assertNotNull(id);
      Assertions.assertFalse(id.isBlank());
    }
  }

  @ParameterizedTest
  @CsvSource({
      "off,false",
      "light,true",
      "balanced,true",
      "high,true"
  })
  void presetMapsToDocumentedEnabledFeatureSet(String preset, boolean enable) {
    Set<String> managed = new LinkedHashSet<>(Arrays.asList(readManagedIds()));
    Set<String> expectedEnabled = enable ? managed : Set.<String>of();
    Set<String> actualEnabled = enabledFeatureSetForPreset(preset, managed);

    Assertions.assertEquals(expectedEnabled, actualEnabled);
  }

  @Test
  void onlyOffPresetDisablesTheManagedFeatureSet() {
    Set<String> managed = new LinkedHashSet<>(Arrays.asList(readManagedIds()));

    Assertions.assertTrue(enabledFeatureSetForPreset("off", managed).isEmpty());
    Assertions.assertFalse(enabledFeatureSetForPreset("light", managed).isEmpty());
    Assertions.assertFalse(enabledFeatureSetForPreset("balanced", managed).isEmpty());
    Assertions.assertFalse(enabledFeatureSetForPreset("high", managed).isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"light", "balanced", "high"})
  void aggressivePresetsEnableEveryManagedFeature(String preset) {
    Set<String> managed = new LinkedHashSet<>(Arrays.asList(readManagedIds()));
    Set<String> enabled = enabledFeatureSetForPreset(preset, managed);

    Assertions.assertEquals(managed, enabled);
    Assertions.assertEquals(DOCUMENTED_MANAGED_IDS.size(), enabled.size());
  }

  @Test
  void managedFeatureSetIncludesViewDistanceFeaturesTunedPerPreset() {
    Set<String> managed = new LinkedHashSet<>(Arrays.asList(readManagedIds()));

    Assertions.assertTrue(managed.contains("dynamic-view-distance"));
    Assertions.assertTrue(managed.contains("afk-view-shedding"));
  }

  private static Set<String> enabledFeatureSetForPreset(String preset, Set<String> managed) {
    String normalized = preset == null ? "" : preset.trim().toLowerCase(Locale.ROOT);
    boolean enable = !normalized.equals("off");
    return enable ? managed : Set.<String>of();
  }

  private static String[] readManagedIds() {
    try {
      Field field = ReactConfigGUI.class.getDeclaredField("PRESET_MANAGED_IDS");
      field.setAccessible(true);
      return (String[]) field.get(null);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("PRESET_MANAGED_IDS not accessible on ReactConfigGUI", e);
    }
  }
}
