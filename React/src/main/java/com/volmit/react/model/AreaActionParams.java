package com.volmit.react.model;

import lombok.*;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AreaActionParams {
    protected String world;
    @Singular
    protected List<Chunk> chunks;
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
        chunks.addAll(Arrays.asList(w.getLoadedChunks()));
    }

    public Chunk popChunk() {
        if (chunks == null || chunks.isEmpty()) {
            if (allChunks) {
                if (world != null) {
                    addChunksFor(world);
                } else {
                    for (World i : Bukkit.getWorlds()) {
                        addChunksFor(i.getName());
                    }
                }

                allChunks = false;
            }

            if (chunks == null || chunks.isEmpty()) {
                return null;
            }
        }

        return chunks.remove(0);
    }
}
