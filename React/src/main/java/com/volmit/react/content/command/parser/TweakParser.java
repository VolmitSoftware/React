package com.volmit.react.content.command.parser;

import art.arcane.edict.api.parser.SelectionParser;
import com.volmit.react.React;
import com.volmit.react.api.tweak.Tweak;
import com.volmit.react.core.controller.TweakController;

import java.util.List;

public class TweakParser implements SelectionParser<Tweak> {
    @Override
    public List<Tweak> getSelectionOptions() {
        return React.controller(TweakController.class).getTweaks().all().stream().toList();
    }

    @Override
    public String getName(Tweak tweak) {
        return tweak.getId();
    }
}
