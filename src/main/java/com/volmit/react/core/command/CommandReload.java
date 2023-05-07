package com.volmit.react.core.command;

import com.volmit.react.React;
import com.volmit.react.api.command.RCommand;
import dev.jorel.commandapi.annotations.Alias;
import dev.jorel.commandapi.annotations.Command;
import dev.jorel.commandapi.annotations.Default;
import dev.jorel.commandapi.annotations.Permission;
import org.bukkit.command.CommandSender;

@Command("reload")
@Alias({"rl", "restart"})
@Permission("react.reload")
public class CommandReload implements RCommand {
    @Default
    @Permission("react.reload")
    public static void reload(CommandSender sender) {
        React.instance.reload();
        sender.sendMessage("React Reloaded");
    }
}
