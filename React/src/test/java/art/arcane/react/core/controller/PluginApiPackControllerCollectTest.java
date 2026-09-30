package art.arcane.react.core.controller;

import art.arcane.react.React;
import art.arcane.react.core.pluginapi.PluginApiPackRuntime;
import art.arcane.react.util.common.scheduling.Ticker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

class PluginApiPackControllerCollectTest {
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
  @SuppressWarnings("unchecked")
  void oneFailingPackDoesNotStopTheRemainingPacks() throws ReflectiveOperationException {
    PluginApiPackController controller = new PluginApiPackController();
    PluginApiPackRuntime failing = Mockito.mock(PluginApiPackRuntime.class);
    PluginApiPackRuntime healthy = Mockito.mock(PluginApiPackRuntime.class);
    IllegalStateException failure = new IllegalStateException("collect failed");
    Mockito.doThrow(failure).when(failing).collect(Mockito.anyLong());
    Field packsField = PluginApiPackController.class.getDeclaredField("packs");
    packsField.setAccessible(true);
    Map<String, PluginApiPackRuntime> packs = (Map<String, PluginApiPackRuntime>) packsField.get(controller);
    packs.put("failing", failing);
    packs.put("healthy", healthy);
    Method collect = PluginApiPackController.class.getDeclaredMethod("collectOnServerThread");
    collect.setAccessible(true);

    try (MockedStatic<React> react = Mockito.mockStatic(React.class)) {
      Assertions.assertDoesNotThrow(() -> collect.invoke(controller));
      Assertions.assertDoesNotThrow(() -> collect.invoke(controller));

      Mockito.verify(healthy, Mockito.times(2)).collect(Mockito.anyLong());
      react.verify(() -> React.reportError(Mockito.contains("failing"), Mockito.same(failure)), Mockito.times(1));
    }
  }
}
