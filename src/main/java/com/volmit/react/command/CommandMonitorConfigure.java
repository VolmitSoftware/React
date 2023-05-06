package com.volmit.react.command;

import com.volmit.react.React;
import com.volmit.react.api.sampler.Sampler;
import com.volmit.react.gui.SamplerGUI;
import com.volmit.react.util.J;
import com.volmit.react.util.MortarCommand;
import com.volmit.react.util.MortarSender;

import java.util.List;

public class CommandMonitorConfigure extends MortarCommand {
    public CommandMonitorConfigure() {
        super("edit", "e");
    }

    @Override
    public boolean handle(MortarSender sender, String[] args) {
        if(sender.isPlayer()) {
            J.a(() -> {
                Sampler s = SamplerGUI.pickSampler(sender.player());
                System.out.println("Picked " + (s == null ? "NONE" : s.getId()));
            });
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
