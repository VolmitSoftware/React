package com.volmit.react.util;

import com.volmit.react.Config;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import primal.bukkit.nms.Catalyst;
import primal.bukkit.nms.CatalystHost;
import primal.lang.collection.GList;
import primal.lang.collection.GMap;

import java.util.Set;

public class TickListSplitter {
    private final World world;
    private final Set<Object> master;
    private final Set<Object> masterFluid;
    private final CatalystHost host;
    private final GMap<Object, Integer> withold;
    private final GMap<Object, Integer> witholdFluid;
    private final GMap<Material, Integer> witholdTypes;
    private final GMap<Chunk, Integer> witholdChunks;
    private final GMap<Chunk, Integer> witholdTicks;
    private final RollingAverage avg;
    private int globalThrottle;

    public TickListSplitter(World world) {
        this.world = world;
        host = Catalyst.host;
        master = host.getTickList(world);
        masterFluid = host.getTickListFluid(world);
        witholdTypes = new GMap<>();
        withold = new GMap<>();
        witholdFluid = new GMap<>();
        witholdChunks = new GMap<>();
        witholdTicks = new GMap<>();
        setGlobalThrottle(0);
        avg = new RollingAverage(50);
    }

    public void tick() {
        throttleTick(master, withold);
        throttleTick(masterFluid, witholdFluid);
        computeGlobalTickLimiter();
        dropTickChunks();
        dumpWitheldTickList();
    }

    private void dropTickChunks() {
        for (Chunk i : witholdTicks.k()) {
            witholdTicks.put(i, witholdTicks.get(i) - 1);

            if (witholdTicks.get(i) <= 0) {
                witholdTicks.remove(i);
                witholdChunks.remove(i);
            }
        }
    }

    public void withold(Chunk c, int cy) {
        if (cy > 0) {
            witholdChunks.put(c, cy);
        } else {
            unregister(c);
        }
    }

    public void register(Material type, int ticks) {
        if (ticks > 0) {
            witholdTypes.put(type, ticks);
        } else {
            unregister(type);
        }
    }

    public void dumpAll() {
        while (getWitheldCount() > 0) {
            dumpWitheldTickList();
        }
    }

    private void throttleTick(Set<Object> master2, GMap<Object, Integer> withold) {
        for (Object i : new GList<>(master2)) {
            Block b = host.getBlock(world, i);
            Material t = b.getType();

            if (witholdChunks.containsKey(b.getChunk()) && witholdTicks.containsKey(b.getChunk()) && witholdTicks.get(b.getChunk()) > 0) {
                withold.put(i, witholdChunks.get(b.getChunk()));
                master2.remove(i);
            } else if (witholdTypes.containsKey(t)) {
                withold.put(i, witholdTypes.get(t));
                master2.remove(i);
            } else if (globalThrottle > 0) {
                withold.put(i, globalThrottle);
                master2.remove(i);
            }
        }
    }

    private void computeGlobalTickLimiter() {
        avg.put(getTickCount());

        if (avg.get() > Config.MAX_TICKS_PER_WORLD) {
            setGlobalThrottle((int) M.clip(globalThrottle + 1, 0, 20));
        } else {
            setGlobalThrottle((int) M.clip(globalThrottle - 1, 0, 20));
        }
    }

    private void dumpWitheldTickList() {
        for (Object i : withold.k()) {
            withold.put(i, withold.get(i) - 1);

            if (withold.get(i) <= 0) {
                withold.remove(i, withold.get(i));
                master.add(i);
            }
        }

        for (Object i : witholdFluid.k()) {
            witholdFluid.put(i, witholdFluid.get(i) - 1);

            if (witholdFluid.get(i) <= 0) {
                witholdFluid.remove(i);
                masterFluid.add(i);
            }
        }
    }

    public boolean throttle(Chunk chunk, int tickDelay, long time) {
        withold(chunk, tickDelay);
        witholdTicks.put(chunk, (int) (time / 50));

        return true;
    }

    public int getTickCount() {
        return master.size() + masterFluid.size();
    }

    public int getWitheldCount() {
        return withold.size() + witholdFluid.size();
    }

    public double getPhysicsSpeed() {
        return 1D - M.clip(globalThrottle, 0, 20D) / 20D;
    }

    public void setPhysicsSpeed(double d) {
        setGlobalThrottle((int) (M.clip(d, 0, 1D) * 20D));
    }

    public void unregisterAll() {
        witholdTypes.clear();
    }

    public void unregister(Material type) {
        witholdTypes.remove(type);
    }

    public void unregisterAllChunks() {
        witholdChunks.clear();
    }

    public void unregister(Chunk type) {
        witholdChunks.remove(type);
    }

    public void setGlobalThrottle(int throttle) {
        this.globalThrottle = throttle;
    }
}
