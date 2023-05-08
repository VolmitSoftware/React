package com.volmit.react.content.sampler;

import com.volmit.react.React;
import com.volmit.react.api.sampler.ReactTickedSampler;
import com.volmit.react.util.format.Form;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.RedstoneWire;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class SamplerRedstone extends ReactTickedSampler implements Listener {
    public static final String ID = "redstone";
    private final AtomicInteger redstoneInteractions;
    private final Set<Material> redstoneComponents;

    public SamplerRedstone() {
        super(ID, 50, 1); // 1 tick interval for higher accuracy
        redstoneInteractions = new AtomicInteger(0);
        redstoneComponents = new HashSet<>();
        redstoneComponents.add(Material.REDSTONE_WIRE);
        redstoneComponents.add(Material.REDSTONE_TORCH);
        redstoneComponents.add(Material.REDSTONE_BLOCK);
        redstoneComponents.add(Material.REPEATER);
        redstoneComponents.add(Material.COMPARATOR);
        redstoneComponents.add(Material.PISTON);
        redstoneComponents.add(Material.STICKY_PISTON);
        redstoneComponents.add(Material.REDSTONE_LAMP);
        redstoneComponents.add(Material.OBSERVER);
        // Add more redstone components here if needed
    }

    @Override
    public void start() {
        React.instance.registerListener(this);
    }

    @Override
    public void stop() {
        React.instance.unregisterListener(this);
    }

    @EventHandler // Redstone wire only
    public void onRedstoneUpdate(BlockRedstoneEvent event) {
        Block block = event.getBlock();
        BlockData blockData = block.getBlockData();

        if (blockData instanceof RedstoneWire) {
            redstoneInteractions.incrementAndGet();
        }
    }

    @Override
    public double onSample() {
        int interactions = redstoneInteractions.getAndSet(0);
        return interactions;
    }

    @Override
    public String formattedValue(double t) {
        return Form.f(Math.round(t));
    }

    @Override
    public String formattedSuffix(double t) {
        return "RI/t";
    }
}
