package com.volmit.react.command;

import com.volmit.react.React;
import com.volmit.react.util.MortarCommand;
import com.volmit.react.util.MortarSender;

import java.util.List;

public class CommandReload extends MortarCommand {
    public CommandReload() {
        super("reload");
    }

    @Override
    public boolean handle(MortarSender sender, String[] args) {
        React.instance.reload();
        sender.sendMessage("React Reloaded");
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
