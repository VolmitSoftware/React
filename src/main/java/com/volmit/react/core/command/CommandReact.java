package com.volmit.react.core.command;

import com.volmit.react.util.Command;
import com.volmit.react.util.MortarCommand;
import com.volmit.react.util.MortarSender;

import java.util.List;

public class CommandReact extends MortarCommand {
    @Command
    private CommandMonitor monitor = new CommandMonitor();

    @Command
    private CommandReload reload = new CommandReload();

    @Command
    private CommandAction action = new CommandAction();

    public CommandReact() {
        super("react", "re");
    }

    @Override
    public boolean handle(MortarSender sender, String[] args) {
        printHelp(sender);
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
