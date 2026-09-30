package art.arcane.react.testutil;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Registry;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Proxy;

public final class FakeRegistries {
  private FakeRegistries() {
  }

  public static void initialize(Class<?> registryBackedType) {
    try (MockedStatic<RegistryAccess> registryAccess = Mockito.mockStatic(RegistryAccess.class)) {
      RegistryAccess access = Mockito.mock(RegistryAccess.class);
      registryAccess.when(RegistryAccess::registryAccess).thenReturn(access);
      Mockito.when(access.getRegistry(Mockito.any(RegistryKey.class))).thenAnswer(invocation -> emptyRegistry());
      Mockito.when(access.getRegistry(Mockito.any(Class.class))).thenAnswer(invocation -> emptyRegistry());
      Class.forName(registryBackedType.getName(), true, registryBackedType.getClassLoader());
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(registryBackedType.getName() + " is not on the test classpath", e);
    }
  }

  private static Object emptyRegistry() {
    return Proxy.newProxyInstance(Registry.class.getClassLoader(), new Class<?>[]{Registry.class},
        (proxy, method, arguments) -> switch (method.getName()) {
          case "equals" -> proxy == arguments[0];
          case "hashCode" -> System.identityHashCode(proxy);
          case "toString" -> "EmptyRegistry";
          default -> null;
        });
  }
}
