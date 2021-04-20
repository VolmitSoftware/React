package com.volmit.react.api;

import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapView;
import primal.lang.collection.GList;

public interface IPapyrus {
    MapView getView();

    BufferedFrame getFrameBuffer();

    void addRenderer(IRenderer renderer);

    void clearRenderers();

    GList<IRenderer> getRenderers();

    void removeRenderer(IRenderer renderer);

    void destroy();

    ItemStack makeMapItem();
}
