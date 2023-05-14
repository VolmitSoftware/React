package com.volmit.react.content.feature;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.volmit.react.React;
import com.volmit.react.api.feature.ReactFeature;
import com.volmit.react.util.scheduling.J;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.LeavesDecayEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class FeatureFastLeafDecay extends ReactFeature implements Listener {
    public static final String ID = "fast-leaf-decay";
    private final transient List<Block> search = new ArrayList<>();
    private transient Cache<IChunk, ChunkSnapshot> snapshot;
    private int leafDecayDistance = 6;
    private int leafDecayRadius = 6;
    private int tickIntervalMS = 250;
    private int decayTickSpread = 20;
    private double soundChance = 0.25;
    private double soundVolume = 0.26;
    private double soundPitch = 0.2;
    private boolean forceDecayPersistent = false;
    private boolean playSounds = true;
    private Sound decaySound = Sound.BLOCK_AZALEA_LEAVES_FALL;

    public FeatureFastLeafDecay() {
        super(ID);
    }

    @Override
    public void onActivate() {
        search.clear();
        React.instance.registerListener(this);
        snapshot = Caffeine.newBuilder()
                .expireAfterAccess(10, TimeUnit.SECONDS)
                .refreshAfterWrite(1, TimeUnit.SECONDS)
                .build((k) -> k.chunk().getChunkSnapshot(true, false, false));
    }

    @Override
    public void onDeactivate() {
        search.clear();
        React.instance.unregisterListener(this);
    }

    @Override
    public int getTickInterval() {
        return tickIntervalMS;
    }

    public int getLeafDecayDistance() {
        return leafDecayDistance;
    }

    public int getLeafDecayRadius() {
        return leafDecayRadius;
    }

    public boolean isLeaf(BlockData block) {
        return block instanceof Leaves;
    }

    public boolean shouldDecay(BlockData block) {
        return isLeaf(block)
                && (forceDecayPersistent || !((Leaves) block).isPersistent())
                && ((Leaves) block).getDistance() >= getLeafDecayDistance();
    }

    public void addBlockForDecay(IBlock block, BlockData data) {
        int d = ((Leaves) data).getDistance();
        J.s(() -> {
            Block b = block.block();

            if (shouldDecay(b.getBlockData())) {
                if (playSounds && Math.random() < soundChance) {
                    b.getWorld().playSound(b.getLocation(), decaySound, (float) soundVolume, (float) soundPitch);
                }

                b.breakNaturally();
            }
        }, (int) (Math.random() * decayTickSpread));
    }

    public ChunkSnapshot snap(Chunk c) {
        return snapshot.get(new IChunk(c.getWorld(), c.getX(), c.getZ()), (k) -> k.chunk().getChunkSnapshot(true, false, false));
    }

    public BlockData data(World world, int x, int y, int z) {
        return snap(world.getChunkAt(x >> 4, z >> 4)).getBlockData(x & 15, y, z & 15);
    }

    public void decay(Block block) {
        synchronized (search) {
            search.add(block);
        }
    }

    public void checkDecay(Block block) {
        if (isLeaf(block.getBlockData())) {
            decay(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(LeavesDecayEvent e) {
        checkDecay(e.getBlock());
    }

    @Override
    public void onTick() {
        synchronized (search) {
            int i, j, k;
            for (Block block : search) {
                BlockData d;
                for (i = block.getX() - getLeafDecayRadius(); i < block.getX() + getLeafDecayRadius(); i++) {
                    for (j = block.getY() - getLeafDecayRadius(); j < block.getY() + getLeafDecayRadius(); j++) {
                        for (k = block.getZ() - getLeafDecayRadius(); k < block.getZ() + getLeafDecayRadius(); k++) {
                            d = data(block.getWorld(), i, j, k);
                            if (shouldDecay(d)) {
                                addBlockForDecay(new IBlock(block.getWorld(), i, j, k), d);
                            }
                        }
                    }
                }
            }

            search.clear();
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

        public IChunk chunk() {
            return new IChunk(world, x >> 4, z >> 4);
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
