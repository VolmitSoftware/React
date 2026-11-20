package art.arcane.react.util.director;

import art.arcane.react.content.directorcommand.CommandReact;
import art.arcane.react.platform.CommandHost;
import art.arcane.react.util.plugin.VolmitSender;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

class CommandAvailabilityTest {
  @Test
  void hostWithoutOverrideDoesNotSupportTheCommand() {
    CommandHost host = new CommandHost() {
    };

    Assertions.assertFalse(CommandAvailability.supported(host, "pluginApiStatus"));
  }

  @Test
  void hostOverrideSupportsTheCommand() {
    Assertions.assertTrue(CommandAvailability.supported(new StatusHost(), "pluginApiStatus"));
    Assertions.assertFalse(CommandAvailability.supported(new StatusHost(), "pluginApiReload"));
  }

  @Test
  void everyRefusingHostMethodIsDeclaredByACommand() {
    Set<String> declared = declaredHostCommands();
    Set<String> hostMethods = new HashSet<>();
    for (Method method : CommandHost.class.getDeclaredMethods()) {
      hostMethods.add(method.getName());
      if (method.isDefault() && method.getParameterCount() > 0 && method.getParameterTypes()[0] == VolmitSender.class
          && !method.getName().equals("openLanguage")) {
        Assertions.assertTrue(declared.contains(method.getName()), "No command declares " + method.getName());
      }
    }
    for (String name : declared) {
      Assertions.assertTrue(hostMethods.contains(name), "Unknown command host method " + name);
    }
  }

  private static Set<String> declaredHostCommands() {
    Set<String> declared = new HashSet<>();
    Set<Class<?>> seen = new HashSet<>();
    Deque<Class<?>> pending = new ArrayDeque<>();
    pending.add(CommandReact.class);
    while (!pending.isEmpty()) {
      Class<?> type = pending.poll();
      if (!seen.add(type)) {
        continue;
      }
      for (Method method : type.getDeclaredMethods()) {
        HostCommand command = method.getAnnotation(HostCommand.class);
        if (command != null) {
          declared.add(command.value());
        }
      }
      for (Field field : type.getDeclaredFields()) {
        if (DirectorExecutor.class.isAssignableFrom(field.getType())) {
          pending.add(field.getType());
        }
      }
    }
    return declared;
  }

  private static final class StatusHost implements CommandHost {
    @Override
    public void pluginApiStatus(VolmitSender sender) {
    }
  }
}
