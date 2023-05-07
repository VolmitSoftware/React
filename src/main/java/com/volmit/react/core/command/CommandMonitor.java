package com.volmit.react.core.command;

import com.volmit.react.React;
import com.volmit.react.api.command.RCommand;
import com.volmit.react.api.command.RConst;
import com.volmit.react.api.monitor.configuration.MonitorConfiguration;
import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.core.gui.MonitorConfigGUI;
import com.volmit.react.core.gui.SamplerGUI;
import com.volmit.react.util.J;
import dev.jorel.commandapi.annotations.Alias;
import dev.jorel.commandapi.annotations.Command;
import dev.jorel.commandapi.annotations.Permission;
import dev.jorel.commandapi.annotations.Subcommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;


@Command("react")
@Alias({"re", "ract"})
@Permission("react.main")
public class CommandMonitor implements RCommand {

    //    @Alias({"mon", "m"}) //Not implemented by CommandAPI yet, Bigsad
    @Subcommand({"monitor", "mon", "m"})
    @Permission("react.monitor")
    public static void monitor(CommandSender sender) {
        if (sender instanceof Player p) {
            React.instance.getPlayerController().getPlayer(p).toggleActionBar();
            RConst.success("Monitor " + (React.instance.getPlayerController().getPlayer(p).isActionBarMonitoring() ? "Enabled" : "Disabled"));
        } else {
            RConst.error("You must be a player to use this command.");
        }
    }

    @Subcommand({"monitoredit", "monedit", "medit", "med"})
    @Permission("react.monitor.edit")
    public static void monitorConfig(CommandSender sender) {
        if (sender instanceof Player p) {
            J.a(() -> MonitorConfigGUI.editMonitorConfiguration(p, React.instance.getPlayerController().getPlayer(p).getSettings().getMonitorConfiguration(),
                (c) -> React.instance.getPlayerController().getPlayer(p).saveSettings(false)));
        } else {
            RConst.error("You must be a player to use this command.");
        }
    }

}
