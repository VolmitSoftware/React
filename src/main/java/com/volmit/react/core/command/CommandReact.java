package com.volmit.react.core.command;

import com.volmit.react.api.command.RCommand;
import dev.jorel.commandapi.annotations.Alias;
import dev.jorel.commandapi.annotations.Command;
import dev.jorel.commandapi.annotations.Default;
import dev.jorel.commandapi.annotations.Permission;
import org.bukkit.command.CommandSender;

@Command("react")
@Alias({"re", "ract"})
@Permission("react.main")
public class CommandReact implements RCommand {

    @Default
    public static void react(CommandSender sender) {
        sender.sendMessage("--== [ React Help Page ] ==--");
        sender.sendMessage("/react - Show this page");
        sender.sendMessage("/react reload - Reload React");
        sender.sendMessage("/react action - List all actions");
        sender.sendMessage("/react monitor - Opens the monitor");
        sender.sendMessage("/react color - Opens the color picker");
        sender.sendMessage("/react monitorconfig - Opens the monitor config");
    }
}
