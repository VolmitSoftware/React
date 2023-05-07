package com.volmit.react.core.command;

import com.volmit.react.api.command.RCommand;
import com.volmit.react.api.command.RConst;
import com.volmit.react.core.gui.ColorPickerGUI;
import com.volmit.react.util.J;
import dev.jorel.commandapi.annotations.Alias;
import dev.jorel.commandapi.annotations.Command;
import dev.jorel.commandapi.annotations.Permission;
import dev.jorel.commandapi.annotations.Subcommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.awt.*;

@Command("react")
@Alias({"re", "ract"})
@Permission("react.main")
public class CommandColor implements RCommand {
    @Subcommand("color")
    @Permission("react.color")
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
