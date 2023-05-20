package com.volmit.react.content.tweak;

import com.volmit.react.React;
import com.volmit.react.api.tweak.ReactTweak;
import com.volmit.react.util.data.B;
import com.volmit.react.util.math.M;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;

public class TweakBlocks extends ReactTweak implements Listener {
    public static final String ID = "tweak-blocks";
    private double globalHardnessMultiplier = 1.0;
    private Map<String, Double> hardnessMultipliers = defaultMultipliers();

    public TweakBlocks() {
        super(ID);
    }

    @Override
    public void onActivate() {
        React.instance.registerListener(this);
    }

    public String blockName(BlockData d)
    {
        String s = d.getAsString(true).split("\\Q:\\E")[1];

        if(s.contains("[")){
            s = s.split("\\Q[\\E")[0];
        }

        return s;
    }

    @EventHandler
    public void on(PlayerInteractEvent e) {
        if(e.getClickedBlock() != null) {
            double m = hardnessMultipliers.getOrDefault(blockName(e.getClickedBlock().getBlockData()),  1.0) * globalHardnessMultiplier;
            if(m != 1.0) {
                e.getPlayer().removePotionEffect(PotionEffectType.FAST_DIGGING);
                e.getPlayer().removePotionEffect(PotionEffectType.SLOW_DIGGING);
                if(m > 1.0) {
                    e.getPlayer().addPotionEffect(new PotionEffect(
                        PotionEffectType.FAST_DIGGING, (int)M.lerp(0, 200, m-1), (int)M.lerp(0, 3, m-1),
                        true, false, false
                    ));
                }

                else {
                    e.getPlayer().addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOW_DIGGING, (int)M.lerp(200, 0, m), (int)M.lerp(2, 0, m),
                        true, false, false
                    ));
                }
            }
        }
    }

    @Override
    public void onDeactivate() {
        React.instance.unregisterListener(this);
    }

    @Override
    public int getTickInterval() {
        return -1;
    }

    @Override
    public void onTick() {

    }

    public static Map<String, Double> defaultMultipliers() {
        Map<String, Double> map = new HashMap<>();

        map.put("stone", 1.0);

        return map;
    }
}
