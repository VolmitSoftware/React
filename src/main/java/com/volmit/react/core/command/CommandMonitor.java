package com.volmit.react.core.command;

import com.volmit.react.React;
import com.volmit.react.api.command.RConst;
import com.volmit.react.core.gui.MonitorConfigGUI;
import com.volmit.react.util.J;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.executors.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CommandMonitor {

    public static void register() {
        CommandAPICommand monitorCommand = new CommandAPICommand("monitor")
                .withAliases("mon", "m")
                .withPermission("react.monitor")
                .executes((CommandExecutor) (sender, args) -> monitor(sender));

        CommandAPICommand monitorEditCommand = new CommandAPICommand("monitoredit")
                .withAliases("monedit", "medit", "med")
                .withPermission("react.monitor.edit")
                .executes((CommandExecutor) (sender, args) -> monitorConfig(sender));

        CommandReact.addSubcommands(monitorCommand, monitorEditCommand);
    }

    public static void monitor(CommandSender sender) {
        if (sender instanceof Player p) {
            React.instance.getPlayerController().getPlayer(p).toggleActionBar();
            RConst.success("Monitor " + (React.instance.getPlayerController().getPlayer(p).isActionBarMonitoring() ? "Enabled" : "Disabled"));
        } else {
            RConst.error("You must be a player to use this command.");
        }
    }

    public static void monitorConfig(CommandSender sender) {
        if (sender instanceof Player p) {
            J.a(() -> MonitorConfigGUI.editMonitorConfiguration(p, React.instance.getPlayerController().getPlayer(p).getSettings().getMonitorConfiguration(),
                    (c) -> React.instance.getPlayerController().getPlayer(p).saveSettings(false)));
        } else {
            RConst.error("You must be a player to use this command.");
        }
    }

}
