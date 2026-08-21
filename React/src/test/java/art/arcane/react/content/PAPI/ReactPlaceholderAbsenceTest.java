package art.arcane.react.content.PAPI;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReactPlaceholderAbsenceTest {
  private static final String[] LOADS_WITHOUT_PLACEHOLDER_API = {
      "art.arcane.react.content.PAPI.ReactPlaceholders",
      "art.arcane.volmlib.util.bukkit.papi.PlaceholderRegistration"
  };

  private static final String[] REQUIRES_PLACEHOLDER_API = {
      "art.arcane.react.content.PAPI.ReactPlaceholderInstaller",
      "art.arcane.react.content.PAPI.PapiExpansion"
  };

  @Test
  void everyEnablePathClassLoadsWhenPlaceholderApiIsAbsent() {
    ClassLoader hidden = new PlaceholderApiHidingLoader();

    for (String name : LOADS_WITHOUT_PLACEHOLDER_API) {
      assertDoesNotThrow(() -> Class.forName(name, true, hidden), name
          + " must load when PlaceholderAPI is not installed");
    }
  }

  @Test
  void theExpansionItselfStillDependsOnPlaceholderApi() {
    ClassLoader hidden = new PlaceholderApiHidingLoader();

    for (String name : REQUIRES_PLACEHOLDER_API) {
      assertThrows(Throwable.class, () -> Class.forName(name, true, hidden), name
          + " is expected to depend on PlaceholderAPI, so the split above is what keeps the plugin loadable");
    }
  }

  private static final class PlaceholderApiHidingLoader extends URLClassLoader {
    private PlaceholderApiHidingLoader() {
      super(classpath(), ClassLoader.getPlatformClassLoader());
    }

    private static URL[] classpath() {
      String[] entries = System.getProperty("java.class.path").split(java.io.File.pathSeparator);
      URL[] resolved = new URL[entries.length];

      for (int i = 0; i < entries.length; i++) {
        try {
          resolved[i] = new java.io.File(entries[i]).toURI().toURL();
        } catch (Throwable failure) {
          throw new IllegalStateException(entries[i], failure);
        }
      }

      return resolved;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      if (name.startsWith("me.clip.")) {
        throw new ClassNotFoundException(name);
      }

      return super.loadClass(name, resolve);
    }
  }
}
