package com.volmit.react.content.sampler;

import com.volmit.react.React;
import com.volmit.react.api.sampler.ReactCachedRateSampler;
import com.volmit.react.util.format.Form;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;

public class SamplerFluidUpdates extends ReactCachedRateSampler implements Listener {
    public static final String ID = "fluid";

    public SamplerFluidUpdates() {
        super(ID, 1000);
    }

    @Override
    public Material getIcon() {
        return Material.MILK_BUCKET;
    }

    @EventHandler
    public void onBlockFromTo(BlockFromToEvent event) {
        BlockData data = event.getBlock().getBlockData();

        if (data instanceof Levelled l) {
            increment();
            getChunkCounter(event.getBlock()).addAndGet(1D);
        }
    }

    @Override
    public String formattedValue(double t) {
        return Form.f(Math.round(t));
    }

    @Override
    public String formattedSuffix(double t) {
        return "FLOW/s";
    }
}
