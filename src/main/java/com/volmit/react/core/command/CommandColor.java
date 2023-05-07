package com.volmit.react.core.command;

import com.volmit.react.api.command.RCommand;
import com.volmit.react.api.command.RConst;
import com.volmit.react.core.gui.ColorPickerGUI;
import com.volmit.react.util.J;
import dev.jorel.commandapi.annotations.Alias;
import dev.jorel.commandapi.annotations.Command;
import dev.jorel.commandapi.annotations.Default;
import dev.jorel.commandapi.annotations.Permission;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.awt.*;

@Command("editmonitor")
@Alias({"editmon", "emon"})
@Permission("react.monitor.edit")
public class CommandColor implements RCommand {
    @Default
    @Permission("react.monitor.edit")
    public static void monitor(CommandSender sender) {
        if (sender instanceof Player p) {
            J.a(() -> {
                Color color = ColorPickerGUI.pickColor(p);
                sender.sendMessage("You picked " + color.getRed() + ", " + color.getGreen() + ", " + color.getBlue());
            });
        } else {
            RConst.error("You must be a player to use this command.");
        }
    }

}
