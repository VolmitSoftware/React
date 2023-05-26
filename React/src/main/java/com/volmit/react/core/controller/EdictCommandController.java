package com.volmit.react.core.controller;

import art.arcane.edict.Edict;
import com.volmit.react.React;
import com.volmit.react.util.plugin.IController;

public class EdictCommandController implements IController {
    private static final String PKG = "com.volmit.react.";
    private static final String CORE = PKG + "core.";
    private static final String CONTENT = PKG + "content.";
    private transient Edict edict;

    @Override
    public void start() {

    }

    @Override
    public boolean autoRegister() {
        return false;
    }

    @Override
    public void stop() {

    }

    @Override
    public void postStart() {
        edict = Edict.builder()
            .parserPackage(CONTENT + "command.parser")
            .contextResolverPackage(CONTENT + "command.context")
            .build().registerPackage(CONTENT + "command")
            .registerPackage(CONTENT + "action")
            .registerPackage(CONTENT + "feature")
            .registerPackage(CONTENT + "tweak")
            .registerPackage(CORE + "controller")
            .initialize(React.instance.getCommand("react"));
    }

    @Override
    public String getId() {
        return "command";
    }
}
