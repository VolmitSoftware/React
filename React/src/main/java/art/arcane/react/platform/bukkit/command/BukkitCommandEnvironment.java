package art.arcane.react.platform.bukkit.command;

import art.arcane.react.React;
import art.arcane.react.api.benchmark.EnvironmentReport;
import art.arcane.react.content.directorcommand.EnvironmentCommands;
import art.arcane.react.platform.bukkit.BukkitCommandSender;
import art.arcane.react.util.plugin.VolmitSender;
import art.arcane.volmlib.util.web.MclogsClient;
import org.bukkit.Bukkit;

public final class BukkitCommandEnvironment {
  private final EnvironmentReport report = new EnvironmentReport(new MclogsClient());

  public void info(VolmitSender sender) {
    EnvironmentCommands.info(sender, React.instance.getDescription().getVersion(), Bukkit.getVersion());
    report.upload(BukkitCommandSender.bukkit(sender));
  }
}
