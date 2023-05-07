package com.volmit.react.core.command;

import com.volmit.react.React;
import com.volmit.react.api.action.Action;
import com.volmit.react.api.command.RCommand;
import com.volmit.react.api.command.RConst;
import dev.jorel.commandapi.annotations.*;
import dev.jorel.commandapi.annotations.arguments.AMultiLiteralArgument;
import org.bukkit.command.CommandSender;

@Command("react")
@Alias({"re", "ract"})
@Permission("react.main")
public class CommandAction implements RCommand {

    @Subcommand({"action", "act"})
    @Permission("react.actions")
    @Default
    public static void action(CommandSender sender) {
        for (Action<?> i : React.instance.getActionController().getActions().values()) {
            sender.sendMessage(i.getId());
            RConst.success(i.getId());
        }
    }


    @Subcommand({"action", "act"})
    @Permission("react.actions")
    public static void action(CommandSender sender, @AMultiLiteralArgument({"purge-entities", "unknown"}) String args) {
        Action<?> action = React.instance.getActionController().getAction(args);
        action.create()
                .onStart(() -> sender.sendMessage("Started " + action.getId()))
                .onComplete(() -> sender.sendMessage("Completed " + action.getId()))
                .queue();
        RConst.success("Queued " + action.getId());
    }
}
