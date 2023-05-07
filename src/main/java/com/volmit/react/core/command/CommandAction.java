package com.volmit.react.core.command;

import com.volmit.react.React;
import com.volmit.react.api.action.Action;
import com.volmit.react.api.command.RCommand;
import com.volmit.react.api.command.RConst;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.executors.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Map;
public class CommandAction implements CommandExecutor, RCommand {

    public CommandAction() {
        new CommandAPICommand("action")
                .withAliases("act", "a")
                .withPermission("react.action")
                .withArguments(new GreedyStringArgument("args"))
                .executes(this)
                .register();
    }

    @Override
    public void run(CommandSender sender, Object[] args) {
        String argString = (String) args[0];
        String[] argsArray = argString.split(" ");

        if (argsArray.length == 0) {
            for (Action<?> i : React.instance.getActionController().getActions().values()) {
                RConst.success(i.getId());
            }
        } else {
            Action<?> action = React.instance.getActionController().getAction(argsArray[0]);
            action.create()
                    .onStart(() -> sender.sendMessage("Started " + action.getId()))
                    .onComplete(() -> sender.sendMessage("Completed " + action.getId()))
                    .queue();
            RConst.success("Queued " + action.getId());
        }
    }

    public static String[] getActionKeys() {
        Map<String, Action<?>> actions = React.instance.getActionController().getActions();
        return actions.keySet().toArray(new String[0]);
    }
}