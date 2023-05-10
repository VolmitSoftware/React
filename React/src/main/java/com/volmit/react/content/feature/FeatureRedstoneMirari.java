package com.volmit.react.content.feature;

import com.volmit.react.React;
import com.volmit.react.api.feature.ReactFeature;
import com.volmit.react.core.controller.ObserverController;
import com.volmit.react.core.controller.data.ChunkStatistic;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.Listener;


public class FeatureRedstoneMirari extends ReactFeature implements Listener {
    public static final String ID = "redstone-mirari";
    private ObserverController observerController;
    private int tickIntervalMS = 250;

    public FeatureRedstoneMirari() {
        super(ID);
    }

    @Override
    public void onActivate() {
        React.instance.registerListener(this);
        this.observerController = React.instance.getObserverController();
    }

    @Override
    public void onDeactivate() {
        React.instance.unregisterListener(this);
    }

    @Override
    public int getTickInterval() {
        return tickIntervalMS;
    }

    @Override
    public void onTick() {
        for (String worldName : observerController.getWorldStatistics().keySet()) {
            for (Chunk chunk : observerController.getWorldStatistics().get(worldName).keySet()) {
                ChunkStatistic chunkStatistic = observerController.getWorldStatistics().get(worldName).get(chunk);
                if ((chunkStatistic.getRedstoneInteractions()/2 + chunkStatistic.getPistonInteractions()/2) > 10) {
                    //this is where i would do something if i had any idea what i was doing with the features.
                    Bukkit.broadcastMessage("Redstone Mirari: " + chunkStatistic.getRedstoneInteractions()/2 + " redstone interactions and " + chunkStatistic.getPistonInteractions()/2 + " piston interactions in chunk " + chunk.getX() + ", " + chunk.getZ() + " in world " + worldName);
                }
                // Clear the statistics for the next tick
                chunkStatistic.setRedstoneInteractions(0);
                chunkStatistic.setPistonInteractions(0);
            }
        }
    }


    @EqualsAndHashCode
    @AllArgsConstructor
    @Data
    public static class IBlock {
        private final World world;
        private final int x;
        private final int y;
        private final int z;

        public Block block() {
            return world.getBlockAt(x, y, z);
        }

        public FeatureRedstoneMirari.IChunk chunk() {
            return new FeatureRedstoneMirari.IChunk(world, x >> 4, z >> 4);
        }
    }

    @EqualsAndHashCode
    @AllArgsConstructor
    @Data
    public static class IChunk {
        private final World world;
        private final int x;
        private final int z;

        public Chunk chunk() {
            return world.getChunkAt(x, z);
        }
    }

}
