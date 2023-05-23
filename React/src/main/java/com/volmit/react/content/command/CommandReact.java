package com.volmit.react.content.command;

import art.arcane.edict.Edict;
import art.arcane.edict.api.context.EdictContext;
import com.volmit.react.React;

public class CommandReact {
    @Edict.Command("/react version")
    public static void version() {
        EdictContext.get().getSender().sendMessage("React " + React.instance.getDescription().getVersion());
    }

    @Edict.Command("/react reload")
    public static void reload() {
        EdictContext.get().getSender().sendMessage("Reloading React");
        React.instance.reload();
        EdictContext.get().getSender().sendMessage("React v" + React.instance.getDescription().getVersion() + " Reloaded!");
    }
}
