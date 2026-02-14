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

package art.arcane.react.content.action;

import art.arcane.react.React;
import art.arcane.react.api.action.ActionParams;
import art.arcane.react.api.action.ActionTicket;
import art.arcane.react.api.action.ReactAction;
import art.arcane.react.core.controller.ActionController;
import art.arcane.react.core.controller.ObserverController;
import art.arcane.react.model.SampledChunk;
import art.arcane.react.model.SampledWorld;
import art.arcane.volmlib.util.format.Form;
import art.arcane.react.util.scheduling.J;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActionPrewarmCriticalChunks extends ReactAction<ActionPrewarmCriticalChunks.Params> {
    public static final String ID = "action-prewarm-critical-chunks";
    public static final String SHORT = "apcc";

    public ActionPrewarmCriticalChunks() {
        super(ID);
    }

    @Override
    public String getCompletedMessage(ActionTicket<Params> ticket) {
        Params p = ticket.getParams();
        return "Prewarmed " + p.getChunksWarmed() + " chunks (" + p.getChunksLoaded() + " newly loaded) in " + Form.duration(ticket.getDuration(), 1);
    }

    @Override
    public void workOn(ActionTicket<Params> ticket) {
        Params params = ticket.getParams();
        if (!params.isPrepared()) {
            params.setQueue(J.sResult(() -> buildQueueSync(params)));
            params.setPrepared(true);
            ticket.setWork(0);
            ticket.setTotalWork(Math.max(1, params.getQueue().size()));
        }

        if (params.getQueue().isEmpty()) {
            ticket.setCount(params.getChunksWarmed());
            ticket.complete();
            return;
        }

        int chunkBudget = Math.max(1, React.controller(ActionController.class).getActionSpeedMultiplier() / 16);
        int worked = 0;
        while (worked < chunkBudget && !params.getQueue().isEmpty()) {
            ChunkRef target = params.getQueue().remove(0);
            PrewarmResult result = J.sResult(() -> prewarmSync(target, params));
            if (result != null && result.warmed()) {
                params.setChunksWarmed(params.getChunksWarmed() + 1);
                if (result.newlyLoaded()) {
                    params.setChunksLoaded(params.getChunksLoaded() + 1);
                }
            }

            worked++;
        }

        ticket.setWork(Math.min(ticket.getTotalWork(), ticket.getWork() + worked));
        ticket.setCount(params.getChunksWarmed());

        if (params.getQueue().isEmpty()) {
            ticket.complete();
        }
    }

    @Override
    public Params getDefaultParams() {
        return Params.builder().build();
    }

    @Override
    public void onInit() {

    }

    private List<ChunkRef> buildQueueSync(Params params) {
        Map<ChunkRef, Double> weighted = new HashMap<>();
        ObserverController observer = React.controller(ObserverController.class);
        if (observer != null && observer.getSampled() != null) {
            for (SampledWorld sampledWorld : observer.getSampled().getWorlds().values()) {
                World world = sampledWorld.getWorld();
                if (world == null) {
                    continue;
                }

                if (params.getWorld() != null && !params.getWorld().isBlank() && !world.getName().equalsIgnoreCase(params.getWorld())) {
                    continue;
                }

                for (SampledChunk sampledChunk : sampledWorld.getChunks().values()) {
                    Chunk chunk = sampledChunk.getChunk();
                    if (chunk == null || chunk.getWorld() == null) {
                        continue;
                    }

                    double score = sampledChunk.totalScore();
                    if (score <= 0D) {
                        continue;
                    }

                    addWeighted(weighted, ChunkRef.of(chunk), score);
                    for (int dx = -params.getNeighborRadius(); dx <= params.getNeighborRadius(); dx++) {
                        for (int dz = -params.getNeighborRadius(); dz <= params.getNeighborRadius(); dz++) {
                            if (dx == 0 && dz == 0) {
                                continue;
                            }

                            double dist = Math.sqrt((dx * dx) + (dz * dz));
                            addWeighted(weighted, new ChunkRef(world.getName(), chunk.getX() + dx, chunk.getZ() + dz), score * (0.7D / (1D + dist)));
                        }
                    }
                }
            }
        }

        if (params.isIncludePlayerChunks()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                World world = player.getWorld();
                if (world == null) {
                    continue;
                }

                if (params.getWorld() != null && !params.getWorld().isBlank() && !world.getName().equalsIgnoreCase(params.getWorld())) {
                    continue;
                }

                Chunk origin = player.getLocation().getChunk();
                for (int dx = -params.getPlayerChunkRadius(); dx <= params.getPlayerChunkRadius(); dx++) {
                    for (int dz = -params.getPlayerChunkRadius(); dz <= params.getPlayerChunkRadius(); dz++) {
                        double dist = Math.sqrt((dx * dx) + (dz * dz));
                        addWeighted(weighted, new ChunkRef(world.getName(), origin.getX() + dx, origin.getZ() + dz), 180D / (1D + dist));
                    }
                }
            }
        }

        List<Map.Entry<ChunkRef, Double>> sorted = new ArrayList<>(weighted.entrySet());
        sorted.sort(Map.Entry.<ChunkRef, Double>comparingByValue(Comparator.reverseOrder()));

        int limit = Math.max(1, params.getMaxChunks());
        List<ChunkRef> refs = new ArrayList<>();
        for (Map.Entry<ChunkRef, Double> entry : sorted) {
            refs.add(entry.getKey());
            if (refs.size() >= limit) {
                break;
            }
        }

        return refs;
    }

    private void addWeighted(Map<ChunkRef, Double> weighted, ChunkRef ref, double value) {
        if (value <= 0D) {
            return;
        }

        weighted.merge(ref, value, Math::max);
    }

    private PrewarmResult prewarmSync(ChunkRef ref, Params params) {
        World world = Bukkit.getWorld(ref.world());
        if (world == null) {
            return new PrewarmResult(false, false);
        }

        boolean wasLoaded = world.isChunkLoaded(ref.x(), ref.z());
        if (!wasLoaded) {
            world.loadChunk(ref.x(), ref.z(), params.isGenerateMissingChunks());
            if (!world.isChunkLoaded(ref.x(), ref.z())) {
                return new PrewarmResult(false, false);
            }
        }

        Chunk chunk = world.getChunkAt(ref.x(), ref.z());
        if (params.isTouchChunkSnapshot()) {
            chunk.getChunkSnapshot(false, false, false);
        }

        // Touching entities helps prime internal chunk/entity structures.
        chunk.getEntities();

        return new PrewarmResult(true, !wasLoaded);
    }

    private record ChunkRef(String world, int x, int z) {
        private static ChunkRef of(Chunk chunk) {
            return new ChunkRef(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        }
    }

    private record PrewarmResult(boolean warmed, boolean newlyLoaded) {
    }

    @Builder
    @Data
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Params implements ActionParams {
        private String world;
        @Builder.Default
        private int maxChunks = 40;
        @Builder.Default
        private int neighborRadius = 1;
        @Builder.Default
        private boolean includePlayerChunks = true;
        @Builder.Default
        private int playerChunkRadius = 1;
        @Builder.Default
        private boolean generateMissingChunks = true;
        @Builder.Default
        private boolean touchChunkSnapshot = true;
        @Builder.Default
        private transient boolean prepared = false;
        @Builder.Default
        private transient List<ChunkRef> queue = new ArrayList<>();
        @Builder.Default
        private transient int chunksWarmed = 0;
        @Builder.Default
        private transient int chunksLoaded = 0;

        public Params withWorld(World world) {
            if (world != null) {
                this.world = world.getName();
            }
            return this;
        }
    }
}
