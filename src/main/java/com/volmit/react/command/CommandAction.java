package com.volmit.react.command;

import com.volmit.react.React;
import com.volmit.react.api.action.Action;
import com.volmit.react.util.MortarCommand;
import com.volmit.react.util.MortarSender;

import java.util.List;

public class CommandAction extends MortarCommand {
    public CommandAction() {
        super("action", "act");
    }

    @Override
    public boolean handle(MortarSender sender, String[] args) {
        if(args.length == 0) {
            for(Action<?> i : React.instance.getActionController().getActions().values())
            {
                sender.sendMessage(i.getId());
            }
            return true;
        }

        else {
            Action<?> action = React.instance.getActionController().getAction(args[0]);
            action.create()
                .onStart(() -> sender.sendMessage("Started " + action.getId()))
                .onComplete(() -> sender.sendMessage("Completed " + action.getId()))
                .queue();
            sender.sendMessage("Queued " + action.getId());
        }

        return true;
    }

    @Override
    public void addTabOptions(MortarSender sender, String[] args, List<String> list) {

    }

    @Override
    protected String getArgsUsage() {
        return null;
    }
}
