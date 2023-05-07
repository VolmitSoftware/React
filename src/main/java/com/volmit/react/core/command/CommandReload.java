package com.volmit.react.core.command;

import com.volmit.react.React;
import com.volmit.react.api.command.RCommand;
import dev.jorel.commandapi.annotations.Alias;
import dev.jorel.commandapi.annotations.Command;
import dev.jorel.commandapi.annotations.Permission;
import dev.jorel.commandapi.annotations.Subcommand;
import org.bukkit.command.CommandSender;

@Command("react")
@Alias({"re", "ract"})
@Permission("react.main")
public class CommandReload implements RCommand {
    @Subcommand({"reload", "rl"})
    @Permission("react.reload")
    public static void reload(CommandSender sender) {
        React.instance.reload();
        sender.sendMessage("React Reloaded");
    }
}
