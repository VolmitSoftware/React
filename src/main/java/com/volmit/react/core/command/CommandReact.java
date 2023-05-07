package com.volmit.react.core.command;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.executors.CommandExecutor;
import org.bukkit.command.CommandSender;

public class CommandReact {

    private static CommandAPICommand reactCommand;

    public static void register() {
        reactCommand = new CommandAPICommand("react")
                .withAliases("re", "ract")
                .withPermission("react.main")
                .executes((CommandExecutor) (sender, args) -> react(sender, args));

        reactCommand.register();

        CommandAction.register();
        CommandMonitor.register();
        CommandReload.register();
    }

    public static void addSubcommands(CommandAPICommand... subcommands) {
        for (CommandAPICommand subcommand : subcommands) {
            reactCommand.withSubcommand(subcommand);
        }
    }

    public static void react(CommandSender sender, Object[] args) {
        sender.sendMessage("--== [ React Help Page ] ==--");
        sender.sendMessage("/react - Show this page");
        sender.sendMessage("/react reload - Reload React");
        sender.sendMessage("/react action - List all actions");
        sender.sendMessage("/react monitor - Opens the monitor");
        sender.sendMessage("/react color - Opens the color picker");
        sender.sendMessage("/react monitorconfig - Opens the monitor config");
    }
}
