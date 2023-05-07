package com.volmit.react.core.command;

import com.volmit.react.api.command.RCommand;
import com.volmit.react.api.command.RConst;
import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.core.gui.SamplerGUI;
import com.volmit.react.util.J;
import dev.jorel.commandapi.annotations.Alias;
import dev.jorel.commandapi.annotations.Command;
import dev.jorel.commandapi.annotations.Default;
import dev.jorel.commandapi.annotations.Permission;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Command("monitor")
@Alias({"mon"})
@Permission("react.monitor")
public class CommandMonitorConfigure implements RCommand {
    @Default
    @Permission("react.monitor")
    public static void monConf(CommandSender sender) {
        if(sender instanceof Player p) {
            J.a(() -> {
                Sampler s = SamplerGUI.pickSampler(p);
                System.out.println("Picked " + (s == null ? "NONE" : s.getId()));
            });
        }
        else {
            RConst.error("You must be a player to use this command.");
        }
    }

}
