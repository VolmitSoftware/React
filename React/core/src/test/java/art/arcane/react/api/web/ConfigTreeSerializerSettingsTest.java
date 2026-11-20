package art.arcane.react.api.web;

import art.arcane.react.api.entity.EntityPriority;
import art.arcane.react.api.web.dto.ConfigNodeDto;
import art.arcane.react.api.web.dto.ConfigSectionDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTreeSerializerSettingsTest {
  static final class Settings {
    String language = "en_US";
    boolean integrationSecretsEnabled = false;
    EntityPriority priority = new EntityPriority();
  }

  @Test
  void anyReactSettingsObjectListsGeneralAndNestedSections() {
    ConfigSectionDto[] sections = new ConfigTreeSerializer().serialize(new Settings());
    List<String> names = new ArrayList<>();
    for (ConfigSectionDto section : sections) {
      names.add(section.name);
    }

    assertEquals("general", sections[0].name);
    assertTrue(names.contains("priority"), names.toString());
    List<String> generalKeys = new ArrayList<>();
    for (ConfigNodeDto node : sections[0].nodes) {
      generalKeys.add(node.key);
    }
    assertEquals(List.of("main.language", "main.integrationSecretsEnabled"), generalKeys);
    for (ConfigSectionDto section : sections) {
      for (ConfigNodeDto node : section.nodes) {
        assertTrue(node.key.startsWith("main." + (section.name.equals("general") ? "" : section.name + ".")), node.key);
      }
    }
  }
}
