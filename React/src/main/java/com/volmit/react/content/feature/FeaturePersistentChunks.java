package com.volmit.react.content.feature;

import com.volmit.react.React;
import com.volmit.react.api.feature.ReactFeature;
import com.volmit.react.core.controller.EntityController;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.*;

public class FeaturePersistentChunks extends ReactFeature implements Listener {
    public static final String ID = "persistent-chunks";
    private int maxChunksPerPlayer = 10;
    private long maxChunkKeepaliveTimeMS = 60000; // One minute in milliseconds
    private int tickTimeMS = 1000; // One second in milliseconds
    private transient HashMap<UUID, LinkedList<TrackedChunk>> trackedChunks;

    public FeaturePersistentChunks() {
        super(ID);
    }

    @Override
    public void onActivate() {
        React.controller(EntityController.class).registerEntityTickListener(EntityType.PLAYER, (e) -> onPlayer((Player) e));
        trackedChunks = new HashMap<>();
    }

    public void onPlayer(Player player) {
        Chunk currentChunk = player.getLocation().getChunk();
        LinkedList<TrackedChunk> playerTrackedChunks = trackedChunks.getOrDefault(player.getUniqueId(), new LinkedList<>());
        if (playerTrackedChunks.stream().noneMatch(tc -> tc.chunkX == currentChunk.getX() && tc.chunkZ == currentChunk.getZ() && tc.worldName.equals(currentChunk.getWorld().getName()))) {
            if (playerTrackedChunks.size() >= maxChunksPerPlayer) {
                TrackedChunk oldestChunk = playerTrackedChunks.removeFirst(); // Remove the oldest chunk if max is reached
                Bukkit.getWorld(oldestChunk.worldName).setChunkForceLoaded(oldestChunk.chunkX, oldestChunk.chunkZ, false); // Unforce load the oldest chunk
            }
            Bukkit.getWorld(currentChunk.getWorld().getName()).setChunkForceLoaded(currentChunk.getX(), currentChunk.getZ(), true); // Force load the new chunk
            playerTrackedChunks.add(new TrackedChunk(currentChunk));
            trackedChunks.put(player.getUniqueId(), playerTrackedChunks);
        }
    }

    @Override
    public void onDeactivate() {
        for (LinkedList<TrackedChunk> chunks : trackedChunks.values()) {
            for (TrackedChunk tc : chunks) {
                Bukkit.getWorld(tc.worldName).setChunkForceLoaded(tc.chunkX, tc.chunkZ, false); // Unforce load all chunks when the feature is deactivated
            }
        }
        trackedChunks.clear();
    }

    @Override
    public int getTickInterval() {
        return tickTimeMS;
    }

    @Override
    public void onTick() {
        for (LinkedList<TrackedChunk> chunks : trackedChunks.values()) {
            Iterator<TrackedChunk> iterator = chunks.iterator();
            while (iterator.hasNext()) {
                TrackedChunk tc = iterator.next();
                if ((System.currentTimeMillis() - tc.timeAdded) > maxChunkKeepaliveTimeMS) {
                    Bukkit.getWorld(tc.worldName).setChunkForceLoaded(tc.chunkX, tc.chunkZ, false); // Unforce load the chunk if it has exceeded its keepalive time
                    iterator.remove();
                }
            }
        }
    }

    // Class to keep track of chunks and when they were added
    private static class TrackedChunk {
        public int chunkX, chunkZ;
        public String worldName;
        public long timeAdded;

        public TrackedChunk(Chunk chunk) {
            this.chunkX = chunk.getX();
            this.chunkZ = chunk.getZ();
            this.worldName = chunk.getWorld().getName();
            this.timeAdded = System.currentTimeMillis();
        }
    }
}