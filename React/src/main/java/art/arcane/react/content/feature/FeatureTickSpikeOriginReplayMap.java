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
import art.arcane.react.content.sampler.SamplerTickTime;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.model.SampledChunk;
import art.arcane.react.util.data.TinyColor;
import art.arcane.react.util.scheduling.J;
import org.bukkit.Chunk;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FeatureTickSpikeOriginReplayMap extends FeatureChunkHeatmapBase {
    public static final String ID = "tick-spike-origin-replay-map";
    private int tickIntervalMS = 250;
    private double spikeThresholdMS = 50;
    private int spikeCaptureCooldownMS = 350;
    private int captureRadiusChunks = 3;
    private int maxTrackedChunks = 4096;
    private int staleChunkMS = 120000;
    private int decayEveryMS = 500;
    private double decayFactor = 0.90;
    private double minimumHeat = 0.15;
    private transient Map<ChunkKey, HeatCell> replayHeat = new ConcurrentHashMap<>();
    private transient volatile long lastCaptureMS;
    private transient volatile long lastDecayMS;

    public FeatureTickSpikeOriginReplayMap() {
        super(ID);
    }

    @Override
    public void onActivate() {
        replayHeat = new ConcurrentHashMap<>();
        long now = System.currentTimeMillis();
        lastCaptureMS = now;
        lastDecayMS = now;
    }

    @Override
    public void onDeactivate() {
        replayHeat.clear();
    }

    @Override
    public int getTickInterval() {
        return tickIntervalMS;
    }

    @Override
    public void onTick() {
        long now = System.currentTimeMillis();
        double tickMS = tickTime();

        if (tickMS >= spikeThresholdMS && now - lastCaptureMS >= spikeCaptureCooldownMS) {
            lastCaptureMS = now;
            J.s(() -> captureSpike(now, tickMS));
        }

        if (now - lastDecayMS >= decayEveryMS) {
            lastDecayMS = now;
            decay(now);
        }
    }

    @Override
    protected String mapLabel() {
        return "Tick Spike Replay";
    }

    @Override
    protected TinyColor backgroundColor() {
        return new TinyColor(18, 9, 5);
    }

    @Override
    protected double chunkScore(Chunk chunk) {
        ChunkKey key = new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        HeatCell cell = replayHeat.get(key);
        return cell == null ? 0D : Math.max(0D, cell.heat);
    }

    @Override
    protected TinyColor colorFor(double normalized, double rawScore) {
        if (normalized < 0.5D) {
            return gradient(normalized * 2D, new TinyColor(70, 40, 0), new TinyColor(255, 120, 10));
        }

        return gradient((normalized - 0.5D) * 2D, new TinyColor(255, 120, 10), new TinyColor(255, 240, 180));
    }

    private void captureSpike(long now, double tickMS) {
        ObserverController observer = React.controller(ObserverController.class);
        if (observer == null) {
            return;
        }

        SampledChunk worst = observer.absoluteWorst();
        if (worst == null || worst.getChunk() == null || worst.getChunk().getWorld() == null) {
            return;
        }

        Chunk origin = worst.getChunk();
        int radius = Math.max(0, captureRadiusChunks);
        double spikePressure = Math.max(1D, tickMS - spikeThresholdMS + 1D);

        for (Chunk chunk : origin.getWorld().getLoadedChunks()) {
            int dx = chunk.getX() - origin.getX();
            int dz = chunk.getZ() - origin.getZ();
            if (Math.abs(dx) > radius || Math.abs(dz) > radius) {
                continue;
            }

            double distance = Math.sqrt((dx * dx) + (dz * dz));
            double localScore = Math.max(0D, chunkTotalScore(chunk));
            double distanceWeight = 1D / (1D + distance);
            double scoreWeight = 1D + (Math.min(localScore, 250D) / 120D);
            addHeat(chunk, spikePressure * distanceWeight * scoreWeight, now);
        }

        addHeat(origin, spikePressure * 1.8D, now);
    }

    private void addHeat(Chunk chunk, double heat, long now) {
        if (chunk == null || chunk.getWorld() == null || heat <= 0D) {
            return;
        }

        ChunkKey key = new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        HeatCell cell = replayHeat.computeIfAbsent(key, k -> replayHeat.size() >= maxTrackedChunks ? null : new HeatCell());
        if (cell == null) {
            return;
        }

        cell.heat = Math.min(10000D, cell.heat + heat);
        cell.lastSeenMS = now;
    }

    private void decay(long now) {
        for (Map.Entry<ChunkKey, HeatCell> entry : replayHeat.entrySet()) {
            HeatCell cell = entry.getValue();
            cell.heat *= decayFactor;

            if (cell.heat < minimumHeat || now - cell.lastSeenMS > staleChunkMS) {
                replayHeat.remove(entry.getKey(), cell);
            }
        }
    }

    private double tickTime() {
        var sampler = React.sampler(SamplerTickTime.ID);
        return sampler == null ? 0D : sampler.sample();
    }

    private static final class HeatCell {
        private double heat = 0D;
        private long lastSeenMS = 0L;
    }

    private static final class ChunkKey {
        private final UUID world;
        private final int x;
        private final int z;

        private ChunkKey(UUID world, int x, int z) {
            this.world = world;
            this.x = x;
            this.z = z;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }

            if (!(object instanceof ChunkKey key)) {
                return false;
            }

            return x == key.x && z == key.z && world.equals(key.world);
        }

        @Override
        public int hashCode() {
            int result = world.hashCode();
            result = 31 * result + x;
            result = 31 * result + z;
            return result;
        }
    }
}
