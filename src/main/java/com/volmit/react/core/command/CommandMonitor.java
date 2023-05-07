package com.volmit.react.core.command;

import com.volmit.react.React;
import com.volmit.react.api.command.RCommand;
import com.volmit.react.api.command.RConst;
import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.core.gui.SamplerGUI;
import com.volmit.react.util.J;
import dev.jorel.commandapi.annotations.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;


@Command("react")
@Alias({"re", "ract"})
@Permission("react.main")
public class CommandMonitor implements RCommand {


    @Subcommand("monitor")
    @Permission("react.monitor")    public static void monitor(CommandSender sender) {
        if (sender instanceof Player p) {
            React.instance.getPlayerController().getPlayer(p).toggleActionBar();
            RConst.success("Monitor " + (React.instance.getPlayerController().getPlayer(p).isActionBarMonitoring() ? "Enabled" : "Disabled"));
        } else {
            RConst.error("You must be a player to use this command.");
        }
    }


    @Subcommand("monitoredit")
    @Permission("react.monitor.edit")
    public static void monConf(CommandSender sender) {
        if (sender instanceof Player p) {
            J.a(() -> {
                Sampler s = SamplerGUI.pickSampler(p);
                System.out.println("Picked " + (s == null ? "NONE" : s.getId()));
            });
        } else {
            RConst.error("You must be a player to use this command.");
        }
    }

}
