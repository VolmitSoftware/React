package com.volmit.react.core.command;

import com.volmit.react.React;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.executors.CommandExecutor;
import org.bukkit.command.CommandSender;

public class CommandReload {

    public static void register() {
        CommandAPICommand reloadCommand = new CommandAPICommand("reload")
                .withAliases("rl")
                .withPermission("react.reload")
                .executes((CommandExecutor) (sender, args) -> reload(sender));

        CommandReact.addSubcommands(reloadCommand);
    }

    public static void reload(CommandSender sender) {
        React.instance.reload();
        sender.sendMessage("React Reloaded");
    }
}
