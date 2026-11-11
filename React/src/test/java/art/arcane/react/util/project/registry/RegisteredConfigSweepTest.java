package art.arcane.react.util.project.registry;

import art.arcane.react.React;
import art.arcane.react.util.common.scheduling.Ticker;
import art.arcane.react.util.project.config.ConfigHotloadSnapshot;
import art.arcane.react.util.project.config.TomlCodec;
import art.arcane.react.util.project.registry.RegisteredTypes.Built;
import art.arcane.react.util.project.registry.RegisteredTypes.Construction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the config copy against every Registered implementation React ships: features, tweaks,
 * actions, samplers and controllers. A config file can only hotload if each of its fields makes
 * it from the freshly parsed object onto the live one.
 */
class RegisteredConfigSweepTest {
  private static final long HOTLOAD_FILE_LIMIT = 256L * 1024L;
  private static final List<String> REGISTRY_PACKAGES = List.of(
      "art.arcane.react.content.feature.",
      "art.arcane.react.content.tweak.",
      "art.arcane.react.content.action.",
      "art.arcane.react.content.sampler."
  );

  private static React previousPlugin;
  private static List<Class<? extends Registered>> types;

  @TempDir
  static Path configRoot;

  @BeforeAll
  static void installPlugin() {
    previousPlugin = React.instance;
    React plugin = Mockito.mock(React.class);
    Mockito.when(plugin.getName()).thenReturn("React");
    Mockito.when(plugin.namespace()).thenReturn("react");
    Mockito.when(plugin.getTicker()).thenReturn(Mockito.mock(Ticker.class));
    React.instance = plugin;
    types = RegisteredTypes.concrete();
  }

  @AfterAll
  static void restorePlugin() {
    React.instance = previousPlugin;
  }

  private static Registered build(Class<? extends Registered> type) {
    Built built = RegisteredTypes.build(type);
    Assumptions.assumeTrue(built.construction() != Construction.UNAVAILABLE,
        () -> type.getName() + " cannot be loaded without a server: " + built.failure());
    return built.instance();
  }

  private static Map<Field, Object> values(Object instance) {
    Map<Field, Object> values = new LinkedHashMap<>();
    for (Field field : ConfigurationFieldScenarios.hierarchyFields(instance.getClass())) {
      values.put(field, ConfigurationFieldScenarios.read(field, instance));
    }
    return values;
  }

  private static boolean sameValue(Field field, Object left, Object right) {
    return field.getType().isPrimitive() ? left.equals(right) : left == right;
  }

  private static void assertArrived(Field field, Object expected, Object actual) {
    String label = field.getDeclaringClass().getName() + "." + field.getName();
    if (field.getType().isPrimitive()) {
      assertEquals(expected, actual, label);
    } else {
      assertSame(expected, actual, label);
    }
  }

  private static boolean usesDefault(Class<?> type, String method, Class<?>... parameters) {
    try {
      return type.getMethod(method, parameters).getDeclaringClass() == Registered.class;
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void findsEveryKindOfRegisteredComponent() {
    Map<String, Integer> perPackage = new TreeMap<>();
    for (Class<? extends Registered> type : types) {
      for (String prefix : REGISTRY_PACKAGES) {
        if (type.getName().startsWith(prefix)) {
          perPackage.merge(prefix, 1, Integer::sum);
        }
      }
    }

    assertEquals(REGISTRY_PACKAGES.size(), perPackage.size(), perPackage.toString());
    assertTrue(types.stream().anyMatch(type -> type.getName().startsWith("art.arcane.react.core.controller.")));
    assertTrue(types.size() >= 200, "Only found " + types.size() + " Registered implementations");
  }

  @Test
  void everyRegistryScannedComponentBuildsThroughItsPublicConstructor() {
    List<String> fallbacks = new ArrayList<>();
    for (Class<? extends Registered> type : types) {
      boolean scanned = REGISTRY_PACKAGES.stream().anyMatch(prefix -> type.getName().startsWith(prefix));
      if (!scanned || !RegisteredTypes.hasPublicNoArgConstructor(type)) {
        continue;
      }
      Built built = RegisteredTypes.build(type);
      if (built.construction() != Construction.PUBLIC_NO_ARG && built.construction() != Construction.UNAVAILABLE) {
        fallbacks.add(type.getName() + ": " + built.failure());
      }
    }

    assertEquals(List.of(), fallbacks);
  }

  @Test
  void noComponentDeclaresTheSameConfigFieldNameTwice() {
    List<String> shadowed = new ArrayList<>();
    for (Class<? extends Registered> type : types) {
      for (Field field : ConfigurationFieldScenarios.hierarchyFields(type)) {
        if (!ConfigurationFieldScenarios.copyable(field)) {
          continue;
        }
        Field firstByName = ConfigurationFieldScenarios.findByName(type, field.getName());
        if (!field.equals(firstByName)) {
          shadowed.add(type.getName() + ": " + field + " is hidden by " + firstByName);
        }
      }
    }

    assertEquals(List.of(), shadowed);
  }

  @TestFactory
  Stream<DynamicTest> everyConfigFieldArrivesOnTheLiveObject() {
    return types.stream().map(type -> DynamicTest.dynamicTest(type.getName(), () -> {
      Registered live = build(type);
      Registered legacyLive = build(type);
      Registered loaded = build(type);
      ConfigFieldMutator.Report report = ConfigFieldMutator.mutate(loaded, new Random(type.getName().hashCode()), false);
      Map<Field, Object> before = values(live);
      Map<Field, Object> loadedValues = values(loaded);
      for (Field field : report.changed()) {
        assertFalse(sameValue(field, before.get(field), loadedValues.get(field)),
            () -> "The edit to " + field + " is indistinguishable from the live value");
      }

      ConfigurationFields.copy(loaded, live);
      LegacyCurseCopy.copy(loaded, legacyLive);

      Map<Field, Object> after = values(live);
      Map<Field, Object> legacyAfter = values(legacyLive);
      for (Field field : before.keySet()) {
        Object loadedValue = loadedValues.get(field);
        if (ConfigurationFieldScenarios.copyable(field) && loadedValue != null) {
          assertArrived(field, loadedValue, after.get(field));
          assertArrived(field, legacyAfter.get(field), after.get(field));
        } else {
          assertArrived(field, before.get(field), after.get(field));
        }
      }
      for (Field field : loadedValues.keySet()) {
        assertArrived(field, loadedValues.get(field), ConfigurationFieldScenarios.read(field, loaded));
      }
    }));
  }

  @TestFactory
  Stream<DynamicTest> everyEditedConfigFileAppliesToTheLiveObject() {
    return types.stream()
        .filter(RegisteredTypes::hasPublicNoArgConstructor)
        .filter(type -> usesDefault(type, "prepareConfigurationSnapshot", File.class, String.class))
        .map(type -> DynamicTest.dynamicTest(type.getName(), () -> {
          Built liveBuilt = RegisteredTypes.build(type);
          Assumptions.assumeTrue(liveBuilt.construction() == Construction.PUBLIC_NO_ARG,
              () -> type.getName() + " needs a running server to construct: " + liveBuilt.failure());
          Registered live = liveBuilt.instance();
          Registered edited = build(type);
          ConfigFieldMutator.Report report = ConfigFieldMutator.mutate(edited, new Random(type.getName().hashCode()), false);
          String sourceTag = live.getConfigCategory() + ":" + live.getId();
          Path file = configRoot.resolve(live.getConfigCategory()).resolve(live.getId() + ".toml");
          Files.createDirectories(file.getParent());
          Files.writeString(file, TomlCodec.toToml(edited, sourceTag), StandardCharsets.UTF_8);

          ConfigHotloadSnapshot snapshot = ConfigHotloadSnapshot.capture(file, HOTLOAD_FILE_LIMIT);
          Object prepared = live.prepareConfigurationSnapshot(file.toFile(), snapshot.rawContent());
          assertSame(type, prepared.getClass());
          Map<Field, Object> before = values(live);
          assertTrue(live.applyConfigurationSnapshot(prepared));

          Map<Field, Object> after = values(live);
          Map<Field, Object> preparedValues = values(prepared);
          boolean plainApply = usesDefault(type, "applyConfigurationSnapshot", Object.class);
          for (Field field : before.keySet()) {
            if (Modifier.isStatic(field.getModifiers())) {
              continue;
            }
            Object preparedValue = preparedValues.get(field);
            if (!ConfigurationFieldScenarios.copyable(field)) {
              if (plainApply) {
                assertArrived(field, before.get(field), after.get(field));
              }
            } else if (preparedValue != null && plainApply) {
              assertArrived(field, preparedValue, after.get(field));
            }
          }
          if (plainApply) {
            for (Field field : report.changed()) {
              if (ConfigFieldMutator.isScalar(field.getType())) {
                assertEquals(ConfigurationFieldScenarios.read(field, edited), after.get(field),
                    () -> "The edited value of " + field + " did not reach the live object");
              }
            }
          }
        }));
  }

  @Test
  void malformedFileNeverReachesAnyLiveObject() throws IOException {
    int checked = 0;
    for (Class<? extends Registered> type : types) {
      if (!RegisteredTypes.hasPublicNoArgConstructor(type)
          || !usesDefault(type, "prepareConfigurationSnapshot", File.class, String.class)) {
        continue;
      }
      Built built = RegisteredTypes.build(type);
      if (built.construction() != Construction.PUBLIC_NO_ARG) {
        continue;
      }
      Registered live = built.instance();
      Map<Field, Object> before = values(live);
      File file = configRoot.resolve("malformed").resolve(type.getName() + ".toml").toFile();

      boolean rejected = false;
      try {
        live.prepareConfigurationSnapshot(file, "enabled = [unterminated\n= = =");
      } catch (IOException expected) {
        rejected = true;
      }

      assertTrue(rejected, type.getName() + " accepted a malformed config");
      Map<Field, Object> after = values(live);
      for (Field field : before.keySet()) {
        assertArrived(field, before.get(field), after.get(field));
      }
      checked++;
    }

    assertTrue(checked >= 200, "Only checked " + checked + " components");
  }
}
