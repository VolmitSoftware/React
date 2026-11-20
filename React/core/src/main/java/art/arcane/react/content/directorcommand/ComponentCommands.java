package art.arcane.react.content.directorcommand;

import art.arcane.react.core.controller.ReactComponents;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.localization.catalog.GuiMessages;
import art.arcane.react.platform.ComponentHost;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.react.util.project.config.ConfigLocalization;
import art.arcane.react.util.project.registry.Registered;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The listing and toggling behind {@code /react feature} and {@code /react tweak}, worded with the config editor's
 * messages.
 */
final class ComponentCommands {
  private ComponentCommands() {
  }

  static <T extends Registered> void list(VolmitSender sender, TextKey header, Collection<T> components, Set<String> active,
                                          Predicate<T> enabled) {
    if (components == null || active == null) {
      ReactLanguage.sendPrefixed(sender, GuiMessages.CONFIG_NO_MODULES);
      return;
    }
    List<T> sorted = components.stream().sorted(Comparator.comparing(Registered::getId)).toList();
    ReactLanguage.sendPrefixed(sender, header,
        MessageArgument.untrusted("active", active.size()),
        MessageArgument.untrusted("total", sorted.size()));
    for (T component : sorted) {
      String summary = ConfigLocalization.description(component.getClass());
      ReactLanguage.send(sender, CommandMessages.ACTION_AUDIT_ENTRY,
          MessageArgument.untrusted("id", component.getId()),
          MessageArgument.untrusted("state", state(active.contains(component.getId()), enabled.test(component))),
          MessageArgument.untrusted("summary", summary));
    }
  }

  static <T extends Registered> void set(VolmitSender sender, String category, T component, Predicate<T> enabled, boolean value) {
    String path = category + "." + component.getId() + ".enabled";
    boolean before = enabled.test(component);
    Optional<ComponentHost> host = ReactComponents.host();
    if (host.isEmpty() || !host.get().applyConfigValue(path, value)) {
      ReactLanguage.sendPrefixed(sender, GuiMessages.CONFIG_SET_FAILED, MessageArgument.untrusted("path", path));
      return;
    }
    ReactLanguage.sendPrefixed(sender, GuiMessages.CONFIG_UPDATED,
        MessageArgument.untrusted("path", path),
        MessageArgument.untrusted("before", before),
        MessageArgument.untrusted("after", value));
  }

  private static String state(boolean running, boolean enabled) {
    if (running) {
      return ReactLanguage.plain(GuiMessages.CONFIG_STATUS_ACTIVE);
    }
    return ReactLanguage.plain(enabled ? CommandMessages.STATE_ENABLED : CommandMessages.STATE_DISABLED);
  }
}
