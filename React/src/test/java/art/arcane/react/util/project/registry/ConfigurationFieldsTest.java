package art.arcane.react.util.project.registry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
