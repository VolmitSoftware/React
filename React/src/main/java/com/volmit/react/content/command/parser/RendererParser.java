package com.volmit.react.content.command.parser;

import art.arcane.edict.api.parser.SelectionParser;
import com.volmit.react.React;
import com.volmit.react.api.rendering.ReactRenderer;
import com.volmit.react.core.controller.MapController;

import java.util.List;

public class RendererParser implements SelectionParser<ReactRenderer> {
    @Override
    public List<ReactRenderer> getSelectionOptions() {
        return React.controller(MapController.class).getRenderers().values().stream().toList();
    }

    @Override
    public String getName(ReactRenderer renderer) {
        return renderer.getId();
    }
}
