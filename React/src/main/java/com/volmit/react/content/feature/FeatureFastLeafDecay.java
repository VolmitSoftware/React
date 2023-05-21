package com.volmit.react.content.feature;

import art.arcane.chrono.ChronoLatch;
import art.arcane.chrono.PrecisionStopwatch;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.volmit.react.React;
import com.volmit.react.api.feature.ReactFeature;
import com.volmit.react.util.data.B;
import com.volmit.react.util.scheduling.J;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class FeatureFastLeafDecay extends ReactFeature implements Listener {
    public static final String ID = "fast-leaf-decay";
    private final transient Set<Block> search = new HashSet<>();
    private transient Cache<IChunk, ChunkSnapshot> snapshot;
    private transient ChronoLatch cooldownLatch;
    private int leafDecayDistance = 6;
    private int leafDecayRadius = 5;
    private double maxAsyncMS = 10;
    private int tickIntervalMS = 250;
    private int decayTriggerCooldownMS = 250;
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
        cooldownLatch = new ChronoLatch(decayTriggerCooldownMS);
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
        Block b = block.block();

        if (shouldDecay(b.getBlockData())) {
            if (playSounds && Math.random() < soundChance) {
                b.getWorld().playSound(b.getLocation(), decaySound, (float) soundVolume, (float) soundPitch);
            }

            b.getDrops().forEach((i) -> b.getWorld().dropItemNaturally(b.getLocation(), i));
            b.setBlockData(B.getAir(),  false);
        }
    }

    public ChunkSnapshot snap(Chunk c) {
        return snapshot.get(new IChunk(c.getWorld(), c.getX(), c.getZ()), (k) -> k.chunk().getChunkSnapshot(true, false, false));
    }

    public BlockData data(World world, int x, int y, int z) {
        return snap(world.getChunkAt(x >> 4, z >> 4)).getBlockData(x & 15, y, z & 15);
    }

    public void decay(Block block) {
        search.add(block);
    }

    public void checkDecay(Block block) {
        if (isLeaf(block.getBlockData())) {
            decay(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(LeavesDecayEvent e) {
        if(cooldownLatch.flip()) {
            checkDecay(e.getBlock());
        }
    }

    @Override
    public void onTick() {
        PrecisionStopwatch p = PrecisionStopwatch.start();

        try {
            AtomicInteger i = new AtomicInteger();
            AtomicInteger j = new AtomicInteger();
            AtomicInteger k = new AtomicInteger();
            for (Block block : search) {
                if(p.getMilliseconds() > maxAsyncMS) {
                    break;
                }

                J.s(() -> {
                for (i.set(block.getX() - getLeafDecayRadius()); i.get() < block.getX() + getLeafDecayRadius(); i.getAndIncrement()) {
                    for (j.set(block.getY() - getLeafDecayRadius()); j.get() < block.getY() + getLeafDecayRadius(); j.getAndIncrement()) {
                        for (k.set(block.getZ() - getLeafDecayRadius()); k.get() < block.getZ() + getLeafDecayRadius(); k.getAndIncrement()) {
                                BlockData d = data(block.getWorld(), i.get(), j.get(), k.get());
                                if (shouldDecay(d)) {
                                    addBlockForDecay(new IBlock(block.getWorld(), i.get(), j.get(), k.get()), d);
                                }
                        }

                    }
                }
                });
            }

            search.clear();
        }

        catch(Throwable e) {

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
