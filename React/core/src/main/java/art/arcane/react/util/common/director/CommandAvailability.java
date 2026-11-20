package art.arcane.react.util.director;

import art.arcane.react.platform.CommandHost;
import art.arcane.react.platform.cost.OwnerNoun;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeNode;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which commands a mod loader can run, judged by whether its command host implements the method a command calls; a
 * command host that leaves the default refuses the command. Bukkit and proxies list every command as before.
 */
public final class CommandAvailability {
  private static final Map<String, Boolean> SUPPORTED = new ConcurrentHashMap<>();

  private CommandAvailability() {
  }

  public static boolean listed(DirectorRuntimeNode node) {
    return !OwnerNoun.modded() || available(DirectorHosts.command(), node);
  }

  public static List<DirectorRuntimeNode> listed(List<DirectorRuntimeNode> nodes) {
    if (!OwnerNoun.modded()) {
      return nodes;
    }
    List<DirectorRuntimeNode> visible = new ArrayList<>(nodes.size());
    for (DirectorRuntimeNode node : nodes) {
      if (available(DirectorHosts.command(), node)) {
        visible.add(node);
      }
    }
    return visible;
  }

  static boolean available(CommandHost host, DirectorRuntimeNode node) {
    if (node == null) {
      return true;
    }
    Method method = node.getMethod();
    HostCommand command = method == null ? null : method.getAnnotation(HostCommand.class);
    if (command != null) {
      return supported(host, command.value());
    }
    if (node.getChildren().isEmpty() || node.isInvocable()) {
      return true;
    }
    for (DirectorRuntimeNode child : node.getChildren()) {
      if (available(host, child)) {
        return true;
      }
    }
    return false;
  }

  static boolean supported(CommandHost host, String methodName) {
    if (host == null) {
      return false;
    }
    Class<?> type = host.getClass();
    return SUPPORTED.computeIfAbsent(type.getName() + '#' + methodName, ignored -> implemented(type, methodName));
  }

  private static boolean implemented(Class<?> type, String methodName) {
    boolean found = false;
    for (Method method : type.getMethods()) {
      if (method.getName().equals(methodName)) {
        found = true;
        if (method.getDeclaringClass() != CommandHost.class) {
          return true;
        }
      }
    }
    return !found;
  }
}
