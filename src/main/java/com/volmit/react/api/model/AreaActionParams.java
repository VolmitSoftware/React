package com.volmit.react.api.model;

import lombok.*;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AreaActionParams {
    protected String world;
    @Singular
    protected List<RChunk> chunks;
    @Builder.Default
    protected boolean allChunks = true;

    private void addChunksFor(String world) {
        World w = Bukkit.getWorld(world);

        if (w == null) {
            return;
        }

        if (chunks == null) {
            chunks = new ArrayList<>();
        }

        chunks = new ArrayList<>(chunks);

        for (Chunk i : w.getLoadedChunks()) {
            chunks.add(new RChunk(i.getX(), i.getZ()));
        }
    }

    public Chunk popChunk() {
        if (chunks == null || chunks.isEmpty()) {
            if (allChunks) {
                if (world != null) {
                    addChunksFor(world);
                }

                allChunks = false;
            }

            if (chunks == null || chunks.isEmpty()) {
                return null;
            }
        }

        RChunk c = chunks.remove(0);
        World w = Bukkit.getWorld(world);

        if (w == null) {
            return popChunk();
        }

        return w.getChunkAt(c.getX(), c.getZ());
    }
}
