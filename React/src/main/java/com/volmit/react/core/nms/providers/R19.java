package com.volmit.react.core.nms.providers;

import com.volmit.react.core.nms.NMS;
import net.minecraft.network.protocol.game.PacketPlayOutEntityMetadata;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherRegistry;

import java.util.List;

public class R19 implements NMS {
    @Override
    public List<?> getDataWatcherProperties(Object... objects) {
        List<DataWatcher.b<?>> list = new java.util.ArrayList<>();

        for(Object i : objects) {
            list.add((DataWatcher.b<?>) i);
        }

        return list;
    }

    @Override
    public Object getEntityStateProperty(byte state) {
        return new DataWatcher.b<>(0, DataWatcherRegistry.a, state);
    }

    @Override
    public Object getGravityProperty(boolean gravity) {
        return new DataWatcher.b<>(5, DataWatcherRegistry.j, !gravity);
    }
}
