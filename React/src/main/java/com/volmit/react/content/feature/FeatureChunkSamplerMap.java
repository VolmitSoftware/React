package com.volmit.react.content.feature;

import com.volmit.react.React;
import com.volmit.react.api.feature.ReactFeature;
import com.volmit.react.api.rendering.ReactRenderer;
import com.volmit.react.core.controller.ObserverController;
import com.volmit.react.model.SampledChunk;
import com.volmit.react.util.data.TinyColor;
import org.bukkit.Chunk;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;

public class FeatureChunkSamplerMap extends ReactFeature implements Listener, ReactRenderer {
    public static final String ID = "chunk-sampler-map";

    public FeatureChunkSamplerMap() {
        super(ID);
    }

    public double distanceSquared(Chunk a, Chunk b) {
        return Math.pow(a.getX() - b.getX(), 2) + Math.pow(a.getZ() - b.getZ(), 2);
    }

    public boolean within(Chunk a, Chunk b, double radius) {
        return distanceSquared(a, b) <= Math.pow(radius, 2);
    }

    @Override
    public void render() {
        int zoom = 5;
        int bpp = (16 / zoom);
        clear(new TinyColor(0, 0, 0));
        Chunk center = player().getLocation().getChunk();

        int ox = -((player().getLocation().getBlockX() % 16) / bpp);
        int oz = -((player().getLocation().getBlockZ() % 16) / bpp);
        Map<Chunk, Double> score = new HashMap<>();
        for (Chunk i : player().getWorld().getLoadedChunks()) {
            if (within(i, center, player().getWorld().getViewDistance() * 2)) {
                SampledChunk sc = React.controller(ObserverController.class).getSampled().getChunk(i);
                score.put(i, sc.totalScore());
            }
        }

        double max = score.values().stream().mapToDouble(i -> i).max().orElse(0);
        double min = score.values().stream().mapToDouble(i -> i).min().orElse(0);
        double yaw = (-player().getLocation().getYaw()) + 180;

        score.replaceAll((k, v) -> (v - min) / (max - min));

        for (Chunk i : player().getWorld().getLoadedChunks()) {
            if (within(i, center, player().getWorld().getViewDistance() * 2)) {
                int x = (i.getX() - center.getX()) * zoom;
                int z = (i.getZ() - center.getZ()) * zoom;

                TinyColor c = new TinyColor((int) (score.get(i) * 255), (int) (score.get(i) * 255), (int) (score.get(i) * 255));

                for (int dx = 0; dx < zoom; dx++) {
                    for (int dz = 0; dz < zoom; dz++) {
                        // calculate the position of this pixel relative to the player
                        int a = x + dx + ox;
                        int b = z + dz + oz;

                        // Rotate each pixel in the chunk
                        int rotatedA = (int) (Math.cos(Math.toRadians(yaw)) * a - Math.sin(Math.toRadians(yaw)) * b);
                        int rotatedB = (int) (Math.sin(Math.toRadians(yaw)) * a + Math.cos(Math.toRadians(yaw)) * b);

                        // Draw the rotated pixel
                        set(63 + rotatedA, 63 + rotatedB, c);
                        set(63 + rotatedA + 1, 63 + rotatedB + 1, c);
                        set(63 + rotatedA - 1, 63 + rotatedB + 1, c);
                        set(63 + rotatedA + 1, 63 + rotatedB - 1, c);
                        set(63 + rotatedA, 63 + rotatedB - 1, c);
                        set(63 + rotatedA, 63 + rotatedB + 1, c);
                        set(63 + rotatedA - 1, 63 + rotatedB, c);
                        set(63 + rotatedA + 1, 63 + rotatedB, c);
                    }
                }
            }
        }

        set(63, 63, new TinyColor(0, 255, 0));
    }

    @Override
    public void onActivate() {

    }


    @Override
    public void onDeactivate() {

    }

    @Override
    public int getTickInterval() {
        return -1;
    }

    @Override
    public void onTick() {

    }
}

