package com.volmit.react.testedict.command;

import art.arcane.edict.Edict;
import art.arcane.edict.api.context.EdictContext;

public class Commands {
    @Edict.Command("/react echo")
    public static void echo(@Edict.Default("Hello World! (was default)") String message) {
        if(message == null)
        {
            message = "Hello World! (was null)";
        }

        EdictContext.get().getSender().sendMessage(message);
    }
}
