package com.volmit.react.core.nms.providers;

import com.volmit.react.core.nms.NMS;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherRegistry;

import java.util.List;

public class R19 implements NMS {
    @Override
    public Object getEntityMetadataPacket(int eid, List<Object> items) {
        return null;
    }

    @Override
    public Object getGravityProperty(boolean gravity) {
        return null;
    }

    @Override
    public Object getEntityStateProperty(byte state) {
        return new DataWatcher.b<>(0, DataWatcherRegistry.a, state);
    }
}
