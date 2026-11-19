package art.arcane.react.platform.bukkit.command;

import art.arcane.react.React;
import art.arcane.react.core.controller.PlayerController;
import art.arcane.react.core.gui.MonitorConfigGUI;
import art.arcane.react.core.gui.ReactConfigGUI;
import art.arcane.react.localization.ReactLanguage;
import art.arcane.react.localization.catalog.CommandMessages;
import art.arcane.react.platform.bukkit.BukkitCommandSender;
import art.arcane.react.util.plugin.VolmitSender;
import org.bukkit.entity.Player;

public final class BukkitCommandConfig {
  public void gui(VolmitSender sender) {
    Player player = BukkitCommandSender.player(sender);
    if (!ReactConfigGUI.canConfigure(player)) {
      ReactLanguage.sendPrefixed(sender, CommandMessages.CONFIG_PERMISSION);
      return;
    }

    ReactConfigGUI.open(player);
  }

  public void monitor(VolmitSender sender) {
    Player player = BukkitCommandSender.player(sender);
    MonitorConfigGUI.editMonitorConfiguration(player, React.controller(PlayerController.class).getPlayer(player).getSettings().getMonitorConfiguration(),
        (c) -> React.controller(PlayerController.class).getPlayer(player).saveSettings());
  }
}
