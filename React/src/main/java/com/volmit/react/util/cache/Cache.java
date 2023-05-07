package com.volmit.react.util.cache;

import org.bukkit.Chunk;

public interface Cache<V> {
    static long key(Chunk chunk) {
        return key(chunk.getX(), chunk.getZ());
    }

    static long key(int x, int z) {
        return (((long) x) << 32) | (z & 0xffffffffL);
    }

    static int keyX(long key) {
        return (int) (key >> 32);
    }

    static int keyZ(long key) {
        return (int) key;
    }

    static int to1D(int x, int y, int z, int w, int h) {
        return (z * w * h) + (y * w) + x;
    }

    static int[] to3D(int idx, int w, int h) {
        final int z = idx / (w * h);
        idx -= (z * w * h);
        final int y = idx / w;
        final int x = idx % w;
        return new int[]{x, y, z};
    }

    int getId();

    V get(int x, int z);
}
