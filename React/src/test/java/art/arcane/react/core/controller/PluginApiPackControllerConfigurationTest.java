package art.arcane.react.core.controller;

import art.arcane.react.util.project.config.TomlCodec;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

class PluginApiPackControllerConfigurationTest {
  private static final List<String> RUNTIME_FIELDS = List.of(
      "collectionQueued",
      "running",
      "scanQueued",
      "packs",
      "validationErrors"
  );

  @Test
  void runtimeOnlyControllerDoesNotLoadCoreConfiguration() throws ReflectiveOperationException {
    PluginApiPackController controller = mock(PluginApiPackController.class, CALLS_REAL_METHODS);

    controller.loadConfiguration();

    assertTrue(controller.reloadConfiguration());
    Object snapshot = controller.prepareConfigurationSnapshot(null, "not valid toml");
    assertSame(controller, snapshot);
    assertTrue(controller.applyConfigurationSnapshot(snapshot));
    assertFalse(controller.applyConfigurationSnapshot(new Object()));
    assertEquals(PluginApiPackController.class,
        PluginApiPackController.class.getMethod("loadConfiguration").getDeclaringClass());
    assertEquals(PluginApiPackController.class,
        PluginApiPackController.class.getMethod("reloadConfiguration").getDeclaringClass());
  }

  @Test
  void runtimeStateIsExcludedFromToml() throws ReflectiveOperationException {
    PluginApiPackController controller = mock(PluginApiPackController.class, CALLS_REAL_METHODS);
    String serialized = TomlCodec.toToml(controller, "core:plugin-api-packs");

    for (String fieldName : RUNTIME_FIELDS) {
      Field field = PluginApiPackController.class.getDeclaredField(fieldName);
      assertTrue(Modifier.isTransient(field.getModifiers()));
      assertFalse(serialized.contains(fieldName + " ="));
      assertFalse(serialized.contains("[" + fieldName + "]"));
    }
  }
}
