package com.volmit.react.core.command;

import com.volmit.react.React;
import com.volmit.react.api.command.RCommand;
import com.volmit.react.api.command.RConst;
import dev.jorel.commandapi.annotations.Alias;
import dev.jorel.commandapi.annotations.Command;
import dev.jorel.commandapi.annotations.Default;
import dev.jorel.commandapi.annotations.Permission;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Command("editmonitor")
@Alias({"editmon", "emon"})
@Permission("react.monitor.edit")
public class CommandMonitor implements RCommand {
    @Default
    @Permission("react.monitor.edit")
    public static void monitor(CommandSender sender) {
        if (sender instanceof Player p) {
            React.instance.getPlayerController().getPlayer(p).toggleActionBar();
            RConst.success("Monitor " + (React.instance.getPlayerController().getPlayer(p).isActionBarMonitoring() ? "Enabled" : "Disabled"));
        } else {
            RConst.error("You must be a player to use this command.");
        }
    }

}
