package com.volmit.react.api;

import com.volmit.react.util.Ex;
import org.bukkit.Chunk;
import org.bukkit.Location;
import primal.lang.collection.GList;
import primal.lang.collection.GMap;

public class LagMap {
    private final GMap<Chunk, LagMapChunk> chunks;

    public LagMap() {
        chunks = new GMap<Chunk, LagMapChunk>();
    }

    public GMap<ChunkIssue, Double> getGrandTotalMilliseconds() {
        GMap<ChunkIssue, Double> m = new GMap<ChunkIssue, Double>();

        for (ChunkIssue i : ChunkIssue.values()) {
            m.put(i, i.getMS());
        }

        return m;
    }

    public GMap<ChunkIssue, Double> getGrandTotal() {
        GMap<ChunkIssue, Double> d = new GMap<ChunkIssue, Double>();

        for (LagMapChunk i : chunks.v()) {
            for (ChunkIssue j : i.getHits().k()) {
                if (!d.containsKey(j)) {
                    d.put(j, 0.0);
                }

                try {
                    d.put(j, d.get(j) + i.getHits().get(j));
                } catch (Throwable e) {
                    Ex.t(e);
                }
            }
        }

        return d;
    }

    public void pump() {
        try {
            for (Chunk i : chunks.k()) {
                try {
                    chunks.get(i).pump();
                } catch (Throwable e) {
                    Ex.t(e);
                }

                try {
                    if (chunks.get(i).getHits().isEmpty()) {
                        chunks.remove(i);
                    }
                } catch (Throwable e) {
                    Ex.t(e);

                    if (i != null && chunks != null) {
                        chunks.remove(i);
                    }
                }
            }
        } catch (Throwable e) {
            Ex.t(e);
        }
    }

    public void hit(Location location, ChunkIssue type, double amt) {
        try {
            if (!chunks.containsKey(location.getChunk())) {
                chunks.put(location.getChunk(), new LagMapChunk(location.getChunk()));
            }

            chunks.get(location.getChunk()).hit(type, amt);
        } catch (Throwable e) {
            Ex.t(e);
        }
    }

    public void hit(Chunk c, ChunkIssue type, double amt) {
        try {
            if (!chunks.containsKey(c)) {
                chunks.put(c, new LagMapChunk(c));
            }

            chunks.get(c).hit(type, amt);
        } catch (Exception e) {
            Ex.t(e);
        }
    }

    public GList<LagMapChunk> sorted() {
        GMap<LagMapChunk, Double> g = new GMap<>();

        for (Chunk i : getChunks().k()) {
            g.put(getChunks().get(i), getChunks().get(i).totalScore());
        }

        return g.sortK();
    }

    public GMap<Chunk, LagMapChunk> getChunks() {
        return chunks;
    }
}
