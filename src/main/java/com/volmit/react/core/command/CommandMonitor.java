package com.volmit.react.core.command;

import com.volmit.react.React;
import com.volmit.react.util.Command;
import com.volmit.react.util.MortarCommand;
import com.volmit.react.util.MortarSender;

import java.util.List;

public class CommandMonitor extends MortarCommand {
    @Command
    private CommandMonitorConfigure configure = new CommandMonitorConfigure();

    public CommandMonitor() {
        super("monitor", "mon");
    }

    @Override
    public boolean handle(MortarSender sender, String[] args) {
        if(sender.isPlayer()) {
            React.instance.getPlayerController().getPlayer(sender.player()).toggleActionBar();
            sender.sendMessage("Monitor " + (React.instance.getPlayerController().getPlayer(sender.player()).isActionBarMonitoring() ? "Enabled" : "Disabled"));
        }

        else {
            sender.sendMessage("You must be a player to use this command.");
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
