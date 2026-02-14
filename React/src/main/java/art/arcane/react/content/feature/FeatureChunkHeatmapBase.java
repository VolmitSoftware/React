/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package art.arcane.react.content.feature;

import art.arcane.react.React;
import art.arcane.react.api.feature.ReactFeature;
import art.arcane.react.api.rendering.ReactRenderer;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.model.SampledChunk;
import art.arcane.react.util.data.TinyColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

abstract class FeatureChunkHeatmapBase extends ReactFeature implements ReactRenderer {
    private int chunkPixelSize = 5;
    private int mapRadiusChunks = 0;
    private boolean rotateWithPlayer = true;
    private boolean drawCenterMarker = true;
    private boolean drawLabel = true;

    protected FeatureChunkHeatmapBase(String id) {
        super(id);
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

    @Override
    public void render() {
        Player viewer = player();
        if (viewer == null || viewer.getWorld() == null) {
            return;
        }

        clear(backgroundColor());

        Chunk center = viewer.getLocation().getChunk();
        int radius = mapRadiusChunks > 0 ? mapRadiusChunks : Math.max(2, viewer.getWorld().getViewDistance() * 2);
        int zoom = Math.max(1, chunkPixelSize);
        double pixelsPerBlock = zoom / 16D;

        int localX = Math.floorMod(viewer.getLocation().getBlockX(), 16);
        int localZ = Math.floorMod(viewer.getLocation().getBlockZ(), 16);
        double ox = -(localX * pixelsPerBlock);
        double oz = -(localZ * pixelsPerBlock);

        Map<Chunk, Double> score = new HashMap<>();
        double max = 0D;
        double min = Double.MAX_VALUE;

        for (Chunk chunk : viewer.getWorld().getLoadedChunks()) {
            if (!within(center, chunk, radius)) {
                continue;
            }

            double value = Math.max(0D, chunkScore(chunk));
            if (value <= 0D) {
                continue;
            }

            score.put(chunk, value);
            max = Math.max(max, value);
            min = Math.min(min, value);
        }

        double yaw = rotateWithPlayer ? ((-viewer.getLocation().getYaw()) + 180D) : 0D;
        double radians = Math.toRadians(yaw);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);

        for (Map.Entry<Chunk, Double> entry : score.entrySet()) {
            Chunk chunk = entry.getKey();
            double normalized = normalize(entry.getValue(), min, max);
            TinyColor color = colorFor(normalized, entry.getValue());
            int baseX = (chunk.getX() - center.getX()) * zoom;
            int baseZ = (chunk.getZ() - center.getZ()) * zoom;

            for (int dx = 0; dx < zoom; dx++) {
                for (int dz = 0; dz < zoom; dz++) {
                    double a = baseX + dx + ox;
                    double b = baseZ + dz + oz;
                    int rx = rotateWithPlayer ? (int) Math.round((cos * a) - (sin * b)) : (int) Math.round(a);
                    int rz = rotateWithPlayer ? (int) Math.round((sin * a) + (cos * b)) : (int) Math.round(b);
                    set(63 + rx, 63 + rz, color);
                }
            }
        }

        if (drawCenterMarker) {
            TinyColor centerColor = new TinyColor(0, 255, 90);
            set(63, 63, centerColor);
            set(62, 63, centerColor);
            set(64, 63, centerColor);
            set(63, 62, centerColor);
            set(63, 64, centerColor);
        }

        renderOverlay(score, min, max);

        if (drawLabel) {
            String label = mapLabel();
            if (label != null && !label.isBlank()) {
                text(3, 3, label);
            }
        }
    }

    protected TinyColor backgroundColor() {
        return new TinyColor(6, 8, 12);
    }

    protected String mapLabel() {
        return getName();
    }

    protected abstract double chunkScore(Chunk chunk);

    protected TinyColor colorFor(double normalized, double rawScore) {
        return gradient(normalized, new TinyColor(20, 80, 220), new TinyColor(255, 70, 20));
    }

    protected void renderOverlay(Map<Chunk, Double> score, double min, double max) {

    }

    protected double chunkSample(Chunk chunk, String samplerId) {
        var sampler = React.sampler(samplerId);
        return sampler == null ? 0D : Math.max(0D, sampler.sample(chunk));
    }

    protected double chunkTotalScore(Chunk chunk) {
        ObserverController observer = React.controller(ObserverController.class);
        if (observer == null || observer.getSampled() == null) {
            return 0D;
        }

        return observer.getSampled().optionalChunk(chunk).map(SampledChunk::totalScore).orElse(0D);
    }

    protected TinyColor gradient(double normalized, TinyColor low, TinyColor high) {
        double n = Math.max(0D, Math.min(1D, normalized));
        int r = (int) Math.round((low.getColor().getRed() * (1D - n)) + (high.getColor().getRed() * n));
        int g = (int) Math.round((low.getColor().getGreen() * (1D - n)) + (high.getColor().getGreen() * n));
        int b = (int) Math.round((low.getColor().getBlue() * (1D - n)) + (high.getColor().getBlue() * n));
        return new TinyColor(r, g, b);
    }

    protected Pixel projectBlockDelta(Location viewerLocation, Location targetLocation) {
        double dxBlocks = targetLocation.getX() - viewerLocation.getX();
        double dzBlocks = targetLocation.getZ() - viewerLocation.getZ();
        double pixelsPerBlock = Math.max(1, chunkPixelSize) / 16D;
        double a = dxBlocks * pixelsPerBlock;
        double b = dzBlocks * pixelsPerBlock;
        double yaw = rotateWithPlayer ? ((-viewerLocation.getYaw()) + 180D) : 0D;
        double radians = Math.toRadians(yaw);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        int rx = rotateWithPlayer ? (int) Math.round((cos * a) - (sin * b)) : (int) Math.round(a);
        int rz = rotateWithPlayer ? (int) Math.round((sin * a) + (cos * b)) : (int) Math.round(b);
        return new Pixel(63 + rx, 63 + rz);
    }

    private boolean within(Chunk a, Chunk b, int radius) {
        int dx = a.getX() - b.getX();
        int dz = a.getZ() - b.getZ();
        return (dx * dx) + (dz * dz) <= radius * radius;
    }

    private double normalize(double value, double min, double max) {
        if (max <= min + 0.0001D) {
            return value > 0D ? 1D : 0D;
        }

        return Math.max(0D, Math.min(1D, (value - min) / (max - min)));
    }

    protected static final class Pixel {
        private final int x;
        private final int y;

        private Pixel(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int x() {
            return x;
        }

        public int y() {
            return y;
        }
    }
}
