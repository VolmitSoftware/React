package com.volmit.react.content.command.parser;

import art.arcane.edict.api.parser.SelectionParser;
import com.volmit.react.React;
import com.volmit.react.api.action.Action;
import com.volmit.react.core.controller.ActionController;

import java.util.List;

public class ActionParser implements SelectionParser<Action<?>> {
    @Override
    public List<Action<?>> getSelectionOptions() {
        return React.controller(ActionController.class).getActions().all().stream().toList();
    }

    @Override
    public String getName(Action<?> a) {
        return a.getId();
    }
}
