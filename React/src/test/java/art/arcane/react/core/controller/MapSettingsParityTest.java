package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.api.rendering.MapSettings;
import art.arcane.react.util.common.scheduling.Ticker;
import art.arcane.react.util.project.config.ConfigDescription;
import art.arcane.react.util.project.config.ConfigDoc;
import art.arcane.react.util.project.config.TomlCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapSettingsParityTest {
  private React previous;

  @BeforeEach
  void setUp() {
    previous = React.instance;
    React plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getTicker()).thenReturn(Mockito.mock(Ticker.class));
    React.instance = plugin;
  }

  @AfterEach
  void tearDown() {
    React.instance = previous;
  }

  @Test
  void mapSettingsMatchMapControllerKeysDocsAndDefaults() throws IllegalAccessException {
    MapController controller = new MapController();
    MapSettings settings = new MapSettings();
    List<Field> controllerFields = documented(MapController.class);
    List<Field> settingsFields = documented(MapSettings.class);

    assertEquals(names(controllerFields), names(settingsFields));
    assertEquals(MapController.class.getAnnotation(ConfigDescription.class).value(),
        MapSettings.class.getAnnotation(ConfigDescription.class).value());
    for (int index = 0; index < controllerFields.size(); index++) {
      Field expected = controllerFields.get(index);
      Field actual = settingsFields.get(index);
      expected.setAccessible(true);
      actual.setAccessible(true);
      assertEquals(expected.getType(), actual.getType(), expected.getName());
      assertEquals(expected.getAnnotation(ConfigDoc.class).value(), actual.getAnnotation(ConfigDoc.class).value(), expected.getName());
      assertEquals(expected.getAnnotation(ConfigDoc.class).impact(), actual.getAnnotation(ConfigDoc.class).impact(), expected.getName());
      assertEquals(expected.get(controller), actual.get(settings), expected.getName());
    }
  }

  @Test
  void mapSettingsWriteTheSameTomlBodyAsMapController() {
    String controllerToml = TomlCodec.toToml(new MapController(), "core:map");
    String settingsToml = TomlCodec.toToml(new MapSettings(), "core:map");

    assertTrue(settingsToml.contains("# Main maintenance cadence for map repair logic in milliseconds."));
    assertEquals(body(controllerToml), body(settingsToml));
  }

  private static String body(String toml) {
    return toml.substring(toml.indexOf("\n\n"));
  }

  private static List<Field> documented(Class<?> type) {
    List<Field> fields = new ArrayList<>();
    for (Field field : type.getDeclaredFields()) {
      if (field.isAnnotationPresent(ConfigDoc.class)) {
        fields.add(field);
      }
    }
    return fields;
  }

  private static List<String> names(List<Field> fields) {
    List<String> names = new ArrayList<>();
    for (Field field : fields) {
      names.add(field.getName());
    }
    return names;
  }
}
