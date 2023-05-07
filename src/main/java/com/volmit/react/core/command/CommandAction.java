package com.volmit.react.core.command;

import com.volmit.react.React;
import com.volmit.react.api.action.Action;
import com.volmit.react.api.command.RConst;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.executors.CommandExecutor;
import org.bukkit.command.CommandSender;

public class CommandAction {

    public static void register() {
        CommandAPICommand reactCommand = new CommandAPICommand("react")
                .withPermission("react.main");

        CommandAPICommand actionCommand = new CommandAPICommand("action")
                .withPermission("react.actions")
                .executes((CommandExecutor) (sender, args) -> action(sender, args));

        CommandAPICommand specificActionCommand = new CommandAPICommand("action")
                .withPermission("react.actions")
                .withArguments(new MultiLiteralArgument("purge-entities", "unknown"))
                .executes((CommandExecutor) (sender, args) -> action(sender, (String) args[0]));

        reactCommand.withSubcommand(actionCommand).withSubcommand(specificActionCommand).register();
    }

    public static void action(CommandSender sender, Object[] args) {
        for (Action<?> i : React.instance.getActionController().getActions().values()) {
            sender.sendMessage(i.getId());
            RConst.success(i.getId());
        }
    }

    public static void action(CommandSender sender, String args) {
        Action<?> action = React.instance.getActionController().getAction(args);
        action.create()
                .onStart(() -> sender.sendMessage("Started " + action.getId()))
                .onComplete(() -> sender.sendMessage("Completed " + action.getId()))
                .queue();
        RConst.success("Queued " + action.getId());
    }
}
