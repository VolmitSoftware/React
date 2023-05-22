package com.volmit.react.api.rendering;

import com.volmit.react.api.rendering.ReactRenderer;
import com.volmit.react.util.data.TinyColor;

public class RendererUnknown implements ReactRenderer {
    public static final String ID = "unknown";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void render() {
        clear(new TinyColor(0));
    }
}
