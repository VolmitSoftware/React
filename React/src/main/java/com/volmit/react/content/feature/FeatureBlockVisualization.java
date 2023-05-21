package com.volmit.react.content.feature;

import com.volmit.react.React;
import com.volmit.react.api.feature.ReactFeature;
import com.volmit.react.core.nms.R194;
import com.volmit.react.model.ReactPlayer;
import com.volmit.react.model.VisualizerType;
import com.volmit.react.util.math.RNG;
import com.volmit.react.util.scheduling.J;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reduces entity spawns / garbage by teleporting drops and xp from blocks and entities directly into your inventory
 */
public class FeatureBlockVisualization extends ReactFeature implements Listener {
    public static final String ID = "block-visualization";
    private Map<Block, VisualizerType> events;
    private int viewDistanceBlocks = 256;

    public FeatureBlockVisualization() {
        super(ID);
    }

    public void log(Block block, VisualizerType type) {
        J.a(() -> {
            for(Player j : block.getWorld().getPlayers()) {
                if(j.getLocation().distanceSquared(block.getLocation()) < viewDistanceBlocks * viewDistanceBlocks) {
                    ReactPlayer p = React.instance.getPlayerController().getPlayer(j);

                    if(p.getSettings().isVisualizing()) {
                        R194.glow(j, block, type.getColor(), 5);
                    }
                }
            }
        });
    }

    @Override
    public void onActivate() {
        events = new ConcurrentHashMap<>();
    }

    @Override
    public void onDeactivate() {

    }

    @Override
    public int getTickInterval() {
        return -1;
    }

    @Override
    public void onTick() {

    }
}
